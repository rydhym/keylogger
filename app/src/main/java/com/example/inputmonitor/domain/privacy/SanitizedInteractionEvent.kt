package com.example.inputmonitor.domain.privacy

import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification

/**
 * Result of [PrivacyFilter.sanitize] processing (Architecture.md §39, SECURITY.md §4, §8).
 *
 * Enforces an architectural safety invariant: if [inputClassification] represents sensitive
 * credentials, [eventData] must be unconditionally null.
 */
data class SanitizedInteractionEvent(
    val timestamp: Long,
    val source: EventSource,
    val eventType: EventType,
    val packageName: String?,
    val eventData: String?,
    val inputClassification: InputClassification,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        if (inputClassification.isSensitive) {
            require(eventData == null) {
                "SECURITY INVARIANT VIOLATION: SanitizedInteractionEvent must never contain eventData for sensitive classification: $inputClassification"
            }
        }
    }
}
