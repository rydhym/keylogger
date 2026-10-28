package com.example.inputmonitor.ui.statistics

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.dao.AppUsageTuple
import com.example.inputmonitor.data.dao.HourlyActivityTuple
import com.example.inputmonitor.data.dao.InteractionEventDao
import com.example.inputmonitor.data.dao.StatisticsSummaryTuple
import com.example.inputmonitor.data.entity.InteractionEventEntity
import com.example.inputmonitor.data.repository.EventRepositoryImpl
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end integration test verifying reactive statistics calculation, app usage percentage breakdown,
 * 24-hour circadian histogram distribution, session-level filtering, and ViewModel state mapping
 * (Architecture.md §57, IMPLEMENTATION_PLAN.md §53, SRS.md §51).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsFlowIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class IntegrationEventDaoMock : InteractionEventDao {
        val storage = mutableListOf<InteractionEventEntity>()

        override suspend fun insertEvent(event: InteractionEventEntity): Long {
            storage.add(event)
            return event.id
        }

        override suspend fun insertEvents(events: List<InteractionEventEntity>): List<Long> {
            storage.addAll(events)
            return events.map { it.id }
        }

        override suspend fun getEventById(id: Long): InteractionEventEntity? = storage.find { it.id == id }

        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEventEntity>> =
            flowOf(storage.filter { it.sessionId == sessionId })

        override suspend fun getEventsForSession(sessionId: String): List<InteractionEventEntity> =
            storage.filter { it.sessionId == sessionId }

        override suspend fun getEventsForSessionPaged(
            sessionId: String,
            limit: Int,
            offset: Int
        ): List<InteractionEventEntity> = storage.filter { it.sessionId == sessionId }.drop(offset).take(limit)

        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEventEntity> = storage

        override suspend fun getEventCountForSession(sessionId: String): Int =
            storage.count { it.sessionId == sessionId }

        override fun observeEventCountForSession(sessionId: String): Flow<Int> =
            flowOf(storage.count { it.sessionId == sessionId })

        override fun observeEventCounts(sessionId: String?): Flow<com.example.inputmonitor.data.dao.EventCountsTuple?> =
            flowOf(com.example.inputmonitor.data.dao.EventCountsTuple(total = storage.size.toLong()))

        override fun observeLatestEvent(): Flow<InteractionEventEntity?> =
            flowOf(storage.maxByOrNull { it.timestamp })

        override fun observeEventsFilteredPaged(
            sessionId: String?,
            packageFilter: String?,
            eventTypeFilter: String?,
            sourceFilter: String?,
            classificationFilter: String?,
            startTime: Long?,
            endTime: Long?,
            searchQuery: String?,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEventEntity>> = flowOf(storage)

        override fun observeFilteredCount(
            sessionId: String?,
            packageFilter: String?,
            eventTypeFilter: String?,
            sourceFilter: String?,
            classificationFilter: String?,
            startTime: Long?,
            endTime: Long?,
            searchQuery: String?
        ): Flow<Int> = flowOf(storage.size)

        override fun observeDistinctPackageNames(): Flow<List<String>> =
            flowOf(storage.mapNotNull { it.packageName }.distinct().sorted())

        override fun observeMatchingApplications(query: String, limit: Int): Flow<List<String>> =
            flowOf(emptyList())

        override fun observeMatchingPackages(query: String, limit: Int): Flow<List<String>> =
            flowOf(emptyList())

        override suspend fun getMatchingApplications(query: String, limit: Int): List<String> = emptyList()
        override suspend fun getMatchingPackages(query: String, limit: Int): List<String> = emptyList()

        override fun observeStatisticsSummary(sessionId: String?): Flow<StatisticsSummaryTuple?> =
            flowOf(computeSummary(sessionId))

        override suspend fun getStatisticsSummary(sessionId: String?): StatisticsSummaryTuple? =
            computeSummary(sessionId)

        override fun observeAppUsageStats(sessionId: String?): Flow<List<AppUsageTuple>> =
            flowOf(computeAppUsage(sessionId))

        override suspend fun getAppUsageStats(sessionId: String?): List<AppUsageTuple> =
            computeAppUsage(sessionId)

        override fun observeHourlyActivity(sessionId: String?): Flow<List<HourlyActivityTuple>> =
            flowOf(computeHourly(sessionId))

        override suspend fun getHourlyActivity(sessionId: String?): List<HourlyActivityTuple> =
            computeHourly(sessionId)

        override suspend fun deleteEventsForSession(sessionId: String): Int = 0
        override suspend fun deleteAllEvents(): Int = 0

        private fun computeSummary(sessionId: String?): StatisticsSummaryTuple {
            val matching = if (sessionId != null) storage.filter { it.sessionId == sessionId } else storage
            return StatisticsSummaryTuple(
                total = matching.size.toLong(),
                keyboard = matching.count { it.source == "KEYBOARD" }.toLong(),
                accessibility = matching.count { it.source == "ACCESSIBILITY" }.toLong(),
                clicks = matching.count { it.eventType == "VIEW_CLICKED" }.toLong(),
                focus = matching.count { it.eventType == "VIEW_FOCUSED" }.toLong(),
                scrolls = matching.count { it.eventType == "VIEW_SCROLLED" }.toLong(),
                windowChanges = matching.count { it.eventType == "WINDOW_STATE_CHANGED" || it.eventType == "WINDOWS_CHANGED" }.toLong(),
                sensitiveSuppressions = matching.count { it.eventType == "SENSITIVE_INPUT_DETECTED" || it.inputClassification.startsWith("SENSITIVE_") }.toLong(),
                distinctApps = matching.mapNotNull { it.packageName }.distinct().size,
                firstTimestamp = matching.minOfOrNull { it.timestamp },
                lastTimestamp = matching.maxOfOrNull { it.timestamp }
            )
        }

        private fun computeAppUsage(sessionId: String?): List<AppUsageTuple> {
            val matching = if (sessionId != null) storage.filter { it.sessionId == sessionId } else storage
            return matching.filter { it.packageName != null }
                .groupBy { it.packageName!! }
                .map { (pkg, evts) ->
                    AppUsageTuple(
                        packageName = pkg,
                        applicationName = evts.firstNotNullOfOrNull { it.applicationName } ?: pkg,
                        eventCount = evts.size.toLong(),
                        lastActiveTimestamp = evts.maxOf { it.timestamp }
                    )
                }
                .sortedByDescending { it.eventCount }
        }

        private fun computeHourly(sessionId: String?): List<HourlyActivityTuple> {
            val matching = if (sessionId != null) storage.filter { it.sessionId == sessionId } else storage
            return matching.groupBy { (it.timestamp / (1000 * 3600) % 24).toInt() }
                .map { (hour, evts) ->
                    HourlyActivityTuple(
                        hourOfDay = hour,
                        eventCount = evts.size.toLong()
                    )
                }
                .sortedBy { it.hourOfDay }
        }
    }

    private class FakeSessionRepository : SessionRepository {
        val sessionsFlow = MutableStateFlow<List<MonitoringSession>>(emptyList())
        override fun observeAllSessions(): Flow<List<MonitoringSession>> = sessionsFlow.asStateFlow()
        override suspend fun getAllSessions(): List<MonitoringSession> = sessionsFlow.value
        override fun observeActiveSession(): Flow<MonitoringSession?> = flowOf(null)
        override suspend fun getActiveSession(): MonitoringSession? = null
        override suspend fun createSession(session: MonitoringSession) {
            sessionsFlow.value = sessionsFlow.value + session
        }
        override suspend fun updateSession(session: MonitoringSession) {
            sessionsFlow.value = sessionsFlow.value.map { if (it.id == session.id) session else it }
        }
        override suspend fun getSessionById(id: String): MonitoringSession? = sessionsFlow.value.find { it.id == id }
        override fun observeSessionById(id: String): Flow<MonitoringSession?> = flowOf(sessionsFlow.value.find { it.id == id })
        override suspend fun finishSession(id: String, endTime: Long): Boolean = true
        override suspend fun deleteSession(id: String): Boolean = true
        override suspend fun deleteAllSessions(): Boolean = true
    }

    private lateinit var daoMock: IntegrationEventDaoMock
    private lateinit var eventRepository: EventRepositoryImpl
    private lateinit var sessionRepository: FakeSessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        daoMock = IntegrationEventDaoMock()
        eventRepository = EventRepositoryImpl(daoMock, testDispatchers)
        sessionRepository = FakeSessionRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun eventRepository_calculatesFullCircadian24HoursAndAppPercentages() = runTest {
        // Populate mock events across 2 sessions, 2 apps, and different simulated hours
        val baseHour10 = 10L * 3600 * 1000
        val baseHour14 = 14L * 3600 * 1000

        val entities = listOf(
            InteractionEventEntity(
                id = 1,
                sessionId = "session-A",
                timestamp = baseHour10,
                source = "KEYBOARD",
                eventType = "TEXT_COMMITTED",
                packageName = "com.example.notes",
                applicationName = "Notes App",
                eventData = "Meeting notes",
                inputClassification = "NORMAL_TEXT",
                metadata = null
            ),
            InteractionEventEntity(
                id = 2,
                sessionId = "session-A",
                timestamp = baseHour10 + 60_000,
                source = "KEYBOARD",
                eventType = "KEY_PRESS",
                packageName = "com.example.notes",
                applicationName = "Notes App",
                eventData = "a",
                inputClassification = "NORMAL_TEXT",
                metadata = null
            ),
            InteractionEventEntity(
                id = 3,
                sessionId = "session-A",
                timestamp = baseHour10 + 120_000,
                source = "ACCESSIBILITY",
                eventType = "VIEW_CLICKED",
                packageName = "com.example.notes",
                applicationName = "Notes App",
                eventData = "Save Button",
                inputClassification = "NORMAL_TEXT",
                metadata = null
            ),
            InteractionEventEntity(
                id = 4,
                sessionId = "session-B",
                timestamp = baseHour14,
                source = "ACCESSIBILITY",
                eventType = "VIEW_SCROLLED",
                packageName = "com.example.browser",
                applicationName = "Browser",
                eventData = "Web page",
                inputClassification = "NORMAL_TEXT",
                metadata = null
            ),
            InteractionEventEntity(
                id = 5,
                sessionId = "session-B",
                timestamp = baseHour14 + 60_000,
                source = "ACCESSIBILITY",
                eventType = "SENSITIVE_INPUT_DETECTED",
                packageName = "com.example.browser",
                applicationName = "Browser",
                eventData = null,
                inputClassification = "SENSITIVE_PASSWORD",
                metadata = null
            )
        )
        daoMock.storage.addAll(entities)

        // Observe global statistics (across all sessions)
        val globalStats = eventRepository.observeStatistics(null).first()

        // 1. Summary assertions
        assertEquals(5L, globalStats.summary.totalEvents)
        assertEquals(2L, globalStats.summary.keyboardEvents)
        assertEquals(3L, globalStats.summary.accessibilityEvents)
        assertEquals(1L, globalStats.summary.clicks)
        assertEquals(1L, globalStats.summary.scrollEvents)
        assertEquals(1L, globalStats.summary.sensitiveSuppressions)
        assertEquals(2, globalStats.summary.distinctApps)
        assertFalse(globalStats.isEmpty)

        // 2. App Usage percentages
        // Notes App: 3 / 5 = 60.0%
        // Browser: 2 / 5 = 40.0%
        assertEquals(2, globalStats.appUsage.size)
        val notesStat = globalStats.appUsage.find { it.packageName == "com.example.notes" }
        assertNotNull(notesStat)
        assertEquals(3L, notesStat?.count)
        assertEquals(60.0f, notesStat!!.percentage, 0.01f)

        val browserStat = globalStats.appUsage.find { it.packageName == "com.example.browser" }
        assertNotNull(browserStat)
        assertEquals(2L, browserStat?.count)
        assertEquals(40.0f, browserStat!!.percentage, 0.01f)

        // 3. Circadian 24-hour histogram
        // Full 24 hours guaranteed
        assertEquals(24, globalStats.hourlyActivity.size)
        val hour10Stat = globalStats.hourlyActivity.find { it.hourOfDay == 10 }
        assertNotNull(hour10Stat)
        assertEquals(3L, hour10Stat?.count)
        assertEquals(1.0f, hour10Stat!!.normalizedRatio, 0.01f) // Hour 10 has max count (3), so ratio is 1.0

        val hour14Stat = globalStats.hourlyActivity.find { it.hourOfDay == 14 }
        assertNotNull(hour14Stat)
        assertEquals(2L, hour14Stat?.count)
        assertEquals(2f / 3f, hour14Stat!!.normalizedRatio, 0.01f)

        // Missing hour (e.g., hour 3) should have count = 0, ratio = 0
        val hour3Stat = globalStats.hourlyActivity.find { it.hourOfDay == 3 }
        assertNotNull(hour3Stat)
        assertEquals(0L, hour3Stat?.count)
        assertEquals(0f, hour3Stat!!.normalizedRatio, 0.01f)

        // 4. Session-filtered statistics
        val sessionAStats = eventRepository.observeStatistics("session-A").first()
        assertEquals(3L, sessionAStats.summary.totalEvents)
        assertEquals(2L, sessionAStats.summary.keyboardEvents)
        assertEquals(1L, sessionAStats.summary.accessibilityEvents)
        assertEquals(1, sessionAStats.appUsage.size)
        assertEquals(100.0f, sessionAStats.appUsage.first().percentage, 0.01f)
    }

    @Test
    fun statisticsViewModel_endToEndSessionSelectionAndStateUpdates() = runTest {
        val session1 = MonitoringSession(
            id = "sess-1",
            name = "Session Alpha",
            state = SessionState.STOPPED,
            startTime = 1000L
        )
        val session2 = MonitoringSession(
            id = "sess-2",
            name = "Session Beta",
            state = SessionState.ACTIVE,
            startTime = 5000L
        )
        sessionRepository.sessionsFlow.value = listOf(session1, session2)

        daoMock.storage.add(
            InteractionEventEntity(
                id = 1,
                sessionId = "sess-1",
                timestamp = 2000L,
                source = "KEYBOARD",
                eventType = "TEXT_COMMITTED",
                packageName = "com.example.notes",
                applicationName = "Notes",
                eventData = "Test",
                inputClassification = "NORMAL_TEXT",
                metadata = null
            )
        )

        val viewModel = StatisticsViewModel(eventRepository, sessionRepository)
        advanceUntilIdle()

        // Initially "All Sessions" is selected
        var state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasData)
        assertEquals(1L, state.statistics.summary.totalEvents)
        assertEquals("All Sessions", state.selectedSessionName)
        assertEquals(2, state.availableSessions.size)

        // Switch to Session Beta (which has 0 events)
        viewModel.selectSession("sess-2")
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals("Session Beta", state.selectedSessionName)
        assertEquals("sess-2", state.selectedSessionId)
        assertEquals(0L, state.statistics.summary.totalEvents)
        assertFalse(state.hasData)
        assertTrue(state.statistics.isEmpty)

        // Switch back to "All Sessions"
        viewModel.selectSession(null)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals("All Sessions", state.selectedSessionName)
        assertEquals(1L, state.statistics.summary.totalEvents)
        assertTrue(state.hasData)
    }
}
