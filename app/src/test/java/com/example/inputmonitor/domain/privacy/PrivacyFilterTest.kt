package com.example.inputmonitor.domain.privacy

import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.RawInteractionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PrivacyFilter] (Architecture.md §39, §40, SECURITY.md §5, §6, §8, TESTING.md §5, §6).
 */
class PrivacyFilterTest {

    private lateinit var privacyFilter: PrivacyFilter

    @Before
    fun setUp() {
        privacyFilter = PrivacyFilterImpl()
    }

    @Test
    fun sanitize_sensitivePasswordEvent_discardsCandidateDataAndChangesEventType() {
        val rawEvent = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.login",
            candidateData = "SuperSecretPassword123!",
            inputClassification = InputClassification.SENSITIVE_PASSWORD,
            metadata = mapOf("fieldCategory" to "PASSWORD", "key_action" to "Character")
        )

        val sanitized = privacyFilter.sanitize(rawEvent)

        assertNull("Event data must be null for sensitive password", sanitized.eventData)
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, sanitized.inputClassification)
        assertEquals("com.example.login", sanitized.packageName)
        assertEquals(1700000000000L, sanitized.timestamp)
    }

    @Test
    fun sanitize_sensitivePinEvent_discardsCandidateData() {
        val rawEvent = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.KEY_PRESS,
            packageName = "com.example.bank",
            candidateData = "1234",
            inputClassification = InputClassification.SENSITIVE_PIN
        )

        val sanitized = privacyFilter.sanitize(rawEvent)

        assertNull("PIN candidate data must be stripped", sanitized.eventData)
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
        assertEquals(InputClassification.SENSITIVE_PIN, sanitized.inputClassification)
    }

    @Test
    fun sanitize_sensitiveOtpEvent_discardsCandidateData() {
        val rawEvent = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.auth",
            candidateData = "849201",
            inputClassification = InputClassification.SENSITIVE_OTP
        )

        val sanitized = privacyFilter.sanitize(rawEvent)

        assertNull("OTP candidate data must be stripped", sanitized.eventData)
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
        assertEquals(InputClassification.SENSITIVE_OTP, sanitized.inputClassification)
    }

    @Test
    fun sanitize_sensitiveCreditCardEvent_discardsCandidateData() {
        val rawEvent = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.checkout",
            candidateData = "4111111111111111",
            inputClassification = InputClassification.SENSITIVE_CREDIT_CARD
        )

        val sanitized = privacyFilter.sanitize(rawEvent)

        assertNull("Credit card candidate data must be stripped", sanitized.eventData)
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
        assertEquals(InputClassification.SENSITIVE_CREDIT_CARD, sanitized.inputClassification)
    }

    @Test
    fun sanitize_sensitiveEvent_sanitizesUnsafeMetadataKeys() {
        val rawEvent = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.KEY_PRESS,
            packageName = "com.example.login",
            candidateData = "Secret",
            inputClassification = InputClassification.SENSITIVE_PASSWORD,
            metadata = mapOf(
                "action" to "DONE",
                "fieldCategory" to "PASSWORD",
                "input_text" to "Secret",
                "candidate_data" to "Secret",
                "secret_key" to "mySecret"
            )
        )

        val sanitized = privacyFilter.sanitize(rawEvent)

        assertTrue(sanitized.metadata.containsKey("action"))
        assertTrue(sanitized.metadata.containsKey("fieldCategory"))
        assertFalse(sanitized.metadata.containsKey("input_text"))
        assertFalse(sanitized.metadata.containsKey("candidate_data"))
        assertFalse(sanitized.metadata.containsKey("secret_key"))
    }

    @Test
    fun sanitize_defenseInDepthLuhnCardDetection_redactsEvenIfUnclassified() {
        // Valid Visa test card number passing Luhn algorithm
        val validVisa = "4111111111111111"
        val rawEvent = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.unclassified",
            candidateData = validVisa,
            inputClassification = InputClassification.NORMAL_TEXT // Unclassified or misclassified
        )

        val sanitized = privacyFilter.sanitize(rawEvent)

        assertNull("Luhn credit card must be redacted by content heuristic", sanitized.eventData)
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
        assertEquals(InputClassification.SENSITIVE_CREDIT_CARD, sanitized.inputClassification)
        assertEquals("content_luhn_filter", sanitized.metadata["redacted_by"])
    }

    @Test
    fun sanitize_normalTextEvent_preservesCandidateDataAndEventType() {
        val rawEvent = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.notes",
            candidateData = "Grocery list: milk, eggs, bread",
            inputClassification = InputClassification.NORMAL_TEXT,
            metadata = mapOf("action" to "↵")
        )

        val sanitized = privacyFilter.sanitize(rawEvent)

        assertEquals("Grocery list: milk, eggs, bread", sanitized.eventData)
        assertEquals(EventType.TEXT_COMMITTED, sanitized.eventType)
        assertEquals(InputClassification.NORMAL_TEXT, sanitized.inputClassification)
        assertEquals("com.example.notes", sanitized.packageName)
    }

    @Test
    fun sanitizedInteractionEvent_invariantViolation_throwsIllegalArgumentException() {
        // Attempting to construct a SanitizedInteractionEvent with sensitive classification and non-null data
        // must fail immediately
        assertThrows(IllegalArgumentException::class.java) {
            SanitizedInteractionEvent(
                timestamp = 1700000000000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.app",
                eventData = "leakedPassword",
                inputClassification = InputClassification.SENSITIVE_PASSWORD
            )
        }
    }
}
