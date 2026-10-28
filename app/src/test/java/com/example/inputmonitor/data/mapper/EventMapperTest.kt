package com.example.inputmonitor.data.mapper

import com.example.inputmonitor.data.entity.InteractionEventEntity
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventMapperTest {

    @Test
    fun toDomain_mapsNormalEventWithData() {
        val entity = InteractionEventEntity(
            id = 42L,
            sessionId = "session-1",
            timestamp = 1000L,
            source = "KEYBOARD",
            eventType = "KEY_PRESS",
            packageName = "com.example.notes",
            applicationName = "Notes",
            eventData = "a",
            inputClassification = "NORMAL_TEXT",
            metadata = null
        )

        val domain = entity.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("session-1", domain.sessionId)
        assertEquals(EventSource.KEYBOARD, domain.source)
        assertEquals(EventType.KEY_PRESS, domain.eventType)
        assertEquals("a", domain.eventData)
        assertEquals(InputClassification.NORMAL_TEXT, domain.inputClassification)
    }

    @Test
    fun toDomain_stripsDataIfClassificationIsSensitive() {
        // Even if entity somehow contained data with a sensitive tag, mapping strips it
        val entity = InteractionEventEntity(
            id = 99L,
            sessionId = "session-1",
            timestamp = 1000L,
            source = "KEYBOARD",
            eventType = "KEY_PRESS",
            packageName = "com.example.bank",
            applicationName = "Bank",
            eventData = "SuperSecretPassword123",
            inputClassification = "SENSITIVE_PASSWORD",
            metadata = null
        )

        val domain = entity.toDomain()

        assertNull("Event data must be null for sensitive classification", domain.eventData)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, domain.inputClassification)
    }

    @Test
    fun toEntity_sanitizesSensitiveEventDataBeforePersistence() {
        val sensitiveEvent = InteractionEvent(
            id = 100L,
            sessionId = "session-1",
            timestamp = 2000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.KEY_PRESS,
            packageName = "com.example.login",
            applicationName = "Login",
            eventData = "123456", // Raw PIN attempted to pass through
            inputClassification = InputClassification.SENSITIVE_PIN
        )

        val entity = sensitiveEvent.toEntity()

        assertNull("Raw PIN eventData must be nullified during entity conversion", entity.eventData)
        assertEquals("SENSITIVE_PIN", entity.inputClassification)
    }
}
