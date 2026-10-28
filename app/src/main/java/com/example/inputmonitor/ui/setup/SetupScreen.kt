package com.example.inputmonitor.ui.setup

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.inputmonitor.domain.model.SetupState

/**
 * Setup wizard screen guiding the user through permissions, accessibility,
 * keyboard enablement, and verification (Architecture.md §14–§17, SRS.md §12–§18, AC-03, AC-35, AC-36, AC-37).
 */
@Composable
fun SetupScreen(
    setupState: SetupState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onShowKeyboardPicker: () -> Unit,
    onCompleteSetup: () -> Unit,
    modifier: Modifier = Modifier,
    requiredPermissions: List<String> = emptyList(),
    isPermissionDenied: Boolean = false,
    onPermissionsResult: (Map<String, Boolean>) -> Unit = {},
    onDismissPermissionDeniedAlert: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        onPermissionsResult(permissions)
    }

    Scaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Application Setup & Services",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Configure system capabilities required for local interaction monitoring.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Non-blocking Permission Denial Notice (SRS.md §12, AC-35)
            if (isPermissionDenied) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Permission Notice",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "One or more optional permissions were not granted. The application will continue operating with available capabilities without trapping setup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onDismissPermissionDeniedAlert,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Optional Step: Application Runtime Permissions (SRS.md §12)
            if (requiredPermissions.isNotEmpty()) {
                SetupStepCard(
                    icon = Icons.Default.Info,
                    title = "Application Permissions",
                    description = "Grants runtime permissions required for application notifications and status alerts.",
                    isConfigured = setupState.runtimePermissionsReady,
                    statusText = if (setupState.runtimePermissionsReady) "READY" else "ACTION NEEDED",
                    action = {
                        if (!setupState.runtimePermissionsReady) {
                            Button(
                                onClick = { permissionLauncher.launch(requiredPermissions.toTypedArray()) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Grant Permissions")
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Step 1: Accessibility Service (SRS.md §13)
            SetupStepCard(
                icon = Icons.Default.Info,
                title = "Interaction Monitoring Service",
                description = "Enables permitted observation of clicks, focus changes, scrolling, and window transitions. Android requires manual activation in Accessibility settings.",
                isConfigured = setupState.accessibilityEnabled,
                statusText = if (setupState.accessibilityEnabled) "ENABLED" else "DISABLED",
                action = {
                    if (!setupState.accessibilityEnabled) {
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Open Accessibility Settings")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step 2: Custom Keyboard Enablement (SRS.md §14)
            SetupStepCard(
                icon = Icons.Default.Settings,
                title = "Enable Custom Keyboard",
                description = "The application includes a privacy-respecting Android IME keyboard. Android requires manual activation of new keyboards.",
                isConfigured = setupState.imeEnabled,
                statusText = if (setupState.imeEnabled) "ENABLED" else "NOT ENABLED",
                action = {
                    if (!setupState.imeEnabled) {
                        Button(
                            onClick = onOpenKeyboardSettings,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Enable in Android Settings")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step 3: Custom Keyboard Selection (SRS.md §15)
            SetupStepCard(
                icon = Icons.Default.Lock,
                title = "Select Custom Keyboard",
                description = "Choose the Custom Keyboard as your active input method to record permitted text typing in compatible applications.",
                isConfigured = setupState.imeSelected,
                statusText = if (setupState.imeSelected) "SELECTED" else "OTHER KEYBOARD",
                action = {
                    if (!setupState.imeSelected) {
                        Button(
                            onClick = onShowKeyboardPicker,
                            enabled = setupState.imeEnabled,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Select Keyboard")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Step 4: System Verification Checklist (SRS.md §16)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SYSTEM STATUS",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    StatusCheckItem(label = "Application Permissions", isReady = setupState.runtimePermissionsReady, readyText = "READY", notReadyText = "ACTION NEEDED")
                    StatusCheckItem(label = "Accessibility Service", isReady = setupState.accessibilityEnabled, readyText = "ENABLED", notReadyText = "DISABLED")
                    StatusCheckItem(label = "Custom Keyboard", isReady = setupState.imeEnabled, readyText = "ENABLED", notReadyText = "NOT ENABLED")
                    StatusCheckItem(label = "Current Keyboard", isReady = setupState.imeSelected, readyText = "CUSTOM KEYBOARD", notReadyText = "OTHER KEYBOARD")
                    StatusCheckItem(label = "Sensitive Input Protection", isReady = true, readyText = "ACTIVE", notReadyText = "INACTIVE")
                    StatusCheckItem(label = "Local Database", isReady = setupState.databaseReady, readyText = "READY", notReadyText = "INITIALIZING")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step 5: Proceed to Dashboard (SRS.md §16, §17)
            if (setupState.isComplete) {
                Button(
                    onClick = onCompleteSetup,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Complete Setup & Open Dashboard",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onCompleteSetup,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Proceed with Partial Setup",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You can return to configuration at any time from Dashboard or Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SetupStepCard(
    icon: ImageVector,
    title: String,
    description: String,
    isConfigured: Boolean,
    statusText: String,
    action: @Composable () -> Unit
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                StatusBadge(isConfigured = isConfigured, text = statusText)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            action()
        }
    }
}

@Composable
private fun StatusBadge(
    isConfigured: Boolean,
    text: String
) {
    val backgroundColor = if (isConfigured) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val contentColor = if (isConfigured) Color(0xFF2E7D32) else Color(0xFFE65100)

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConfigured) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
        }
    }
}

@Composable
private fun StatusCheckItem(
    label: String,
    isReady: Boolean,
    readyText: String,
    notReadyText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isReady) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isReady) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isReady) readyText else notReadyText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isReady) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}
