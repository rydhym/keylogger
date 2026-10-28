package com.example.inputmonitor.domain.model

/**
 * Filter criteria for interaction event queries (Architecture.md §56, SRS.md §49, §50).
 */
data class EventFilterCriteria(
    val sessionId: String? = null,
    val packageName: String? = null,
    val eventType: EventType? = null,
    val source: EventSource? = null,
    val classification: InputClassification? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val searchQuery: String? = null
) {
    val hasActiveFilters: Boolean
        get() = sessionId != null ||
                packageName != null ||
                eventType != null ||
                source != null ||
                classification != null ||
                startTime != null ||
                endTime != null ||
                !searchQuery.isNullOrBlank()

    fun clear(): EventFilterCriteria = EventFilterCriteria()
}
