package com.example.inputmonitor.domain.model

import com.example.inputmonitor.ui.statistics.StatisticsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for statistics domain models and state calculations (SRS.md §51, Architecture.md §57).
 */
class InteractionStatisticsTest {

    @Test
    fun statisticsSummary_durationCalculation() {
        val emptySummary = StatisticsSummary()
        assertEquals(0.0, emptySummary.durationMinutes, 0.001)

        val sameTimeSummary = StatisticsSummary(
            firstEventTimestamp = 1000L,
            lastEventTimestamp = 1000L
        )
        assertEquals(0.0, sameTimeSummary.durationMinutes, 0.001)

        val durationSummary = StatisticsSummary(
            firstEventTimestamp = 1000L,
            lastEventTimestamp = 1000L + 120_000L // 2 minutes
        )
        assertEquals(2.0, durationSummary.durationMinutes, 0.001)
    }

    @Test
    fun statisticsSummary_velocityMetrics() {
        val summary = StatisticsSummary(
            totalEvents = 120,
            firstEventTimestamp = 1000L,
            lastEventTimestamp = 1000L + 60_000L, // 1 minute
            eventsPerMinute = 120.0
        )
        assertEquals(120.0, summary.eventsPerMinute, 0.001)
        assertEquals(1.0, summary.durationMinutes, 0.001)
    }

    @Test
    fun interactionStatistics_emptyState() {
        val empty = InteractionStatistics()
        assertTrue(empty.isEmpty)

        val populated = InteractionStatistics(
            summary = StatisticsSummary(totalEvents = 1)
        )
        assertFalse(populated.isEmpty)
    }

    @Test
    fun appUsageStat_properties() {
        val appStat = AppUsageStat(
            packageName = "com.example.notes",
            applicationName = "Notes App",
            count = 50,
            percentage = 50.0f,
            lastActiveTimestamp = 123456789L
        )
        assertEquals("com.example.notes", appStat.packageName)
        assertEquals("Notes App", appStat.applicationName)
        assertEquals(50L, appStat.count)
        assertEquals(50.0f, appStat.percentage, 0.001f)
        assertEquals(123456789L, appStat.lastActiveTimestamp)
    }

    @Test
    fun hourlyActivityStat_properties() {
        val hourStat = HourlyActivityStat(
            hourOfDay = 14,
            count = 42,
            normalizedRatio = 0.84f
        )
        assertEquals(14, hourStat.hourOfDay)
        assertEquals(42L, hourStat.count)
        assertEquals(0.84f, hourStat.normalizedRatio, 0.001f)
    }

    @Test
    fun statisticsUiState_sessionNameResolution() {
        val globalState = StatisticsUiState(selectedSessionId = null)
        assertEquals("All Sessions", globalState.selectedSessionName)
        assertFalse(globalState.hasData)

        val session1 = MonitoringSession(
            id = "session-123456789",
            name = "Morning Research",
            state = SessionState.ACTIVE,
            startTime = 1000L
        )

        val activeSessionState = StatisticsUiState(
            selectedSessionId = "session-123456789",
            availableSessions = listOf(session1)
        )
        assertEquals("Morning Research", activeSessionState.selectedSessionName)

        val unnamedSession = MonitoringSession(
            id = "session-999999999",
            name = "",
            state = SessionState.STOPPED,
            startTime = 2000L
        )
        val fallbackState = StatisticsUiState(
            selectedSessionId = "session-999999999",
            availableSessions = listOf(unnamedSession)
        )
        assertEquals("Session #session-", fallbackState.selectedSessionName)
    }
}
