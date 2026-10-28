package com.example.inputmonitor.ui.settings

import com.example.inputmonitor.domain.backup.model.BackupSettings
import com.example.inputmonitor.domain.model.MonitoringSession

/**
 * UI state for the Settings and Data Management screen (Architecture.md §50, SRS.md §55).
 * Extended with production automatic cloud backup configuration and status.
 */
data class SettingsUiState(
    val sessions: List<MonitoringSession> = emptyList(),
    val totalEventsCount: Int = 0,
    val isKeyboardLoggingEnabled: Boolean = true,
    val isAccessibilityLoggingEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isOperating: Boolean = false,
    val pendingDeleteSession: MonitoringSession? = null,
    val showDeleteAllSessionsDialog: Boolean = false,
    val showDeleteAllDataDialog: Boolean = false,
    val userMessage: String? = null,
    val error: String? = null,
    // Automatic Daily Backup state
    val backupSettings: BackupSettings = BackupSettings(),
    val showConnectBackupDialog: Boolean = false,
    val showDisconnectBackupDialog: Boolean = false,
    val isTestingConnection: Boolean = false
) {
    val isBackupConfigured: Boolean
        get() = backupSettings.isConfigured
}
