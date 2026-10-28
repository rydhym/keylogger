package com.example.inputmonitor.ui.dashboard

import com.example.inputmonitor.domain.model.EventCounts
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.model.SetupState

/**
 * UI state representation for the main Dashboard screen (Architecture.md §55, SRS.md §47).
 *
 * Aggregates system capability readiness, session lifecycle state, active session metadata,
 * aggregate event metrics, and the most recent application context.
 */
data class DashboardUiState(
    val setupState: SetupState = SetupState(),
    val sessionState: SessionState = SessionState.INACTIVE,
    val activeSession: MonitoringSession? = null,
    val eventCounts: EventCounts = EventCounts(),
    val currentApplicationLabel: String? = null,
    val currentPackageName: String? = null
) {
    val totalEvents: Long get() = eventCounts.total
    val keyboardEvents: Long get() = eventCounts.keyboard
    val accessibilityEvents: Long get() = eventCounts.accessibility
    val clickEvents: Long get() = eventCounts.clicks
    val focusEvents: Long get() = eventCounts.focus
    val scrollEvents: Long get() = eventCounts.scrolls
    val windowEvents: Long get() = eventCounts.windowChanges
    val hasIncompleteServices: Boolean get() = !setupState.isComplete
}
