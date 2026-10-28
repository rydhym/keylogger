package com.example.inputmonitor.domain.backup.model

/**
 * Configuration model for automatic daily cloud backups.
 *
 * Mandatory security rules:
 * - [enabled] MUST default to false (zero covert/automatic transmission by default).
 * - [wifiOnly] defaults to true (unmetered network preference).
 * - Authentication secrets MUST NOT be stored in this model.
 */
data class BackupSettings(
    val enabled: Boolean = false,
    val providerId: String = "https_rest",
    val providerEndpoint: String? = null,
    val wifiOnly: Boolean = true,
    val lastAttemptAt: Long? = null,
    val lastSuccessfulBackupAt: Long? = null,
    val lastFailureReason: String? = null,
    val lastRemoteBackupId: String? = null,
    val isAuthenticationRequired: Boolean = false
) {
    val isConfigured: Boolean
        get() = !providerEndpoint.isNullOrBlank()
}
