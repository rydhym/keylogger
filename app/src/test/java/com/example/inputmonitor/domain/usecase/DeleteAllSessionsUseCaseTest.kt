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
 * Unit tests for [DeleteAllSessionsUseCase] (SRS.md §55).
 */
class DeleteAllSessionsUseCaseTest {

    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private class FakeSessionRepository : SessionRepository {
        val sessions = mutableListOf<MonitoringSession>()
        var deleteAllSessionsCalled = false
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
        override suspend fun deleteSession(id: String): Boolean = true

        override suspend fun deleteAllSessions(): Boolean {
            if (shouldThrow) throw RuntimeException("IO Failure")
            deleteAllSessionsCalled = true
            sessions.clear()
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
    private lateinit var sessionManager: FakeSessionManager
    private lateinit var deleteAllSessionsUseCase: DeleteAllSessionsUseCase

    @Before
    fun setUp() {
        sessionRepository = FakeSessionRepository()
        eventRepository = FakeEventRepository()
        sessionManager = FakeSessionManager()
        deleteAllSessionsUseCase = DeleteAllSessionsUseCase(
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            sessionManager = sessionManager,
            dispatchers = testDispatchers
        )
    }

    @Test
    fun deleteAllSessions_deletesAllEventsAndSessionsAndResetsSessionState() = runBlocking {
        sessionRepository.createSession(
            MonitoringSession(
                id = "s1",
                name = "Sess 1",
                startTime = 100L,
                endTime = 200L,
                state = SessionState.STOPPED,
                androidVersion = "15",
                deviceModel = "Pixel",
                appVersion = "1.0",
                notes = null
            )
        )

        val result = deleteAllSessionsUseCase()
        assertTrue(result.isSuccess)
        assertTrue(eventRepository.deleteAllEventsCalled)
        assertTrue(sessionRepository.deleteAllSessionsCalled)
        assertEquals(0, sessionRepository.sessions.size)
        assertTrue(sessionManager.resetCalled)
        assertEquals(SessionState.INACTIVE, sessionManager.sessionState.value)
    }

    @Test
    fun deleteAllSessions_whenRepositoryThrows_returnsFailure() = runBlocking {
        sessionRepository.shouldThrow = true
        val result = deleteAllSessionsUseCase()
        assertTrue(result.isFailure)
    }
}
