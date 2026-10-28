package com.example.inputmonitor.ui.events

import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.MonitoringSession

/**
 * State representing the interaction event timeline viewer (Architecture.md §56, SRS.md §48).
 */
data class TimelineUiState(
    val events: List<InteractionEvent> = emptyList(),
    val totalMatchingEvents: Int = 0,
    val filterCriteria: EventFilterCriteria = EventFilterCriteria(),
    val distinctPackages: List<String> = emptyList(),
    val sessions: List<MonitoringSession> = emptyList(),
    val activeSession: MonitoringSession? = null,
    val currentPage: Int = 0,
    val pageSize: Int = 50,
    val selectedEventForDetail: InteractionEvent? = null,
    val isFilterDialogVisible: Boolean = false,
    val searchQueryInput: String = "",
    val searchSuggestions: List<com.example.inputmonitor.domain.model.SearchSuggestion> = emptyList()
) {
    val totalPages: Int
        get() = if (totalMatchingEvents <= 0) 1 else ((totalMatchingEvents + pageSize - 1) / pageSize)

    val hasNextPage: Boolean
        get() = currentPage < totalPages - 1

    val hasPreviousPage: Boolean
        get() = currentPage > 0

    val pageRangeStart: Int
        get() = if (totalMatchingEvents == 0) 0 else (currentPage * pageSize + 1)

    val pageRangeEnd: Int
        get() = minOf((currentPage + 1) * pageSize, totalMatchingEvents)
}
