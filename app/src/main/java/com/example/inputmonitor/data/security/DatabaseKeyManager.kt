package com.example.inputmonitor.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore cryptographic key manager for SQLCipher database encryption (DEC-005, SECURITY.md §11).
 *
 * Uses hardware-backed Android Keystore AES-256-GCM to protect the database passphrase.
 * The raw database passphrase is never persisted in plaintext.
 */
class DatabaseKeyManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "monitoring_db_key"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val PASSPHRASE_FILE = "encrypted_db_passphrase.bin"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Retrieves or generates the 256-bit database encryption passphrase.
     *
     * @return The raw byte array passphrase to supply to SQLCipher SupportOpenHelperFactory.
     * @throws SecurityException if cryptographic operations or Keystore fail.
     */
    @Synchronized
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val file = File(context.filesDir, PASSPHRASE_FILE)
        val secretKey = getOrCreateMasterKey()

        if (file.exists()) {
            try {
                return decryptPassphrase(file.readBytes(), secretKey)
            } catch (_: Throwable) {
                // Keystore entry invalidated, rotated, or file corrupted.
                // Safely delete stale passphrase and unopenable database (DEC-005).
                try {
                    file.delete()
                    context.deleteDatabase(com.example.inputmonitor.data.database.AppDatabase.DATABASE_NAME)
                } catch (_: Throwable) {
                }
            }
        }

        val rawPassphrase = ByteArray(32).apply {
            SecureRandom().nextBytes(this)
        }
        val encryptedBytes = encryptPassphrase(rawPassphrase, secretKey)
        file.writeBytes(encryptedBytes)
        return rawPassphrase
    }

    private fun getOrCreateMasterKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }

        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encryptPassphrase(rawPassphrase: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(rawPassphrase)

        // Prepend 12-byte IV to ciphertext
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return combined
    }

    private fun decryptPassphrase(combined: ByteArray, secretKey: SecretKey): ByteArray {
        if (combined.size <= GCM_IV_LENGTH) {
            throw SecurityException("Corrupted encrypted database passphrase")
        }

        val iv = ByteArray(GCM_IV_LENGTH)
        val ciphertext = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.size)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }
}
