package com.example.inputmonitor.ui.statistics

import com.example.inputmonitor.domain.model.InteractionStatistics
import com.example.inputmonitor.domain.model.MonitoringSession

/**
 * UI state for the Statistics screen (Architecture.md §57, SRS.md §51).
 */
data class StatisticsUiState(
    val statistics: InteractionStatistics = InteractionStatistics(),
    val selectedSessionId: String? = null,
    val availableSessions: List<MonitoringSession> = emptyList(),
    val isLoading: Boolean = false,
    val isSessionDropdownExpanded: Boolean = false
) {
    val selectedSessionName: String
        get() {
            if (selectedSessionId == null) return "All Sessions"
            val session = availableSessions.find { it.id == selectedSessionId }
            return session?.name?.takeIf { it.isNotBlank() } ?: "Session #${selectedSessionId.take(8)}"
        }

    val hasData: Boolean
        get() = !statistics.isEmpty
}
