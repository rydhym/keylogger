package com.example.inputmonitor.ui.export

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inputmonitor.domain.export.ExportResult
import com.example.inputmonitor.ui.util.DateTimeUtils
import java.text.NumberFormat
import java.util.Locale

/**
 * Screen providing user-initiated local CSV export with defense-in-depth privacy guarantees
 * (SRS.md §54, Architecture.md §58, §59, SEC-006, SEC-015).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    uiState: ExportUiState,
    onNavigateBack: () -> Unit,
    onSelectSession: (String?) -> Unit,
    onSetDropdownExpanded: (Boolean) -> Unit,
    onExportRequested: (Uri) -> Unit,
    onDismissResult: () -> Unit,
    onShareViaShareSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

    // Storage Access Framework launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            onExportRequested(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Export Data",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Privacy & Defense Notice Card (SRS.md §54, SEC-015)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security Disclosure",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Privacy & Export Defense",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Exported files are stored exclusively on your local device via Android Storage Access Framework. In accordance with zero-credential persistence, passwords, PINs, OTPs, and credit card numbers are strictly excluded and redacted.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Scope & Session Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "EXPORT SCOPE",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    ExposedDropdownMenuBox(
                        expanded = uiState.isSessionDropdownExpanded,
                        onExpandedChange = { onSetDropdownExpanded(it) }
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedSessionName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Selected Scope") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uiState.isSessionDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = uiState.isSessionDropdownExpanded,
                            onDismissRequest = { onSetDropdownExpanded(false) }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Sessions (Global Export)") },
                                onClick = { onSelectSession(null) }
                            )
                            uiState.availableSessions.forEach { session ->
                                val name = if (session.name.isNotBlank()) session.name else "Session #${session.id.take(8)}"
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = { onSelectSession(session.id) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Events in Scope:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = numberFormat.format(uiState.eventCountForScope),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (uiState.eventCountForScope > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Technical Export Parameters Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SPECIFICATION",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    ExportParameterRow(label = "Format", value = "CSV (RFC-4180 Compliant)")
                    ExportParameterRow(label = "Encoding", value = "UTF-8 with Standard CRLF")
                    ExportParameterRow(label = "Memory Profile", value = "Bounded Streaming Chunks (O(1) Heap)")
                    ExportParameterRow(label = "Destination", value = "User-Selected Local Storage (SAF)")
                    ExportParameterRow(label = "Network Transmission", value = "None (Offline Only)")
                }
            }

            // Export Progress Card (when exporting)
            if (uiState.isExporting) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Exporting Events...",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        LinearProgressIndicator(
                            progress = { uiState.exportProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Processed ${numberFormat.format(uiState.exportedCount)} of ${numberFormat.format(uiState.totalCount)} records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Primary Action Button
            Button(
                onClick = {
                    val dateTag = DateTimeUtils.generateFileTimestamp()
                    val safeSessionName = uiState.selectedSessionName
                    val cleanSessionName = safeSessionName
                        .replace(" ", "_")
                        .replace("[^a-zA-Z0-9_-]".toRegex(), "")
                    val suggestedFilename = "inputmonitor_${cleanSessionName}_$dateTag.csv"
                    createDocumentLauncher.launch(suggestedFilename)
                },
                enabled = uiState.canExport,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isExporting) "Exporting..." else "Save CSV Locally (File Picker)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Secondary Action: Share via Android Share Sheet (Phase 14)
            OutlinedButton(
                onClick = onShareViaShareSheet,
                enabled = uiState.canExport,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share CSV via Android Share Sheet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            if (uiState.eventCountForScope == 0L) {
                Text(
                    text = "There are no events recorded in the selected scope to export.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    // Export Result Dialog
    uiState.lastResult?.let { result ->
        when (result) {
            is ExportResult.Success -> {
                AlertDialog(
                    onDismissRequest = onDismissResult,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = { Text("Export Complete") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Successfully exported ${numberFormat.format(result.eventCount)} interaction events to CSV.")
                            Text(
                                "Total size: ${numberFormat.format(result.bytesWritten)} bytes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = onDismissResult) {
                            Text("Done")
                        }
                    }
                )
            }
            is ExportResult.Error -> {
                AlertDialog(
                    onDismissRequest = onDismissResult,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = { Text("Export Failed") },
                    text = {
                        Text(result.message)
                    },
                    confirmButton = {
                        Button(onClick = onDismissResult) {
                            Text("Dismiss")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExportParameterRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
