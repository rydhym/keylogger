package com.example.inputmonitor.domain.session

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.session.SessionManagerImpl
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeSessionRepository : SessionRepository {
        val sessions = mutableMapOf<String, MonitoringSession>()

        override suspend fun createSession(session: MonitoringSession) {
            sessions[session.id] = session
        }

        override suspend fun updateSession(session: MonitoringSession) {
            sessions[session.id] = session
        }

        override suspend fun getSessionById(id: String): MonitoringSession? = sessions[id]

        override fun observeSessionById(id: String): Flow<MonitoringSession?> = flowOf(sessions[id])

        override suspend fun getActiveSession(): MonitoringSession? =
            sessions.values.firstOrNull { it.state == SessionState.ACTIVE }

        override fun observeActiveSession(): Flow<MonitoringSession?> =
            flowOf(sessions.values.firstOrNull { it.state == SessionState.ACTIVE })

        override fun observeAllSessions(): Flow<List<MonitoringSession>> =
            flowOf(sessions.values.toList().sortedByDescending { it.startTime })

        override suspend fun getAllSessions(): List<MonitoringSession> =
            sessions.values.toList().sortedByDescending { it.startTime }

        override suspend fun finishSession(id: String, endTime: Long): Boolean {
            val existing = sessions[id] ?: return false
            sessions[id] = existing.copy(endTime = endTime, state = SessionState.STOPPED)
            return true
        }

        override suspend fun deleteSession(id: String): Boolean {
            return sessions.remove(id) != null
        }

        override suspend fun deleteAllSessions(): Boolean {
            sessions.clear()
            return true
        }
    }

    private class FakePreferencesRepository : PreferencesRepository {
        private val _onboarding = MutableStateFlow(false)
        override val isOnboardingCompleted: Flow<Boolean> = _onboarding

        private val _activeSessionId = MutableStateFlow<String?>(null)
        override val activeSessionId: Flow<String?> = _activeSessionId

        private val _keyboardLogging = MutableStateFlow(true)
        override val isKeyboardLoggingEnabled: Flow<Boolean> = _keyboardLogging

        private val _accessibilityLogging = MutableStateFlow(true)
        override val isAccessibilityLoggingEnabled: Flow<Boolean> = _accessibilityLogging

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            _onboarding.value = completed
        }

        override suspend fun setActiveSessionId(sessionId: String?) {
            _activeSessionId.value = sessionId
        }

        override suspend fun setKeyboardLoggingEnabled(enabled: Boolean) {
            _keyboardLogging.value = enabled
        }

        override suspend fun setAccessibilityLoggingEnabled(enabled: Boolean) {
            _accessibilityLogging.value = enabled
        }

        override suspend fun clearAllPreferences() {
            _onboarding.value = false
            _activeSessionId.value = null
            _keyboardLogging.value = true
            _accessibilityLogging.value = true
        }
    }

    private fun createSessionManager(
        sessionRepository: SessionRepository = FakeSessionRepository(),
        preferencesRepository: PreferencesRepository = FakePreferencesRepository()
    ): SessionManagerImpl {
        return SessionManagerImpl(
            sessionRepository = sessionRepository,
            preferencesRepository = preferencesRepository,
            dispatchers = testDispatchers,
            androidVersion = "15",
            deviceModel = "Test Pixel 9",
            appVersion = "0.1.0"
        )
    }

    @Test
    fun initialState_isInactiveWhenNoSavedSession() = runBlocking {
        val manager = createSessionManager()
        assertEquals(SessionState.INACTIVE, manager.sessionState.value)
        assertNull(manager.currentSession.value)
    }

    @Test
    fun startSession_transitionsToActiveAndPersists() = runBlocking {
        val sessionRepo = FakeSessionRepository()
        val prefRepo = FakePreferencesRepository()
        val manager = createSessionManager(sessionRepo, prefRepo)

        val session = manager.startSession("Test Run 1", "Initial test")

        assertEquals(SessionState.ACTIVE, manager.sessionState.value)
        assertEquals(session.id, manager.currentSession.value?.id)
        assertEquals("Test Run 1", manager.currentSession.value?.name)
        assertEquals("Initial test", manager.currentSession.value?.notes)

        // Verify repository persistence
        val persisted = sessionRepo.getSessionById(session.id)
        assertNotNull(persisted)
        assertEquals(SessionState.ACTIVE, persisted?.state)

        // Verify preferences storage
        assertEquals(session.id, manager.currentSession.value?.id)
    }

    @Test
    fun pauseSession_fromActive_transitionsToPaused() = runBlocking {
        val sessionRepo = FakeSessionRepository()
        val manager = createSessionManager(sessionRepo)

        val session = manager.startSession("Test Run 2")
        val paused = manager.pauseSession()

        assertTrue(paused)
        assertEquals(SessionState.PAUSED, manager.sessionState.value)
        assertEquals(SessionState.PAUSED, manager.currentSession.value?.state)

        val persisted = sessionRepo.getSessionById(session.id)
        assertEquals(SessionState.PAUSED, persisted?.state)
    }

    @Test
    fun pauseSession_fromInactive_fails() = runBlocking {
        val manager = createSessionManager()
        val paused = manager.pauseSession()

        assertFalse(paused)
        assertEquals(SessionState.INACTIVE, manager.sessionState.value)
    }

    @Test
    fun resumeSession_fromPaused_transitionsToActive() = runBlocking {
        val sessionRepo = FakeSessionRepository()
        val manager = createSessionManager(sessionRepo)

        val session = manager.startSession("Test Run 3")
        manager.pauseSession()
        val resumed = manager.resumeSession()

        assertTrue(resumed)
        assertEquals(SessionState.ACTIVE, manager.sessionState.value)
        assertEquals(SessionState.ACTIVE, manager.currentSession.value?.state)

        val persisted = sessionRepo.getSessionById(session.id)
        assertEquals(SessionState.ACTIVE, persisted?.state)
    }

    @Test
    fun resumeSession_fromActive_fails() = runBlocking {
        val manager = createSessionManager()
        manager.startSession("Test Run 4")

        val resumed = manager.resumeSession()
        assertFalse(resumed)
        assertEquals(SessionState.ACTIVE, manager.sessionState.value)
    }

    @Test
    fun stopSession_transitionsToStoppedAndSetsEndTime() = runBlocking {
        val sessionRepo = FakeSessionRepository()
        val prefRepo = FakePreferencesRepository()
        val manager = createSessionManager(sessionRepo, prefRepo)

        val session = manager.startSession("Test Run 5")
        val stopped = manager.stopSession()

        assertTrue(stopped)
        assertEquals(SessionState.STOPPED, manager.sessionState.value)
        assertNotNull(manager.currentSession.value?.endTime)
        assertEquals(SessionState.STOPPED, manager.currentSession.value?.state)

        val persisted = sessionRepo.getSessionById(session.id)
        assertEquals(SessionState.STOPPED, persisted?.state)
        assertNotNull(persisted?.endTime)
    }

    @Test
    fun startSession_whenAlreadyActive_finishesExistingSession() = runBlocking {
        val sessionRepo = FakeSessionRepository()
        val manager = createSessionManager(sessionRepo)

        val firstSession = manager.startSession("Session 1")
        val secondSession = manager.startSession("Session 2")

        val oldPersisted = sessionRepo.getSessionById(firstSession.id)
        assertEquals(SessionState.STOPPED, oldPersisted?.state)
        assertNotNull(oldPersisted?.endTime)

        assertEquals(SessionState.ACTIVE, manager.sessionState.value)
        assertEquals(secondSession.id, manager.currentSession.value?.id)
    }

    @Test
    fun restoreSessionIfActive_restoresPersistedActiveSession() = runBlocking {
        val sessionRepo = FakeSessionRepository()
        val prefRepo = FakePreferencesRepository()

        val activeSession = MonitoringSession(
            id = "persisted-1",
            name = "Restored Session",
            startTime = 5000L,
            endTime = null,
            state = SessionState.ACTIVE,
            androidVersion = "15",
            deviceModel = "Pixel 9",
            appVersion = "0.1.0"
        )
        sessionRepo.createSession(activeSession)
        prefRepo.setActiveSessionId("persisted-1")

        val manager = createSessionManager(sessionRepo, prefRepo)
        val restored = manager.restoreSessionIfActive()

        assertNotNull(restored)
        assertEquals("persisted-1", restored?.id)
        assertEquals(SessionState.ACTIVE, manager.sessionState.value)
        assertEquals("Restored Session", manager.currentSession.value?.name)
    }

    @Test
    fun restart_restoresSameSessionWithoutChangingLoggingChoices() = runBlocking {
        val sessions = FakeSessionRepository()
        val preferences = FakePreferencesRepository()
        val original = createSessionManager(sessions, preferences).startSession("Keep this session")
        preferences.setKeyboardLoggingEnabled(false)
        preferences.setAccessibilityLoggingEnabled(true)

        val restarted = createSessionManager(sessions, preferences)
        val restored = restarted.restoreSessionIfActive()

        assertEquals(original, restored)
        assertEquals(SessionState.ACTIVE, restarted.sessionState.value)
        assertEquals(1, sessions.sessions.size)
        assertFalse(preferences.isKeyboardLoggingEnabled.first())
        assertTrue(preferences.isAccessibilityLoggingEnabled.first())
    }

    @Test
    fun restart_keepsPausedSessionPaused() = runBlocking {
        val sessions = FakeSessionRepository()
        val preferences = FakePreferencesRepository()
        val original = createSessionManager(sessions, preferences)
        val session = original.startSession("Paused by user")
        original.pauseSession()

        val restarted = createSessionManager(sessions, preferences)
        assertEquals(session.id, restarted.restoreSessionIfActive()?.id)
        assertEquals(SessionState.PAUSED, restarted.sessionState.value)
        assertEquals(SessionState.PAUSED, restarted.currentSession.value?.state)
    }

    @Test
    fun restart_doesNotResumeStoppedSession() = runBlocking {
        val sessions = FakeSessionRepository()
        val preferences = FakePreferencesRepository()
        val original = createSessionManager(sessions, preferences)
        original.startSession("Stopped by user")
        original.stopSession()

        val restarted = createSessionManager(sessions, preferences)
        assertNull(restarted.restoreSessionIfActive())
        assertEquals(SessionState.INACTIVE, restarted.sessionState.value)
        assertNull(preferences.activeSessionId.first())
    }

    @Test
    fun restart_clearsReferenceToDeletedSession() = runBlocking {
        val sessions = FakeSessionRepository()
        val preferences = FakePreferencesRepository()
        val session = createSessionManager(sessions, preferences).startSession("Deleted")
        sessions.deleteSession(session.id)

        val restarted = createSessionManager(sessions, preferences)
        assertNull(restarted.restoreSessionIfActive())
        assertEquals(SessionState.INACTIVE, restarted.sessionState.value)
        assertNull(preferences.activeSessionId.first())
    }

    @Test
    fun restart_doesNotReviveEndedRecordEvenIfMarkedActive() = runBlocking {
        val sessions = FakeSessionRepository()
        val preferences = FakePreferencesRepository()
        val session = createSessionManager(sessions, preferences).startSession("Already ended")
        sessions.updateSession(session.copy(endTime = session.startTime + 1000))

        val restarted = createSessionManager(sessions, preferences)
        assertNull(restarted.restoreSessionIfActive())
        assertEquals(SessionState.STOPPED, restarted.sessionState.value)
        assertEquals(SessionState.STOPPED, restarted.currentSession.value?.state)
        assertNull(preferences.activeSessionId.first())
    }

    @Test
    fun restart_doesNotRestoreInactiveRecord() = runBlocking {
        val sessions = FakeSessionRepository()
        val preferences = FakePreferencesRepository()
        val session = createSessionManager(sessions, preferences).startSession("Invalid state")
        sessions.updateSession(session.copy(state = SessionState.INACTIVE))

        val restarted = createSessionManager(sessions, preferences)
        assertNull(restarted.restoreSessionIfActive())
        assertEquals(SessionState.STOPPED, restarted.sessionState.value)
        assertNull(preferences.activeSessionId.first())
    }

    @Test
    fun restart_repeatedRestorationDoesNotDuplicateSession() = runBlocking {
        val sessions = FakeSessionRepository()
        val preferences = FakePreferencesRepository()
        val session = createSessionManager(sessions, preferences).startSession("One session")
        val restarted = createSessionManager(sessions, preferences)

        repeat(3) { assertEquals(session.id, restarted.restoreSessionIfActive()?.id) }
        assertEquals(1, sessions.sessions.size)
        assertNull(restarted.currentSession.value?.endTime)
    }
}
