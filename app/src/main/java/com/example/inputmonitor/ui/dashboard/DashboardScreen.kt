package com.example.inputmonitor.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.ui.util.DateTimeUtils
import java.text.NumberFormat
import java.util.Locale

/**
 * Main application dashboard screen providing real-time system status, session controls,
 * current application context, and live aggregate event counters (Architecture.md §55, SRS.md §47).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onStartSession: (String, String?) -> Unit,
    onPauseSession: () -> Unit,
    onResumeSession: () -> Unit,
    onStopSession: () -> Unit,
    onRefreshSetup: () -> Unit,
    onNavigateToEvents: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToExport: () -> Unit = {},
    onNavigateToTestBench: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToSetup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showStartDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Android Interaction Monitor",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = onRefreshSetup) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Status"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Service Status Card (SRS.md §47, §74, AC-22)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SYSTEM STATUS",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (uiState.hasIncompleteServices) {
                            TextButton(onClick = onNavigateToSetup) {
                                Text("Configure Services")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    StatusRow(
                        label = "Keyboard Service",
                        status1 = if (uiState.setupState.imeEnabled) "● ENABLED" else "✕ DISABLED",
                        isSuccess1 = uiState.setupState.imeEnabled,
                        status2 = if (uiState.setupState.imeSelected) "● CURRENT IME" else "✕ OTHER IME",
                        isSuccess2 = uiState.setupState.imeSelected
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    StatusRow(
                        label = "Accessibility Service",
                        status1 = if (uiState.setupState.accessibilityEnabled) "● ENABLED" else "✕ DISABLED",
                        isSuccess1 = uiState.setupState.accessibilityEnabled
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    StatusRow(
                        label = "Credential Protection",
                        status1 = "● ACTIVE",
                        isSuccess1 = true
                    )

                    if (uiState.hasIncompleteServices) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "One or more services disabled. Tap to re-enable in Settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Session Status Card (SRS.md §47)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (uiState.sessionState) {
                        SessionState.ACTIVE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        SessionState.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        SessionState.STOPPED, SessionState.INACTIVE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MONITORING SESSION",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        SessionStateBadge(state = uiState.sessionState)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val session = uiState.activeSession
                    if (session != null) {
                        Text(
                            text = session.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Started: ${DateTimeUtils.formatSessionStart(session.startTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.sessionState == SessionState.ACTIVE || uiState.sessionState == SessionState.PAUSED) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Session saved on this device. After restart and unlock, it is restored when Android starts the app or its enabled services. Paused sessions stay paused. Android controls permissions and service access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (session.notes != null) {
                            Text(
                                text = "Notes: ${session.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "No active monitoring session. Events are dropped until a session is started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Session Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (uiState.sessionState) {
                            SessionState.INACTIVE, SessionState.STOPPED -> {
                                Button(
                                    onClick = { showStartDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = "Start Session")
                                }
                            }
                            SessionState.ACTIVE -> {
                                OutlinedButton(
                                    onClick = onPauseSession,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "Pause")
                                }
                                Button(
                                    onClick = onStopSession,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "Stop Session")
                                }
                            }
                            SessionState.PAUSED -> {
                                Button(
                                    onClick = onResumeSession,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "Resume")
                                }
                                OutlinedButton(
                                    onClick = onStopSession,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "Stop Session")
                                }
                            }
                        }
                    }
                }
            }

            // Current Application Context Card (SRS.md §47)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CURRENT APPLICATION",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.currentApplicationLabel ?: "Waiting for interaction...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (uiState.currentPackageName != null) {
                        Text(
                            text = uiState.currentPackageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Aggregate Event Counters Card (SRS.md §47)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "EVENTS",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val nf = NumberFormat.getNumberInstance(Locale.getDefault())

                    CounterRow(label = "Total Events", value = nf.format(uiState.totalEvents), isTotal = true)
                    Spacer(modifier = Modifier.height(6.dp))
                    CounterRow(label = "Keyboard Commits", value = nf.format(uiState.keyboardEvents))
                    CounterRow(label = "Clicks", value = nf.format(uiState.clickEvents))
                    CounterRow(label = "Focus Changes", value = nf.format(uiState.focusEvents))
                    CounterRow(label = "Scrolls", value = nf.format(uiState.scrollEvents))
                    CounterRow(label = "Window Changes", value = nf.format(uiState.windowEvents))
                }
            }

            // Navigation Actions
            Text(
                text = "Quick Navigation",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigateToEvents,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Event Log")
                }
                Button(
                    onClick = onNavigateToStatistics,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Statistics")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigateToTestBench,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Test Bench")
                }
                Button(
                    onClick = onNavigateToExport,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Export Data")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToSettings,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Settings")
                }
                OutlinedButton(
                    onClick = onNavigateToSetup,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Setup")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (showStartDialog) {
        var sessionName by remember {
            mutableStateOf(DateTimeUtils.generateDefaultSessionName())
        }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showStartDialog = false },
            title = { Text(text = "Start Monitoring Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sessionName,
                        onValueChange = { sessionName = it },
                        label = { Text("Session Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sessionName.isNotBlank()) {
                            onStartSession(sessionName, notes.ifBlank { null })
                            showStartDialog = false
                        }
                    }
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    status1: String,
    isSuccess1: Boolean,
    status2: String? = null,
    isSuccess2: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = status1,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isSuccess1) Color(0xFF2E7D32) else Color(0xFFE65100)
            )
            if (status2 != null) {
                Text(
                    text = status2,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSuccess2) Color(0xFF2E7D32) else Color(0xFFE65100)
                )
            }
        }
    }
}

@Composable
private fun CounterRow(
    label: String,
    value: String,
    isTotal: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isTotal) 4.dp else 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isTotal) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = if (isTotal) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
            color = if (isTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SessionStateBadge(state: SessionState) {
    val (color, text) = when (state) {
        SessionState.ACTIVE -> Color(0xFF2E7D32) to "● ACTIVE"
        SessionState.PAUSED -> Color(0xFFE65100) to "⏸ PAUSED"
        SessionState.STOPPED -> Color(0xFF546E7A) to "■ STOPPED"
        SessionState.INACTIVE -> Color(0xFF757575) to "○ INACTIVE"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
