package com.example.inputmonitor.domain.export

import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ExportValidator] verifying export boundary defense and sensitive data redaction
 * (Architecture.md §59, SRS.md §54, SEC-015).
 */
class ExportValidatorTest {

    private lateinit var validator: ExportValidator

    @Before
    fun setUp() {
        validator = ExportValidatorImpl()
    }

    @Test
    fun sanitizeForExport_normalEvent_preservedUnmodified() {
        val event = InteractionEvent(
            sessionId = "session-1",
            timestamp = 1000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.notes",
            applicationName = "Notes",
            eventData = "Shopping List: Apples, Milk",
            inputClassification = InputClassification.NORMAL_TEXT
        )

        val sanitized = validator.sanitizeForExport(event)

        assertEquals("Shopping List: Apples, Milk", sanitized.eventData)
        assertEquals(EventType.TEXT_COMMITTED, sanitized.eventType)
        assertEquals(InputClassification.NORMAL_TEXT, sanitized.inputClassification)
    }

    @Test
    fun sanitizeForExport_sensitivePassword_redacted() {
        val event = InteractionEvent(
            sessionId = "session-1",
            timestamp = 1000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.login",
            applicationName = "Login",
            eventData = "P@ssword123!",
            inputClassification = InputClassification.SENSITIVE_PASSWORD
        )

        val sanitized = validator.sanitizeForExport(event)

        assertEquals("[REDACTED]", sanitized.eventData)
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
    }

    @Test
    fun sanitizeForExport_sensitivePin_redacted() {
        val event = InteractionEvent(
            sessionId = "session-1",
            timestamp = 1000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.KEY_PRESS,
            packageName = "com.example.banking",
            applicationName = "Bank",
            eventData = "1234",
            inputClassification = InputClassification.SENSITIVE_PIN
        )

        val sanitized = validator.sanitizeForExport(event)

        assertEquals("[REDACTED]", sanitized.eventData)
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
    }

    @Test
    fun sanitizeForExport_sensitiveOtp_redacted() {
        val event = InteractionEvent(
            sessionId = "session-1",
            timestamp = 1000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.auth",
            applicationName = "Auth",
            eventData = "948201",
            inputClassification = InputClassification.SENSITIVE_OTP
        )

        val sanitized = validator.sanitizeForExport(event)

        assertEquals("[REDACTED]", sanitized.eventData)
    }

    @Test
    fun sanitizeForExport_sensitiveEventType_redacted() {
        val event = InteractionEvent(
            sessionId = "session-1",
            timestamp = 1000L,
            source = EventSource.ACCESSIBILITY,
            eventType = EventType.SENSITIVE_INPUT_DETECTED,
            packageName = "com.example.app",
            applicationName = "App",
            eventData = null,
            inputClassification = InputClassification.UNSPECIFIED
        )

        val sanitized = validator.sanitizeForExport(event)

        assertEquals("[REDACTED]", sanitized.eventData)
    }

    @Test
    fun sanitizeForExport_heuristicCreditCard_redacted() {
        // Valid Luhn credit card format
        val event = InteractionEvent(
            sessionId = "session-1",
            timestamp = 1000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.shop",
            applicationName = "Shop",
            eventData = "Card: 4111 1111 1111 1111",
            inputClassification = InputClassification.NORMAL_TEXT
        )

        val sanitized = validator.sanitizeForExport(event)

        assertEquals("[REDACTED]", sanitized.eventData)
        assertEquals(InputClassification.SENSITIVE_CREDIT_CARD, sanitized.inputClassification)
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
    }
}
