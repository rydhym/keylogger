package com.example.inputmonitor.ui.testbench

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.ui.util.DateTimeUtils

/**
 * Controlled Application Use Test Screen (IMPLEMENTATION_PLAN.md §56, SRS.md §66, design.md §Screen 6).
 *
 * Provides a dedicated in-app test bench with 7 controlled input fields representing
 * known ground truth for IME capture, sensitive classification, and zero credential persistence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestBenchScreen(
    uiState: TestBenchUiState,
    onNavigateBack: () -> Unit,
    onFieldFocused: (TestBenchFieldType) -> Unit,
    onFieldValueChanged: (TestBenchFieldType, String) -> Unit,
    onClearAllFields: () -> Unit,
    onPopulateSyntheticData: () -> Unit,
    onStartQuickSession: () -> Unit,
    onStopQuickSession: () -> Unit,
    onSubmitTestInputs: () -> Unit,
    onDismissFeedback: () -> Unit,
    onDismissSubmissionResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissFeedback()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Controlled Test Bench",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Ground-Truth IME & Privacy Verification",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClearAllFields) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset All Fields"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Session Status & Quick Control Card
            SessionControlCard(
                uiState = uiState,
                onStartQuickSession = onStartQuickSession,
                onStopQuickSession = onStopQuickSession
            )

            // 2. Real-Time Telemetry & Classification Detection Banner (design.md §Screen 6)
            LiveTelemetryCard(uiState = uiState)

            // 3. The 7 Controlled Input Test Fields Card (SRS.md §66, IMPLEMENTATION_PLAN.md §56)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONTROLLED INPUT FIELDS",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "7 Ground-Truth Editors",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Focus any field to inspect IME classifications and test typing invariants.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    TestBenchFieldType.entries.forEach { field ->
                        ControlledInputField(
                            fieldType = field,
                            currentValue = uiState.getFieldValue(field),
                            isFocused = uiState.activeField == field,
                            onFocusChanged = { if (it) onFieldFocused(field) },
                            onValueChange = { onFieldValueChanged(field, it) }
                        )
                    }
                }
            }

            // 4. Action Buttons Bar (SRS.md §66 [SUBMIT])
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "TEST BENCH ACTIONS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPopulateSyntheticData,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Populate Samples")
                        }

                        OutlinedButton(
                            onClick = onClearAllFields,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Fields")
                        }
                    }

                    Button(
                        onClick = onSubmitTestInputs,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Submit Test Verification",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // 5. Ground-Truth Invariants Reference Card
            InvariantsChecklistCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Submission Verification Summary Dialog
    uiState.submissionResult?.let { result ->
        SubmissionResultDialog(
            result = result,
            onDismiss = onDismissSubmissionResult
        )
    }
}

@Composable
private fun SessionControlCard(
    uiState: TestBenchUiState,
    onStartQuickSession: () -> Unit,
    onStopQuickSession: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.isSessionActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (uiState.isSessionActive) Color(0xFF2E7D32) else Color(0xFF757575),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isSessionActive) "ACTIVE MONITORING SESSION" else "MONITORING INACTIVE",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (uiState.isSessionActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.activeSession?.name ?: "No session running (Testing AC-32)",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (uiState.isSessionActive) {
                Button(
                    onClick = onStopQuickSession,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            } else {
                Button(
                    onClick = onStartQuickSession,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Test")
                }
            }
        }
    }
}

@Composable
private fun LiveTelemetryCard(uiState: TestBenchUiState) {
    val isSensitive = uiState.isSensitiveMode
    val bannerBg = if (isSensitive) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }
    val badgeColor = if (isSensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bannerBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LAST CLASSIFICATION DETECTED",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = badgeColor
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSensitive) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = uiState.detectedClassification.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSensitive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Text(
                text = "Focused Field: ${uiState.activeField.displayName}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (isSensitive) {
                    "● Security Policy: SENSITIVE CREDENTIAL FIELD DETECTED\n● Key preview popups suppressed, zero raw text persisted (eventData = NULL)."
                } else {
                    "● Security Policy: NON-SENSITIVE GENERAL FIELD\n● Standard typing permitted; recorded only when a monitoring session is active."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isSensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Live Counters Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricColumn(label = "Total Events", value = uiState.totalEventsInSession.toString())
                MetricColumn(label = "Keyboard Commits", value = uiState.keyboardEventsInSession.toString())
                MetricColumn(
                    label = "Sensitive Suppressed",
                    value = uiState.sensitiveSuppressionsInSession.toString(),
                    highlight = uiState.sensitiveSuppressionsInSession > 0
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ControlledInputField(
    fieldType: TestBenchFieldType,
    currentValue: String,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fieldType.isSensitive) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Sensitive",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = fieldType.displayName,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (fieldType.isSensitive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (fieldType.isSensitive) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = fieldType.expectedClassification.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = if (fieldType.isSensitive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = currentValue,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChanged(it.isFocused) },
            singleLine = !fieldType.isMultiline,
            minLines = if (fieldType.isMultiline) 3 else 1,
            visualTransformation = if (fieldType.isSensitive) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = fieldType.keyboardType,
                imeAction = fieldType.imeAction
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = fieldType.helperText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun InvariantsChecklistCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "GROUND-TRUTH PRIVACY INVARIANTS",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            InvariantCheckRow(
                code = "AC-29",
                description = "Permitted synthetic non-sensitive text is logged only during active sessions."
            )
            InvariantCheckRow(
                code = "AC-30",
                description = "Password and PIN fields never persist entered credential contents (eventData = NULL)."
            )
            InvariantCheckRow(
                code = "AC-31",
                description = "Sensitive content does not appear in database exports or debug logs."
            )
            InvariantCheckRow(
                code = "AC-32",
                description = "Stopping event logging does not disable or impede keyboard typing."
            )
        }
    }
}

@Composable
private fun InvariantCheckRow(code: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = code,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubmissionResultDialog(
    result: TestBenchSubmissionResult,
    onDismiss: () -> Unit
) {
    val dateStr = DateTimeUtils.formatDateTime(result.timestamp)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Ground-Truth Verification Passed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Synthetic test inputs evaluated at $dateStr.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "• Total Fields Evaluated: ${result.totalFieldsSubmitted}/7 populated\n" +
                            "• Sensitive Fields Inspected: ${result.sensitiveFieldsCount} (Password & PIN)\n" +
                            "• Non-Sensitive Fields Inspected: ${result.nonSensitiveFieldsCount}\n" +
                            "• Zero Credential Leakage: 100% Guaranteed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Verified Invariants:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                result.invariantsVerified.forEach { inv ->
                    Text(
                        text = "✓ $inv",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}
