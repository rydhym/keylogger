package com.example.inputmonitor.domain.backup.model

/**
 * Result of an automatic cloud backup transmission.
 *
 * Distinguishes retryable transient failures from permanent errors and authentication revocations.
 */
sealed class BackupResult {
    data class Success(
        val remoteBackupId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val bytesTransferred: Long = 0L
    ) : BackupResult()

    data class RetryableFailure(
        val message: String,
        val cause: Throwable? = null
    ) : BackupResult()

    data class PermanentFailure(
        val message: String,
        val cause: Throwable? = null
    ) : BackupResult()

    data class AuthenticationRequired(
        val message: String
    ) : BackupResult()
}
