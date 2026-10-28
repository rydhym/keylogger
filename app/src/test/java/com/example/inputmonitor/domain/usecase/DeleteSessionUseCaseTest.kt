package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.InteractionStatistics
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.session.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeleteSessionUseCase] (SRS.md §55, Architecture.md §10).
 */
class DeleteSessionUseCaseTest {

    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private class FakeSessionRepository : SessionRepository {
        val sessions = mutableListOf<MonitoringSession>()
        var shouldThrow = false

        override suspend fun createSession(session: MonitoringSession) {
            sessions.add(session)
        }

        override suspend fun updateSession(session: MonitoringSession) {
            val idx = sessions.indexOfFirst { it.id == session.id }
            if (idx >= 0) sessions[idx] = session
        }

        override suspend fun getSessionById(id: String): MonitoringSession? = sessions.find { it.id == id }
        override fun observeSessionById(id: String): Flow<MonitoringSession?> = flowOf(sessions.find { it.id == id })
        override suspend fun getActiveSession(): MonitoringSession? = sessions.find { it.state == SessionState.ACTIVE }
        override fun observeActiveSession(): Flow<MonitoringSession?> = flowOf(sessions.find { it.state == SessionState.ACTIVE })
        override fun observeAllSessions(): Flow<List<MonitoringSession>> = flowOf(sessions.toList())
        override suspend fun getAllSessions(): List<MonitoringSession> = sessions.toList()
        override suspend fun finishSession(id: String, endTime: Long): Boolean = true

        override suspend fun deleteSession(id: String): Boolean {
            if (shouldThrow) throw IllegalStateException("Database write error")
            return sessions.removeIf { it.id == id }
        }

        override suspend fun deleteAllSessions(): Boolean {
            sessions.clear()
            return true
        }
    }

    private class FakeEventRepository : EventRepository {
        val deletedSessions = mutableListOf<String>()
        var deleteAllCalled = false

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

        override suspend fun deleteEventsForSession(sessionId: String): Boolean {
            deletedSessions.add(sessionId)
            return true
        }

        override suspend fun deleteAllEvents(): Boolean {
            deleteAllCalled = true
            return true
        }

        override fun observeStatistics(sessionId: String?): Flow<InteractionStatistics> =
            flowOf(InteractionStatistics())
        override suspend fun getStatistics(sessionId: String?): InteractionStatistics =
            InteractionStatistics()
    }

    private class FakeSessionManager : SessionManager {
        private val _sessionState = MutableStateFlow(SessionState.ACTIVE)
        override val sessionState: StateFlow<SessionState> = _sessionState

        private val _currentSession = MutableStateFlow<MonitoringSession?>(null)
        override val currentSession: StateFlow<MonitoringSession?> = _currentSession

        var deletedSessionIdPassed: String? = null
        var resetCalled = false

        override suspend fun startSession(name: String, notes: String?): MonitoringSession {
            val session = MonitoringSession(
                id = "active-sess-1",
                name = name,
                startTime = 1000L,
                endTime = null,
                state = SessionState.ACTIVE,
                androidVersion = "15",
                deviceModel = "Pixel",
                appVersion = "1.0",
                notes = notes
            )
            _currentSession.value = session
            _sessionState.value = SessionState.ACTIVE
            return session
        }

        override suspend fun pauseSession(): Boolean = true
        override suspend fun resumeSession(): Boolean = true
        override suspend fun stopSession(): Boolean = true
        override suspend fun restoreSessionIfActive(): MonitoringSession? = _currentSession.value

        override suspend fun onSessionDeleted(sessionId: String) {
            deletedSessionIdPassed = sessionId
            if (_currentSession.value?.id == sessionId) {
                _currentSession.value = null
                _sessionState.value = SessionState.INACTIVE
            }
        }

        override suspend fun resetSessionState() {
            resetCalled = true
            _currentSession.value = null
            _sessionState.value = SessionState.INACTIVE
        }
    }

    private lateinit var sessionRepository: FakeSessionRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var sessionManager: FakeSessionManager
    private lateinit var deleteSessionUseCase: DeleteSessionUseCase

    @Before
    fun setUp() {
        sessionRepository = FakeSessionRepository()
        eventRepository = FakeEventRepository()
        sessionManager = FakeSessionManager()
        deleteSessionUseCase = DeleteSessionUseCase(
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            sessionManager = sessionManager,
            dispatchers = testDispatchers
        )
    }

    @Test
    fun deleteSession_successfullyCleansUpEventsAndSessionAndNotifiesManager() = runBlocking {
        val session = MonitoringSession(
            id = "sess-123",
            name = "Test Session",
            startTime = 1000L,
            endTime = 2000L,
            state = SessionState.STOPPED,
            androidVersion = "15",
            deviceModel = "Pixel",
            appVersion = "1.0",
            notes = null
        )
        sessionRepository.createSession(session)
        assertEquals(1, sessionRepository.sessions.size)

        val result = deleteSessionUseCase("sess-123")
        assertTrue(result.isSuccess)
        assertEquals(0, sessionRepository.sessions.size)
        assertTrue(eventRepository.deletedSessions.contains("sess-123"))
        assertEquals("sess-123", sessionManager.deletedSessionIdPassed)
    }

    @Test
    fun deleteSession_whenActive_resetsSessionManagerActiveState() = runBlocking {
        val activeSession = sessionManager.startSession("Active Session")
        sessionRepository.createSession(activeSession)

        assertEquals(SessionState.ACTIVE, sessionManager.sessionState.value)
        assertEquals("active-sess-1", sessionManager.currentSession.value?.id)

        val result = deleteSessionUseCase("active-sess-1")
        assertTrue(result.isSuccess)
        assertEquals(SessionState.INACTIVE, sessionManager.sessionState.value)
        assertEquals(null, sessionManager.currentSession.value)
    }

    @Test
    fun deleteSession_withBlankId_failsWithIllegalArgument() = runBlocking {
        val result = deleteSessionUseCase("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun deleteSession_whenRepositoryFails_returnsFailure() = runBlocking {
        sessionRepository.shouldThrow = true
        val result = deleteSessionUseCase("any-id")
        assertTrue(result.isFailure)
    }
}
