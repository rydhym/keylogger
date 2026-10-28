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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DashboardViewModel] verifying reactive UI state combination,
 * session controls, aggregate metrics, and setup refresh (Architecture.md §55, SRS.md §47).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeSessionManager : SessionManager {
        val _sessionState = MutableStateFlow(SessionState.INACTIVE)
        override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        val _currentSession = MutableStateFlow<MonitoringSession?>(null)
        override val currentSession: StateFlow<MonitoringSession?> = _currentSession.asStateFlow()

        override suspend fun startSession(name: String, notes: String?): MonitoringSession {
            val session = MonitoringSession(
                id = "sess-123",
                name = name,
                startTime = System.currentTimeMillis(),
                state = SessionState.ACTIVE,
                androidVersion = "15",
                deviceModel = "Pixel 9",
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

    private class FakeSetupStateManager : SetupStateManager {
        val state = MutableStateFlow(SetupState())
        var refreshCount = 0

        override fun observeSetupState(): StateFlow<SetupState> = state.asStateFlow()
        override suspend fun refresh() {
            refreshCount++
        }
        override fun getSetupState(): SetupState = state.value
    }

    private class FakeEventRepository : EventRepository {
        val countsFlow = MutableStateFlow(EventCounts())
        val latestEventFlow = MutableStateFlow<InteractionEvent?>(null)

        override suspend fun insertEvent(event: InteractionEvent): Long = 1L
        override suspend fun insertEvents(events: List<InteractionEvent>): List<Long> = emptyList()
        override suspend fun getEventById(id: Long): InteractionEvent? = null
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>> = MutableStateFlow(emptyList())
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEvent> = emptyList()
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent> = emptyList()
        override suspend fun getEventsFiltered(sessionId: String, packageFilter: String?, eventTypeFilter: String?, startTime: Long?, endTime: Long?): List<InteractionEvent> = emptyList()
        override suspend fun getEventCountForSession(sessionId: String): Int = 0
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = MutableStateFlow(0)
        override fun observeEventCounts(sessionId: String?): Flow<EventCounts> = countsFlow
        override fun observeLatestEvent(): Flow<InteractionEvent?> = latestEventFlow
        override fun observeEventsPaged(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> = MutableStateFlow(emptyList())
        override fun observeFilteredCount(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria
        ): Flow<Int> = MutableStateFlow(0)
        override fun observeDistinctPackageNames(): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun deleteEventsForSession(sessionId: String): Boolean = true
        override suspend fun deleteAllEvents(): Boolean = true
    }

    private lateinit var sessionManager: FakeSessionManager
    private lateinit var setupStateManager: FakeSetupStateManager
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionManager = FakeSessionManager()
        setupStateManager = FakeSetupStateManager()
        eventRepository = FakeEventRepository()

        viewModel = DashboardViewModel(
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
    fun initialState_reflectsDefaults() {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(SessionState.INACTIVE, state.sessionState)
        assertNull(state.activeSession)
        assertEquals(0L, state.totalEvents)
        assertNull(state.currentApplicationLabel)
        assertNull(state.currentPackageName)
    }

    @Test
    fun startSession_updatesSessionStateAndActiveSession() {
        viewModel.startSession("Morning Audit", "Testing input pipeline")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SessionState.ACTIVE, state.sessionState)
        assertNotNull(state.activeSession)
        assertEquals("Morning Audit", state.activeSession?.name)
        assertEquals("Testing input pipeline", state.activeSession?.notes)
    }

    @Test
    fun pauseAndResumeSession_transitionsStateCorrectly() {
        viewModel.startSession("Audit Session")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.ACTIVE, viewModel.uiState.value.sessionState)

        viewModel.pauseSession()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.PAUSED, viewModel.uiState.value.sessionState)

        viewModel.resumeSession()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionState.ACTIVE, viewModel.uiState.value.sessionState)
    }

    @Test
    fun stopSession_transitionsStateToStopped() {
        viewModel.startSession("Audit Session")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.stopSession()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SessionState.STOPPED, state.sessionState)
        assertNotNull("Ended session metadata preserved", state.activeSession?.endTime)
    }

    @Test
    fun eventCountsAndApplicationContext_updateUiStateLive() {
        testDispatcher.scheduler.advanceUntilIdle()

        eventRepository.countsFlow.value = EventCounts(
            total = 100L,
            keyboard = 50L,
            accessibility = 50L,
            clicks = 20L,
            focus = 15L,
            scrolls = 10L,
            windowChanges = 5L
        )

        eventRepository.latestEventFlow.value = InteractionEvent(
            id = 1L,
            sessionId = "sess-123",
            timestamp = 2000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.notes",
            applicationName = "Notes Application",
            eventData = "Hello",
            inputClassification = InputClassification.NORMAL_TEXT,
            metadata = null
        )

        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(100L, state.totalEvents)
        assertEquals(50L, state.keyboardEvents)
        assertEquals(20L, state.clickEvents)
        assertEquals("Notes Application", state.currentApplicationLabel)
        assertEquals("com.example.notes", state.currentPackageName)
    }

    @Test
    fun refreshSetup_invokesSetupStateManagerRefresh() {
        viewModel.refreshSetup()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, setupStateManager.refreshCount)
    }
}
