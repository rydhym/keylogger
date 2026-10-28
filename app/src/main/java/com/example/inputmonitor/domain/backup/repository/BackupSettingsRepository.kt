package com.example.inputmonitor.domain.backup.repository

import com.example.inputmonitor.domain.backup.model.BackupSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing persistent automatic backup configuration and status.
 */
interface BackupSettingsRepository {
    val backupSettings: Flow<BackupSettings>

    suspend fun getBackupSettings(): BackupSettings

    suspend fun setBackupEnabled(enabled: Boolean)

    suspend fun setWifiOnly(wifiOnly: Boolean)

    suspend fun setProviderConfig(providerId: String, endpoint: String?)

    suspend fun recordBackupAttempt(timestamp: Long)

    suspend fun recordBackupSuccess(timestamp: Long, remoteBackupId: String?)

    suspend fun recordBackupFailure(reason: String, authRequired: Boolean = false)

    suspend fun clearAuthenticationRequired()

    suspend fun resetBackupSettings()
}
