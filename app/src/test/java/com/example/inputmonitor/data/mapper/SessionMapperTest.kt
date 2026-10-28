package com.example.inputmonitor.data.mapper

import com.example.inputmonitor.data.entity.MonitoringSessionEntity
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionMapperTest {

    @Test
    fun toDomain_mapsAllFieldsCorrectly() {
        val entity = MonitoringSessionEntity(
            id = "session-123",
            name = "Test Session",
            startTime = 1000L,
            endTime = 2000L,
            state = "ACTIVE",
            androidVersion = "15",
            deviceModel = "Pixel 9",
            appVersion = "0.1.0",
            notes = "Test notes"
        )

        val domain = entity.toDomain()

        assertEquals("session-123", domain.id)
        assertEquals("Test Session", domain.name)
        assertEquals(1000L, domain.startTime)
        assertEquals(2000L, domain.endTime)
        assertEquals(SessionState.ACTIVE, domain.state)
        assertEquals("15", domain.androidVersion)
        assertEquals("Pixel 9", domain.deviceModel)
        assertEquals("0.1.0", domain.appVersion)
        assertEquals("Test notes", domain.notes)
    }

    @Test
    fun toDomain_handlesUnknownStateGracefully() {
        val entity = MonitoringSessionEntity(
            id = "session-unknown",
            name = "Corrupted State Session",
            startTime = 1000L,
            endTime = null,
            state = "UNKNOWN_OR_INVALID_STATE",
            androidVersion = "15",
            deviceModel = "Pixel 9",
            appVersion = "0.1.0",
            notes = null
        )

        val domain = entity.toDomain()

        assertEquals(SessionState.STOPPED, domain.state)
        assertNull(domain.endTime)
        assertNull(domain.notes)
    }

    @Test
    fun toEntity_mapsDomainToEntityAccurately() {
        val domain = MonitoringSession(
            id = "session-456",
            name = "Active Session",
            startTime = 5000L,
            endTime = null,
            state = SessionState.PAUSED,
            androidVersion = "15",
            deviceModel = "Pixel 9",
            appVersion = "0.1.0",
            notes = null
        )

        val entity = domain.toEntity()

        assertEquals("session-456", entity.id)
        assertEquals("Active Session", entity.name)
        assertEquals(5000L, entity.startTime)
        assertNull(entity.endTime)
        assertEquals("PAUSED", entity.state)
        assertNull(entity.notes)
    }
}
