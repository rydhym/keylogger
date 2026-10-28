package com.example.inputmonitor.domain.model

/**
 * Domain model representing a persisted interaction event (Architecture.md §35).
 *
 * Guaranteed invariant: If [inputClassification.isSensitive] is true,
 * [eventData] MUST be null (SECURITY.md §4, §5).
 */
data class InteractionEvent(
    val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val source: EventSource,
    val eventType: EventType,
    val packageName: String?,
    val applicationName: String?,
    val eventData: String?,
    val inputClassification: InputClassification,
    val metadata: String? = null
)
