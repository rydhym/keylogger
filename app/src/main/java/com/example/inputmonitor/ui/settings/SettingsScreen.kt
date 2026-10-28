package com.example.inputmonitor.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inputmonitor.domain.backup.model.BackupSettings
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings and Data Management screen (SRS.md §55, Architecture.md §50).
 * Provides privacy controls, session deletion, and user-consented automatic daily cloud backup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onKeyboardLoggingToggle: (Boolean) -> Unit,
    onAccessibilityLoggingToggle: (Boolean) -> Unit,
    onRequestDeleteSession: (MonitoringSession) -> Unit,
    onConfirmDeleteSession: () -> Unit,
    onDismissDeleteSessionDialog: () -> Unit,
    onRequestDeleteAllSessions: () -> Unit,
    onConfirmDeleteAllSessions: () -> Unit,
    onDismissDeleteAllSessionsDialog: () -> Unit,
    onRequestDeleteAllData: () -> Unit,
    onConfirmDeleteAllData: () -> Unit,
    onDismissDeleteAllDataDialog: () -> Unit,
    onDismissUserMessage: () -> Unit,
    onDismissError: () -> Unit,
    onAutomaticBackupToggle: (Boolean) -> Unit = {},
    onWifiOnlyToggle: (Boolean) -> Unit = {},
    onTriggerBackupNow: () -> Unit = {},
    onRequestConnectBackup: () -> Unit = {},
    onDismissConnectBackupDialog: () -> Unit = {},
    onConfirmConnectBackup: (String, String?) -> Unit = { _, _ -> },
    onRequestDisconnectBackup: () -> Unit = {},
    onDismissDisconnectBackupDialog: () -> Unit = {},
    onConfirmDisconnectBackup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onDismissUserMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            onDismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings & Data Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text(text = "←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Storage & Security Status Overview
                StorageOverviewCard(
                    sessionCount = uiState.sessions.size,
                    eventCount = uiState.totalEventsCount
                )

                // Section 2: Input Capture & Privacy Configuration
                LoggingPreferencesCard(
                    isKeyboardLoggingEnabled = uiState.isKeyboardLoggingEnabled,
                    isAccessibilityLoggingEnabled = uiState.isAccessibilityLoggingEnabled,
                    onKeyboardLoggingToggle = onKeyboardLoggingToggle,
                    onAccessibilityLoggingToggle = onAccessibilityLoggingToggle
                )

                // Section 3: Automatic Daily Cloud Backup (Phase 13)
                AutomaticBackupCard(
                    backupSettings = uiState.backupSettings,
                    isBackupConfigured = uiState.isBackupConfigured,
                    onAutomaticBackupToggle = onAutomaticBackupToggle,
                    onWifiOnlyToggle = onWifiOnlyToggle,
                    onTriggerBackupNow = onTriggerBackupNow,
                    onRequestConnectBackup = onRequestConnectBackup,
                    onRequestDisconnectBackup = onRequestDisconnectBackup
                )

                // Section 4: Recorded Sessions Inventory
                RecordedSessionsCard(
                    sessions = uiState.sessions,
                    onDeleteSession = onRequestDeleteSession
                )

                // Section 5: Danger Zone (SRS.md §55)
                DangerZoneCard(
                    onRequestDeleteAllSessions = onRequestDeleteAllSessions,
                    onRequestDeleteAllData = onRequestDeleteAllData
                )
            }

            if (uiState.isOperating) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    // Confirmation Dialog: Single Session Deletion
    uiState.pendingDeleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = onDismissDeleteSessionDialog,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(text = "Delete Session?") },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete session \"${session.name}\" and all associated interaction events? This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDeleteSession,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteSessionDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Dialog: All Sessions Deletion
    if (uiState.showDeleteAllSessionsDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeleteAllSessionsDialog,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(text = "Delete All Sessions?") },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete all monitoring sessions and all recorded interaction events? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeleteAllSessions,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All Sessions")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteAllSessionsDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Dialog: Full Application Data Reset
    if (uiState.showDeleteAllDataDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeleteAllDataDialog,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(text = "Reset All Application Data?") },
            text = {
                Text(
                    text = "This action will permanently purge ALL monitoring sessions, all recorded events, and reset all application preferences to factory defaults. Are you sure you want to proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeleteAllData,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Erase Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteAllDataDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Connect Cloud Destination Dialog
    if (uiState.showConnectBackupDialog) {
        ConnectBackupDialog(
            isTesting = uiState.isTestingConnection,
            onDismiss = onDismissConnectBackupDialog,
            onConfirm = onConfirmConnectBackup
        )
    }

    // Disconnect Cloud Destination Dialog
    if (uiState.showDisconnectBackupDialog) {
        DisconnectBackupDialog(
            onDismiss = onDismissDisconnectBackupDialog,
            onConfirm = onConfirmDisconnectBackup
        )
    }
}

@Composable
private fun StorageOverviewCard(
    sessionCount: Int,
    eventCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "LOCAL STORAGE & SECURITY",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Total Sessions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = sessionCount.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Recorded Events",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = eventCount.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Encrypted at rest via SQLCipher (AES-256). Strictly zero network transmission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoggingPreferencesCard(
    isKeyboardLoggingEnabled: Boolean,
    isAccessibilityLoggingEnabled: Boolean,
    onKeyboardLoggingToggle: (Boolean) -> Unit,
    onAccessibilityLoggingToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "LOGGING PREFERENCES",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Keyboard Logging Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Keyboard Input Logging",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Record non-sensitive keystrokes and text commit events through the custom keyboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isKeyboardLoggingEnabled,
                    onCheckedChange = onKeyboardLoggingToggle
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Accessibility Logging Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Accessibility Event Logging",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Record view clicks, window changes, and interactions via the Android Accessibility service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isAccessibilityLoggingEnabled,
                    onCheckedChange = onAccessibilityLoggingToggle
                )
            }
        }
    }
}

@Composable
private fun RecordedSessionsCard(
    sessions: List<MonitoringSession>,
    onDeleteSession: (MonitoringSession) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECORDED SESSIONS",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${sessions.size}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (sessions.isEmpty()) {
                Text(
                    text = "No monitoring sessions recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sessions.forEach { session ->
                        SessionItemRow(
                            session = session,
                            dateFormat = dateFormat,
                            onDelete = { onDeleteSession(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItemRow(
    session: MonitoringSession,
    dateFormat: SimpleDateFormat,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dateFormat.format(Date(session.startTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val statusColor = when (session.state) {
                        SessionState.ACTIVE -> MaterialTheme.colorScheme.primary
                        SessionState.PAUSED -> MaterialTheme.colorScheme.tertiary
                        SessionState.STOPPED -> MaterialTheme.colorScheme.outline
                        SessionState.INACTIVE -> MaterialTheme.colorScheme.outline
                    }

                    Text(
                        text = "• ${session.state.name}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete session ${session.name}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DangerZoneCard(
    onRequestDeleteAllSessions: () -> Unit,
    onRequestDeleteAllData: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "DANGER ZONE",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Destructive and irreversible actions (SRS.md §55). Deleting sessions automatically purges all associated interaction events.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Delete All Sessions Button
            OutlinedButton(
                onClick = onRequestDeleteAllSessions,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete All Sessions")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reset All Application Data Button
            Button(
                onClick = onRequestDeleteAllData,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All Application Data")
            }
        }
    }
}

@Composable
private fun AutomaticBackupCard(
    backupSettings: BackupSettings,
    isBackupConfigured: Boolean,
    onAutomaticBackupToggle: (Boolean) -> Unit,
    onWifiOnlyToggle: (Boolean) -> Unit,
    onTriggerBackupNow: () -> Unit,
    onRequestConnectBackup: () -> Unit,
    onRequestDisconnectBackup: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "AUTOMATIC DAILY BACKUP",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Automatically exports and uploads encrypted CSV backups approximately every 24 hours to your authenticated cloud destination.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Switch 1: Enable Automatic Daily Backup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automatic Daily Backup",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = if (backupSettings.enabled) "Enabled (Runs approx. daily)" else "Disabled (Manual export only)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = backupSettings.enabled,
                    onCheckedChange = onAutomaticBackupToggle
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Switch 2: Wi-Fi Only
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wi-Fi Only",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = if (backupSettings.wifiOnly) "Upload only over unmetered Wi-Fi" else "Upload over Wi-Fi or mobile data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = backupSettings.wifiOnly,
                    onCheckedChange = onWifiOnlyToggle
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Cloud Destination Status
            Text(
                text = "DESTINATION & STATUS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isBackupConfigured) "Connected: ${backupSettings.providerEndpoint}" else "Status: Not configured",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            val lastSuccessText = backupSettings.lastSuccessfulBackupAt?.let {
                dateFormat.format(Date(it))
            } ?: "Never"
            Text(
                text = "Last successful backup: $lastSuccessText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Warning if authentication required or failed
            if (backupSettings.isAuthenticationRequired) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Authentication Required: Please reconnect destination.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (backupSettings.lastFailureReason != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Last attempt issue: ${backupSettings.lastFailureReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            if (!isBackupConfigured) {
                Button(
                    onClick = onRequestConnectBackup,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Cloud Destination")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTriggerBackupNow,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back Up Now")
                    }

                    TextButton(
                        onClick = onRequestDisconnectBackup,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectBackupDialog(
    isTesting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (endpoint: String, token: String?) -> Unit
) {
    var endpoint by remember { mutableStateOf("https://") }
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Cloud Destination") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your authenticated HTTPS endpoint URL (Nextcloud, WebDAV, or custom REST backup server).",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Destination URL (HTTPS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Bearer Token / API Key (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Security: Cleartext HTTP is strictly prohibited. Tokens are encrypted using Android Keystore AES-256-GCM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isTesting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("Verifying connection & credentials...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(endpoint, token) },
                enabled = !isTesting && endpoint.isNotBlank()
            ) {
                Text("Test & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isTesting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DisconnectBackupDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect Cloud Destination?") },
        text = {
            Text("Automatic daily backup will be disabled and saved credentials will be securely wiped from Android Keystore. You can reconnect anytime.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Disconnect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

