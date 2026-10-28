package com.example.inputmonitor.data.backup.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.inputmonitor.core.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Interface for securely storing provider authentication tokens or API credentials.
 * Secrets MUST NEVER be persisted in plaintext preferences.
 */
interface BackupTokenStorage {
    suspend fun saveAuthToken(token: String)
    suspend fun getAuthToken(): String?
    suspend fun clearAuthToken()
    suspend fun hasAuthToken(): Boolean
}

/**
 * Production implementation using hardware-backed Android Keystore AES-256-GCM.
 */
class KeystoreBackupTokenStorage(
    private val context: Context,
    private val dispatchers: DispatcherProvider
) : BackupTokenStorage {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "backup_auth_key"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val TOKEN_FILE = "encrypted_backup_token.bin"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    private val tokenFile: File
        get() = File(context.filesDir, TOKEN_FILE)

    override suspend fun saveAuthToken(token: String): Unit = withContext(dispatchers.io) {
        val secretKey = getOrCreateMasterKey()
        val encrypted = encrypt(token.toByteArray(Charsets.UTF_8), secretKey)
        tokenFile.writeBytes(encrypted)
    }

    override suspend fun getAuthToken(): String? = withContext(dispatchers.io) {
        if (!tokenFile.exists()) return@withContext null
        try {
            val secretKey = getOrCreateMasterKey()
            val raw = decrypt(tokenFile.readBytes(), secretKey)
            String(raw, Charsets.UTF_8)
        } catch (_: Throwable) {
            clearAuthToken()
            null
        }
    }

    override suspend fun clearAuthToken(): Unit = withContext(dispatchers.io) {
        if (tokenFile.exists()) {
            tokenFile.delete()
        }
    }

    override suspend fun hasAuthToken(): Boolean = withContext(dispatchers.io) {
        tokenFile.exists() && tokenFile.length() > GCM_IV_LENGTH
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

    private fun encrypt(plaintext: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return combined
    }

    private fun decrypt(combined: ByteArray, secretKey: SecretKey): ByteArray {
        if (combined.size <= GCM_IV_LENGTH) {
            throw SecurityException("Corrupted encrypted token data")
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
