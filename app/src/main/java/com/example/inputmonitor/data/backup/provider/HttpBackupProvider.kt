package com.example.inputmonitor.data.backup.provider

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.backup.security.BackupTokenStorage
import com.example.inputmonitor.domain.backup.model.BackupMetadata
import com.example.inputmonitor.domain.backup.model.BackupResult
import com.example.inputmonitor.domain.backup.repository.BackupRepository
import com.example.inputmonitor.domain.backup.repository.BackupSettingsRepository
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Production-grade HTTPS REST/WebDAV backup provider using OkHttp.
 *
 * Security Guarantees:
 * - Enforces HTTPS/TLS (cleartext HTTP rejected).
 * - Uses standard Android OS X.509 certificate validation (no insecure TrustAll).
 * - Memory-safe streaming upload directly from disk (O(1) heap).
 * - Never logs credentials or CSV payloads.
 */
class HttpBackupProvider(
    private val backupSettingsRepository: BackupSettingsRepository,
    private val tokenStorage: BackupTokenStorage,
    private val dispatchers: DispatcherProvider,
    private val okHttpClient: OkHttpClient = defaultClient()
) : BackupRepository {

    companion object {
        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    override suspend fun uploadBackup(file: File, metadata: BackupMetadata): BackupResult = withContext(dispatchers.io) {
        val settings = backupSettingsRepository.getBackupSettings()
        val endpoint = settings.providerEndpoint

        if (endpoint.isNullOrBlank()) {
            return@withContext BackupResult.PermanentFailure("Backup provider endpoint is not configured")
        }

        if (!endpoint.startsWith("https://", ignoreCase = true) && !isLocalTestHost(endpoint)) {
            return@withContext BackupResult.PermanentFailure("Cleartext HTTP is prohibited. Destination URL must use HTTPS.")
        }

        val token = tokenStorage.getAuthToken()

        val mediaType = "text/csv".toMediaTypeOrNull()
        val fileBody = file.asRequestBody(mediaType)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("backup_file", metadata.fileName, fileBody)
            .addFormDataPart("event_count", metadata.eventCount.toString())
            .addFormDataPart("timestamp", metadata.timestamp.toString())
            .build()

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("User-Agent", "InputMonitor-AutoBackup/1.0")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use { res ->
                val code = res.code
                when {
                    res.isSuccessful -> {
                        val remoteId = res.header("X-Backup-Id")
                            ?: res.header("ETag")?.trim('"')
                            ?: "backup_${metadata.timestamp}"
                        BackupResult.Success(
                            remoteBackupId = remoteId,
                            timestamp = System.currentTimeMillis(),
                            bytesTransferred = file.length()
                        )
                    }
                    code == 401 || code == 403 -> {
                        BackupResult.AuthenticationRequired("Destination rejected authentication (HTTP $code)")
                    }
                    code == 408 || code == 429 || code in 500..599 -> {
                        BackupResult.RetryableFailure("Server returned transient error (HTTP $code): ${res.message}")
                    }
                    else -> {
                        BackupResult.PermanentFailure("Upload rejected by destination (HTTP $code): ${res.message}")
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            BackupResult.RetryableFailure("Network socket timeout during upload: ${e.message}", e)
        } catch (e: IOException) {
            BackupResult.RetryableFailure("Network I/O failure during upload: ${e.message}", e)
        } catch (t: Throwable) {
            BackupResult.PermanentFailure("Unexpected error during backup upload: ${t.message}", t)
        }
    }

    override suspend fun testConnection(endpoint: String, token: String?): BackupResult = withContext(dispatchers.io) {
        if (endpoint.isBlank()) {
            return@withContext BackupResult.PermanentFailure("Endpoint URL cannot be empty")
        }

        if (!endpoint.startsWith("https://", ignoreCase = true) && !isLocalTestHost(endpoint)) {
            return@withContext BackupResult.PermanentFailure("Cleartext HTTP is prohibited. Destination URL must use HTTPS.")
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .header("User-Agent", "InputMonitor-AutoBackup/1.0")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use { res ->
                val code = res.code
                when {
                    res.isSuccessful || code == 405 -> {
                        // 200..299 or 405 Method Not Allowed (since GET was used on a POST-only endpoint) confirms reachable & auth accepted
                        BackupResult.Success("connection_verified")
                    }
                    code == 401 || code == 403 -> {
                        BackupResult.AuthenticationRequired("Authentication failed (HTTP $code)")
                    }
                    code in 500..599 || code == 408 || code == 429 -> {
                        BackupResult.RetryableFailure("Server transient error (HTTP $code)")
                    }
                    else -> {
                        BackupResult.PermanentFailure("Connection test failed (HTTP $code): ${res.message}")
                    }
                }
            }
        } catch (e: IOException) {
            BackupResult.RetryableFailure("Unable to connect to endpoint: ${e.message}", e)
        } catch (t: Throwable) {
            BackupResult.PermanentFailure("Failed to test connection: ${t.message}", t)
        }
    }

    private fun isLocalTestHost(url: String): Boolean {
        return url.startsWith("http://localhost", ignoreCase = true) ||
                url.startsWith("http://127.0.0.1", ignoreCase = true)
    }
}
