package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.InteractionStatistics
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.session.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeleteAllApplicationDataUseCase] (SRS.md §55, IMPLEMENTATION_PLAN.md §55).
 */
class DeleteAllApplicationDataUseCaseTest {

    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private class FakeSessionRepository : SessionRepository {
        var deleteAllSessionsCalled = false

        override suspend fun createSession(session: MonitoringSession) {}
        override suspend fun updateSession(session: MonitoringSession) {}
        override suspend fun getSessionById(id: String): MonitoringSession? = null
        override fun observeSessionById(id: String): Flow<MonitoringSession?> = flowOf(null)
        override suspend fun getActiveSession(): MonitoringSession? = null
        override fun observeActiveSession(): Flow<MonitoringSession?> = flowOf(null)
        override fun observeAllSessions(): Flow<List<MonitoringSession>> = flowOf(emptyList())
        override suspend fun getAllSessions(): List<MonitoringSession> = emptyList()
        override suspend fun finishSession(id: String, endTime: Long): Boolean = true
        override suspend fun deleteSession(id: String): Boolean = true

        override suspend fun deleteAllSessions(): Boolean {
            deleteAllSessionsCalled = true
            return true
        }
    }

    private class FakeEventRepository : EventRepository {
        var deleteAllEventsCalled = false

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
            flowOf(com.example.inputmonitor.domain.model.EventCounts(0L))
        override fun observeLatestEvent(): Flow<InteractionEvent?> = flowOf(null)
        override fun observeEventsPaged(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override fun observeFilteredCount(filter: com.example.inputmonitor.domain.model.EventFilterCriteria): Flow<Int> = flowOf(0)
        override fun observeDistinctPackageNames(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun deleteEventsForSession(sessionId: String): Boolean = true

        override suspend fun deleteAllEvents(): Boolean {
            deleteAllEventsCalled = true
            return true
        }

        override fun observeStatistics(sessionId: String?): Flow<InteractionStatistics> =
            flowOf(InteractionStatistics())
        override suspend fun getStatistics(sessionId: String?): InteractionStatistics =
            InteractionStatistics()
    }

    private class FakePreferencesRepository : PreferencesRepository {
        var clearAllPreferencesCalled = false

        override val isOnboardingCompleted: Flow<Boolean> = flowOf(true)
        override suspend fun setOnboardingCompleted(completed: Boolean) {}

        override val activeSessionId: Flow<String?> = flowOf("session-1")
        override suspend fun setActiveSessionId(sessionId: String?) {}

        override val isKeyboardLoggingEnabled: Flow<Boolean> = flowOf(true)
        override suspend fun setKeyboardLoggingEnabled(enabled: Boolean) {}

        override val isAccessibilityLoggingEnabled: Flow<Boolean> = flowOf(true)
        override suspend fun setAccessibilityLoggingEnabled(enabled: Boolean) {}

        override suspend fun clearAllPreferences() {
            clearAllPreferencesCalled = true
        }
    }

    private class FakeSessionManager : SessionManager {
        private val _sessionState = MutableStateFlow(SessionState.ACTIVE)
        override val sessionState: StateFlow<SessionState> = _sessionState

        private val _currentSession = MutableStateFlow<MonitoringSession?>(null)
        override val currentSession: StateFlow<MonitoringSession?> = _currentSession

        var resetCalled = false

        override suspend fun startSession(name: String, notes: String?): MonitoringSession =
            throw UnsupportedOperationException()
        override suspend fun pauseSession(): Boolean = true
        override suspend fun resumeSession(): Boolean = true
        override suspend fun stopSession(): Boolean = true
        override suspend fun restoreSessionIfActive(): MonitoringSession? = null

        override suspend fun resetSessionState() {
            resetCalled = true
            _currentSession.value = null
            _sessionState.value = SessionState.INACTIVE
        }
    }

    private lateinit var sessionRepository: FakeSessionRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var preferencesRepository: FakePreferencesRepository
    private lateinit var sessionManager: FakeSessionManager
    private lateinit var deleteAllApplicationDataUseCase: DeleteAllApplicationDataUseCase

    @Before
    fun setUp() {
        sessionRepository = FakeSessionRepository()
        eventRepository = FakeEventRepository()
        preferencesRepository = FakePreferencesRepository()
        sessionManager = FakeSessionManager()
        deleteAllApplicationDataUseCase = DeleteAllApplicationDataUseCase(
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            preferencesRepository = preferencesRepository,
            sessionManager = sessionManager,
            dispatchers = testDispatchers
        )
    }

    @Test
    fun deleteAllApplicationData_clearsEventsSessionsPreferencesAndSessionManager() = runBlocking {
        val result = deleteAllApplicationDataUseCase()
        assertTrue(result.isSuccess)
        assertTrue(eventRepository.deleteAllEventsCalled)
        assertTrue(sessionRepository.deleteAllSessionsCalled)
        assertTrue(sessionManager.resetCalled)
        assertTrue(preferencesRepository.clearAllPreferencesCalled)
    }
}
