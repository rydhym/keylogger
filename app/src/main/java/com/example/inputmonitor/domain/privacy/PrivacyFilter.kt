package com.example.inputmonitor.domain.privacy

import com.example.inputmonitor.domain.model.RawInteractionEvent

/**
 * Security-critical filter that sanitizes raw interaction events before persistence
 * (Architecture.md §39, SECURITY.md §4, §8).
 *
 * When an event is classified as sensitive (or when defense-in-depth heuristics detect
 * unclassified sensitive card/credential patterns):
 * - [SanitizedInteractionEvent.eventData] is unconditionally set to null
 * - [SanitizedInteractionEvent.eventType] is set to [com.example.inputmonitor.domain.model.EventType.SENSITIVE_INPUT_DETECTED]
 * - Only safe metadata keys are preserved
 */
interface PrivacyFilter {

    /**
     * Sanitizes a [RawInteractionEvent] into a safe [SanitizedInteractionEvent].
     */
    fun sanitize(event: RawInteractionEvent): SanitizedInteractionEvent
}
