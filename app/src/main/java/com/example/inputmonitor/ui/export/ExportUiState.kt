package com.example.inputmonitor.ui.export

import com.example.inputmonitor.domain.export.ExportResult
import com.example.inputmonitor.domain.model.MonitoringSession

/**
 * Presentation state for local data export (SRS.md §54, Architecture.md §58).
 */
data class ExportUiState(
    val availableSessions: List<MonitoringSession> = emptyList(),
    val selectedSessionId: String? = null,
    val isSessionDropdownExpanded: Boolean = false,
    val isExporting: Boolean = false,
    val exportedCount: Long = 0L,
    val totalCount: Long = 0L,
    val exportProgress: Float = 0f,
    val lastResult: ExportResult? = null,
    val showConfirmationDialog: Boolean = false,
    val eventCountForScope: Long = 0L
) {
    val selectedSessionName: String
        get() {
            if (selectedSessionId == null) return "All Sessions"
            val session = availableSessions.find { it.id == selectedSessionId }
            return when {
                session == null -> "Unknown Session"
                session.name.isNotBlank() -> session.name
                else -> "Session #${session.id.take(8)}"
            }
        }

    val canExport: Boolean
        get() = !isExporting && eventCountForScope > 0L
}
