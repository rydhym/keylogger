package com.example.inputmonitor.ui.statistics

import com.example.inputmonitor.domain.model.AppUsageStat
import com.example.inputmonitor.domain.model.HourlyActivityStat
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.InteractionStatistics
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.model.StatisticsSummary
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [StatisticsViewModel] verifying reactive aggregation, session filtering, and dropdown state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeEventRepository : EventRepository {
        val statisticsFlow = MutableStateFlow(
            InteractionStatistics(
                summary = StatisticsSummary(totalEvents = 100, keyboardEvents = 70, accessibilityEvents = 30),
                appUsage = listOf(
                    AppUsageStat(
                        packageName = "com.example.notes",
                        applicationName = "Notes App",
                        count = 80,
                        percentage = 80.0f,
                        lastActiveTimestamp = 1000L
                    )
                ),
                hourlyActivity = (0..23).map { HourlyActivityStat(it, count = if (it == 14) 50L else 0L, normalizedRatio = if (it == 14) 1.0f else 0f) }
            )
        )

        val sessionStatisticsMap = mutableMapOf<String, InteractionStatistics>()

        override fun observeStatistics(sessionId: String?): Flow<InteractionStatistics> {
            return if (sessionId == null) {
                statisticsFlow.asStateFlow()
            } else {
                flowOf(sessionStatisticsMap[sessionId] ?: InteractionStatistics())
            }
        }

        override suspend fun getStatistics(sessionId: String?): InteractionStatistics {
            return if (sessionId == null) statisticsFlow.value else sessionStatisticsMap[sessionId] ?: InteractionStatistics()
        }

        override suspend fun insertEvent(event: InteractionEvent): Long = 1L
        override suspend fun insertEvents(events: List<InteractionEvent>): List<Long> = emptyList()
        override suspend fun getEventById(id: Long): InteractionEvent? = null
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEvent> = emptyList()
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent> = emptyList()
        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEvent> = emptyList()
        override suspend fun getEventCountForSession(sessionId: String): Int = 0
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(0)
        override fun observeEventCounts(sessionId: String?): Flow<com.example.inputmonitor.domain.model.EventCounts> =
            flowOf(com.example.inputmonitor.domain.model.EventCounts())
        override fun observeLatestEvent(): Flow<InteractionEvent?> = flowOf(null)
        override fun observeEventsPaged(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override fun observeFilteredCount(filter: com.example.inputmonitor.domain.model.EventFilterCriteria): Flow<Int> = flowOf(0)
        override fun observeDistinctPackageNames(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun deleteEventsForSession(sessionId: String): Boolean = true
        override suspend fun deleteAllEvents(): Boolean = true
    }

    private class FakeSessionRepository : SessionRepository {
        val sessionsFlow = MutableStateFlow<List<MonitoringSession>>(emptyList())

        override fun observeAllSessions(): Flow<List<MonitoringSession>> = sessionsFlow.asStateFlow()
        override suspend fun createSession(session: MonitoringSession) {
            sessionsFlow.value = sessionsFlow.value + session
        }
        override suspend fun updateSession(session: MonitoringSession) {}
        override fun observeSessionById(id: String): Flow<MonitoringSession?> =
            flowOf(sessionsFlow.value.find { it.id == id })
        override fun observeActiveSession(): Flow<MonitoringSession?> =
            flowOf(sessionsFlow.value.find { it.state == SessionState.ACTIVE })
        override suspend fun finishSession(id: String, endTime: Long): Boolean = true
        override suspend fun getSessionById(sessionId: String): MonitoringSession? = sessionsFlow.value.find { it.id == sessionId }
        override suspend fun getActiveSession(): MonitoringSession? = sessionsFlow.value.find { it.state == SessionState.ACTIVE }
        override suspend fun getAllSessions(): List<MonitoringSession> = sessionsFlow.value
        override suspend fun deleteSession(sessionId: String): Boolean = true
        override suspend fun deleteAllSessions(): Boolean = true
    }

    private lateinit var eventRepository: FakeEventRepository
    private lateinit var sessionRepository: FakeSessionRepository
    private lateinit var viewModel: StatisticsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        eventRepository = FakeEventRepository()
        sessionRepository = FakeSessionRepository()
        viewModel = StatisticsViewModel(eventRepository, sessionRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsGlobalStatisticsAndSessions() = runTest(testDispatcher) {
        val session1 = MonitoringSession(id = "s-100", name = "Test Session 1", state = SessionState.STOPPED, startTime = 1000L)
        sessionRepository.sessionsFlow.value = listOf(session1)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.selectedSessionId)
        assertEquals("All Sessions", state.selectedSessionName)
        assertEquals(1, state.availableSessions.size)
        assertEquals(100L, state.statistics.summary.totalEvents)
        assertEquals(70L, state.statistics.summary.keyboardEvents)
        assertEquals(1, state.statistics.appUsage.size)
        assertEquals("Notes App", state.statistics.appUsage[0].applicationName)
        assertTrue(state.hasData)
    }

    @Test
    fun selectSession_updatesFilterAndStatistics() = runTest(testDispatcher) {
        val session1 = MonitoringSession(id = "s-100", name = "Test Session 1", state = SessionState.ACTIVE, startTime = 1000L)
        sessionRepository.sessionsFlow.value = listOf(session1)

        eventRepository.sessionStatisticsMap["s-100"] = InteractionStatistics(
            summary = StatisticsSummary(totalEvents = 25, keyboardEvents = 15, accessibilityEvents = 10),
            appUsage = listOf(
                AppUsageStat(packageName = "com.test", applicationName = "Test App", count = 25, percentage = 100f, lastActiveTimestamp = 2000L)
            )
        )

        advanceUntilIdle()

        viewModel.selectSession("s-100")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("s-100", state.selectedSessionId)
        assertEquals("Test Session 1", state.selectedSessionName)
        assertEquals(25L, state.statistics.summary.totalEvents)
        assertEquals("Test App", state.statistics.appUsage[0].applicationName)
        assertFalse(state.isSessionDropdownExpanded)
    }

    @Test
    fun selectSession_revertToGlobal() = runTest(testDispatcher) {
        viewModel.selectSession("s-100")
        advanceUntilIdle()

        viewModel.selectSession(null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedSessionId)
        assertEquals("All Sessions", state.selectedSessionName)
        assertEquals(100L, state.statistics.summary.totalEvents)
    }

    @Test
    fun dropdownState_toggle() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.isSessionDropdownExpanded)

        viewModel.setDropdownExpanded(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSessionDropdownExpanded)

        viewModel.setDropdownExpanded(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSessionDropdownExpanded)
    }

    @Test
    fun statisticsUpdates_reactivelyReflected() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertEquals(100L, viewModel.uiState.value.statistics.summary.totalEvents)

        eventRepository.statisticsFlow.value = InteractionStatistics(
            summary = StatisticsSummary(totalEvents = 350, keyboardEvents = 200, sensitiveSuppressions = 5)
        )
        advanceUntilIdle()

        val updated = viewModel.uiState.value
        assertEquals(350L, updated.statistics.summary.totalEvents)
        assertEquals(5L, updated.statistics.summary.sensitiveSuppressions)
    }

    @Test
    fun factory_createsValidViewModel() {
        val factory = StatisticsViewModel.provideFactory(eventRepository, sessionRepository)
        val vm = factory.create(StatisticsViewModel::class.java)
        assertNotNull(vm)
    }
}
