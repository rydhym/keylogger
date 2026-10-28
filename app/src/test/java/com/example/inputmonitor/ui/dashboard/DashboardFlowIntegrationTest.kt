package com.example.inputmonitor.ui.dashboard

import com.example.inputmonitor.domain.model.EventCounts
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.model.SetupState
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.session.SessionManager
import com.example.inputmonitor.domain.setup.SetupStateManager
import com.example.inputmonitor.domain.usecase.ObserveActiveSessionUseCase
import com.example.inputmonitor.domain.usecase.ObserveSessionStateUseCase
import com.example.inputmonitor.domain.usecase.PauseMonitoringSessionUseCase
import com.example.inputmonitor.domain.usecase.ResumeMonitoringSessionUseCase
import com.example.inputmonitor.domain.usecase.StartMonitoringSessionUseCase
import com.example.inputmonitor.domain.usecase.StopMonitoringSessionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 11 Dashboard Integration Test Suite (IMPLEMENTATION_PLAN.md §50, SRS.md §47, Architecture.md §55).
 *
 * Verifies end-to-end dashboard integration:
 * 1. Initial inactive system status and service warning handling (SRS.md §74)
 * 2. Service enablement updates (Keyboard & Accessibility status badges)
 * 3. Session lifecycle state machine controls (Start, Pause, Resume, Stop)
 * 4. Concurrent aggregate event counters updates (Keyboard, Clicks, Focus, Scroll, Window transitions)
 * 5. Dynamic foreground application context attribution
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardFlowIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    private class IntegrationSessionManager : SessionManager {
        val _sessionState = MutableStateFlow(SessionState.INACTIVE)
        override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        val _currentSession = MutableStateFlow<MonitoringSession?>(null)
        override val currentSession: StateFlow<MonitoringSession?> = _currentSession.asStateFlow()

        override suspend fun startSession(name: String, notes: String?): MonitoringSession {
            val session = MonitoringSession(
                id = "int-session-1",
                name = name,
                startTime = System.currentTimeMillis(),
                state = SessionState.ACTIVE,
                androidVersion = "15",
                deviceModel = "Pixel 9 Pro",
                appVersion = "0.1.0",
                notes = notes
            )
            _currentSession.value = session
            _sessionState.value = SessionState.ACTIVE
            return session
        }

        override suspend fun pauseSession(): Boolean {
            _sessionState.value = SessionState.PAUSED
            _currentSession.value = _currentSession.value?.copy(state = SessionState.PAUSED)
            return true
        }

        override suspend fun resumeSession(): Boolean {
            _sessionState.value = SessionState.ACTIVE
            _currentSession.value = _currentSession.value?.copy(state = SessionState.ACTIVE)
            return true
        }

        override suspend fun stopSession(): Boolean {
            _sessionState.value = SessionState.STOPPED
            _currentSession.value = _currentSession.value?.copy(
                state = SessionState.STOPPED,
                endTime = System.currentTimeMillis()
            )
            return true
        }

        override suspend fun restoreSessionIfActive(): MonitoringSession? = _currentSession.value
    }

    private class IntegrationSetupStateManager : SetupStateManager {
        val state = MutableStateFlow(
            SetupState(
                runtimePermissionsReady = true,
                accessibilityEnabled = false,
                imeInstalled = true,
                imeEnabled = false,
                imeSelected = false,
                databaseReady = true
            )
        )

        override fun observeSetupState(): StateFlow<SetupState> = state.asStateFlow()
        override suspend fun refresh() {}
        override fun getSetupState(): SetupState = state.value
    }

    private class IntegrationEventRepository : EventRepository {
        val events = mutableListOf<InteractionEvent>()
        val countsFlow = MutableStateFlow(EventCounts())
        val latestEventFlow = MutableStateFlow<InteractionEvent?>(null)

        override suspend fun insertEvent(event: InteractionEvent): Long {
            events.add(event)
            latestEventFlow.value = event
            recalculateCounts()
            return events.size.toLong()
        }

        private fun recalculateCounts() {
            countsFlow.value = EventCounts(
                total = events.size.toLong(),
                keyboard = events.count { it.source == EventSource.KEYBOARD }.toLong(),
                accessibility = events.count { it.source == EventSource.ACCESSIBILITY }.toLong(),
                clicks = events.count { it.eventType == EventType.VIEW_CLICKED }.toLong(),
                focus = events.count { it.eventType == EventType.VIEW_FOCUSED }.toLong(),
                scrolls = events.count { it.eventType == EventType.VIEW_SCROLLED }.toLong(),
                windowChanges = events.count { it.eventType == EventType.WINDOW_STATE_CHANGED || it.eventType == EventType.WINDOWS_CHANGED }.toLong()
            )
        }

        override suspend fun insertEvents(eventsList: List<InteractionEvent>): List<Long> {
            return eventsList.map { insertEvent(it) }
        }

        override suspend fun getEventById(id: Long): InteractionEvent? = events.firstOrNull { it.id == id }
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>> = MutableStateFlow(events)
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEvent> = events
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent> = events.drop(offset).take(limit)
        override suspend fun getEventsFiltered(sessionId: String, packageFilter: String?, eventTypeFilter: String?, startTime: Long?, endTime: Long?): List<InteractionEvent> = events
        override suspend fun getEventCountForSession(sessionId: String): Int = events.size
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = MutableStateFlow(events.size)
        override fun observeEventCounts(sessionId: String?): Flow<EventCounts> = countsFlow
        override fun observeLatestEvent(): Flow<InteractionEvent?> = latestEventFlow
        override fun observeEventsPaged(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> = MutableStateFlow(events.drop(offset).take(limit))
        override fun observeFilteredCount(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria
        ): Flow<Int> = MutableStateFlow(events.size)
        override fun observeDistinctPackageNames(): Flow<List<String>> = MutableStateFlow(events.mapNotNull { it.packageName }.distinct())
        override suspend fun deleteEventsForSession(sessionId: String): Boolean = true
        override suspend fun deleteAllEvents(): Boolean = true
    }

    private lateinit var sessionManager: IntegrationSessionManager
    private lateinit var setupStateManager: IntegrationSetupStateManager
    private lateinit var eventRepository: IntegrationEventRepository
    private lateinit var dashboardViewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionManager = IntegrationSessionManager()
        setupStateManager = IntegrationSetupStateManager()
        eventRepository = IntegrationEventRepository()

        dashboardViewModel = DashboardViewModel(
            startMonitoringSessionUseCase = StartMonitoringSessionUseCase(sessionManager),
            pauseMonitoringSessionUseCase = PauseMonitoringSessionUseCase(sessionManager),
            resumeMonitoringSessionUseCase = ResumeMonitoringSessionUseCase(sessionManager),
            stopMonitoringSessionUseCase = StopMonitoringSessionUseCase(sessionManager),
            observeActiveSessionUseCase = ObserveActiveSessionUseCase(sessionManager),
            observeSessionStateUseCase = ObserveSessionStateUseCase(sessionManager),
            setupStateManager = setupStateManager,
            eventRepository = eventRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun endToEndDashboardFlow_verification() {
        // Step 1: Verify Initial Dashboard State (Disabled services show warning per SRS.md §74)
        testDispatcher.scheduler.advanceUntilIdle()
        var state = dashboardViewModel.uiState.value

        assertEquals(SessionState.INACTIVE, state.sessionState)
        assertTrue("Incomplete services trigger warning banner", state.hasIncompleteServices)
        assertFalse(state.setupState.accessibilityEnabled)
        assertFalse(state.setupState.imeEnabled)
        assertEquals(0L, state.totalEvents)

        // Step 2: System services become enabled in Settings
        setupStateManager.state.value = SetupState(
            runtimePermissionsReady = true,
            accessibilityEnabled = true,
            imeInstalled = true,
            imeEnabled = true,
            imeSelected = true,
            databaseReady = true
        )
        testDispatcher.scheduler.advanceUntilIdle()
        state = dashboardViewModel.uiState.value

        assertFalse("All services complete removes warning", state.hasIncompleteServices)
        assertTrue(state.setupState.accessibilityEnabled)
        assertTrue(state.setupState.imeEnabled)
        assertTrue(state.setupState.imeSelected)

        // Step 3: Start Session
        dashboardViewModel.startSession("Field Usability Test", "Session testing input latency")
        testDispatcher.scheduler.advanceUntilIdle()
        state = dashboardViewModel.uiState.value

        assertEquals(SessionState.ACTIVE, state.sessionState)
        assertNotNull(state.activeSession)
        assertEquals("Field Usability Test", state.activeSession?.name)

        // Step 4: Simulate events arriving from Custom Keyboard and Accessibility Service
        kotlinx.coroutines.runBlocking {
            // Typing event
            eventRepository.insertEvent(
                InteractionEvent(
                    id = 1L,
                    sessionId = "int-session-1",
                    timestamp = 1000L,
                    source = EventSource.KEYBOARD,
                    eventType = EventType.TEXT_COMMITTED,
                    packageName = "com.example.notes",
                    applicationName = "Notes App",
                    eventData = "Search query",
                    inputClassification = InputClassification.NORMAL_TEXT,
                    metadata = null
                )
            )
            // Accessibility events
            eventRepository.insertEvent(
                InteractionEvent(
                    id = 2L,
                    sessionId = "int-session-1",
                    timestamp = 1100L,
                    source = EventSource.ACCESSIBILITY,
                    eventType = EventType.VIEW_CLICKED,
                    packageName = "com.example.notes",
                    applicationName = "Notes App",
                    eventData = null,
                    inputClassification = InputClassification.NORMAL_TEXT,
                    metadata = null
                )
            )
            eventRepository.insertEvent(
                InteractionEvent(
                    id = 3L,
                    sessionId = "int-session-1",
                    timestamp = 1200L,
                    source = EventSource.ACCESSIBILITY,
                    eventType = EventType.VIEW_SCROLLED,
                    packageName = "com.example.browser",
                    applicationName = "Browser",
                    eventData = null,
                    inputClassification = InputClassification.NORMAL_TEXT,
                    metadata = null
                )
            )
        }

        testDispatcher.scheduler.advanceUntilIdle()
        state = dashboardViewModel.uiState.value

        // Step 5: Verify Live Aggregate Event Counters and Foreground Application Context
        assertEquals(3L, state.totalEvents)
        assertEquals(1L, state.keyboardEvents)
        assertEquals(2L, state.accessibilityEvents)
        assertEquals(1L, state.clickEvents)
        assertEquals(1L, state.scrollEvents)
        assertEquals("Browser", state.currentApplicationLabel)
        assertEquals("com.example.browser", state.currentPackageName)

        // Step 6: Pause Session
        dashboardViewModel.pauseSession()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.PAUSED, dashboardViewModel.uiState.value.sessionState)

        // Step 7: Resume Session
        dashboardViewModel.resumeSession()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.ACTIVE, dashboardViewModel.uiState.value.sessionState)

        // Step 8: Stop Session
        dashboardViewModel.stopSession()
        testDispatcher.scheduler.advanceUntilIdle()
        state = dashboardViewModel.uiState.value

        assertEquals(SessionState.STOPPED, state.sessionState)
        assertNotNull("Session end time recorded", state.activeSession?.endTime)
        assertEquals(3L, state.totalEvents)
    }
}
