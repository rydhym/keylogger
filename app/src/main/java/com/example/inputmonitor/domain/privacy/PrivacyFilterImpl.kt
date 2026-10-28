package com.example.inputmonitor.domain.privacy

import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.RawInteractionEvent

/**
 * Production implementation of [PrivacyFilter] (Architecture.md §39, §40, SECURITY.md §5, §6, §8).
 *
 * Provides defense-in-depth sanitization:
 * 1. Unconditionally strips candidateData when [InputClassification.isSensitive] is true.
 * 2. Emits [EventType.SENSITIVE_INPUT_DETECTED] for sensitive inputs.
 * 3. Filters out any sensitive metadata keys.
 * 4. Scans non-sensitive candidateData for leaked payment card PANs using the Luhn checksum,
 *    retroactively classifying and redacting them if found.
 */
class PrivacyFilterImpl : PrivacyFilter {

    override fun sanitize(event: RawInteractionEvent): SanitizedInteractionEvent {
        val isExplicitlySensitive = event.inputClassification.isSensitive

        if (isExplicitlySensitive) {
            return SanitizedInteractionEvent(
                timestamp = event.timestamp,
                source = event.source,
                eventType = EventType.SENSITIVE_INPUT_DETECTED,
                packageName = event.packageName,
                eventData = null,
                inputClassification = event.inputClassification,
                metadata = sanitizeMetadata(event.metadata)
            )
        }

        // Defense-in-depth content heuristic: detect leaked payment card numbers
        if (containsPaymentCardNumber(event.candidateData)) {
            return SanitizedInteractionEvent(
                timestamp = event.timestamp,
                source = event.source,
                eventType = EventType.SENSITIVE_INPUT_DETECTED,
                packageName = event.packageName,
                eventData = null,
                inputClassification = InputClassification.SENSITIVE_CREDIT_CARD,
                metadata = sanitizeMetadata(event.metadata) + mapOf("redacted_by" to "content_luhn_filter")
            )
        }

        return SanitizedInteractionEvent(
            timestamp = event.timestamp,
            source = event.source,
            eventType = event.eventType,
            packageName = event.packageName,
            eventData = event.candidateData,
            inputClassification = event.inputClassification,
            metadata = event.metadata
        )
    }

    /**
     * Strips any metadata entries whose keys or values could inadvertently leak typed text.
     */
    private fun sanitizeMetadata(metadata: Map<String, String>): Map<String, String> {
        val unsafeKeyKeywords = setOf("text", "data", "candidate", "input", "value", "content", "query", "secret")
        return metadata.filter { (key, _) ->
            val lower = key.lowercase()
            unsafeKeyKeywords.none { lower.contains(it) }
        }
    }

    /**
     * Checks if the text contains a 13-19 digit sequence that passes the Luhn checksum algorithm.
     */
    private fun containsPaymentCardNumber(text: String?): Boolean {
        if (text == null || text.length < 13) return false
        val digits = text.filter { it.isDigit() }
        if (digits.length !in 13..19) return false

        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
