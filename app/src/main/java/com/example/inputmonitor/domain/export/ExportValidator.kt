package com.example.inputmonitor.domain.export

import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent

/**
 * Validates and sanitizes interaction events prior to export (Architecture.md §59, SRS.md §54, SEC-015).
 *
 * Implements defense-in-depth export boundary protection. Ensures that even if the database
 * contained anomalous or unscrubbed records, sensitive credentials (passwords, PINs, OTPs,
 * payment cards, auth tokens) are unconditionally redacted or suppressed before reaching CSV serialization.
 */
interface ExportValidator {
    /**
     * Sanitizes an [InteractionEvent] for export output.
     * Returns a copy with [InteractionEvent.eventData] redacted if sensitive.
     */
    fun sanitizeForExport(event: InteractionEvent): InteractionEvent
}

/**
 * Default implementation of [ExportValidator].
 */
class ExportValidatorImpl : ExportValidator {

    companion object {
        const val REDACTED_MARKER = "[REDACTED]"
    }

    override fun sanitizeForExport(event: InteractionEvent): InteractionEvent {
        // Condition 1: Explicit sensitive event type
        if (event.eventType == EventType.SENSITIVE_INPUT_DETECTED) {
            return event.copy(eventData = REDACTED_MARKER)
        }

        // Condition 2: Sensitive input classification
        if (isSensitiveClassification(event.inputClassification)) {
            return event.copy(
                eventData = REDACTED_MARKER,
                eventType = EventType.SENSITIVE_INPUT_DETECTED
            )
        }

        // Condition 3: Additional defense-in-depth heuristic check for credit card numbers in plaintext
        val data = event.eventData
        if (data != null && containsPaymentCardNumber(data)) {
            return event.copy(
                eventData = REDACTED_MARKER,
                inputClassification = InputClassification.SENSITIVE_CREDIT_CARD,
                eventType = EventType.SENSITIVE_INPUT_DETECTED
            )
        }

        return event
    }

    private fun isSensitiveClassification(classification: InputClassification): Boolean {
        return classification.isSensitive
    }

    /**
     * Basic Luhn check over numeric substrings of length 13-19 to intercept stray credit card data.
     */
    private fun containsPaymentCardNumber(text: String): Boolean {
        val digits = text.filter { it.isDigit() }
        if (digits.length in 13..19 && passesLuhnCheck(digits)) {
            return true
        }
        return false
    }

    private fun passesLuhnCheck(number: String): Boolean {
        var checksum = 0
        var isSecond = false
        for (i in number.length - 1 downTo 0) {
            var digit = number[i] - '0'
            if (isSecond) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            checksum += digit
            isSecond = !isSecond
        }
        return (checksum % 10 == 0)
    }
}
