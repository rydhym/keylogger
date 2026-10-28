package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionUseCasesTest {

    private class FakeSessionManager : SessionManager {
        private val _sessionState = MutableStateFlow(SessionState.INACTIVE)
        override val sessionState: StateFlow<SessionState> = _sessionState

        private val _currentSession = MutableStateFlow<MonitoringSession?>(null)
        override val currentSession: StateFlow<MonitoringSession?> = _currentSession

        override suspend fun startSession(name: String, notes: String?): MonitoringSession {
            val session = MonitoringSession(
                id = "use-case-sess",
                name = name,
                startTime = 1000L,
                endTime = null,
                state = SessionState.ACTIVE,
                androidVersion = "15",
                deviceModel = "Pixel",
                appVersion = "0.1.0",
                notes = notes
            )
            _currentSession.value = session
            _sessionState.value = SessionState.ACTIVE
            return session
        }

        override suspend fun pauseSession(): Boolean {
            _sessionState.value = SessionState.PAUSED
            return true
        }

        override suspend fun resumeSession(): Boolean {
            _sessionState.value = SessionState.ACTIVE
            return true
        }

        override suspend fun stopSession(): Boolean {
            _sessionState.value = SessionState.STOPPED
            return true
        }

        override suspend fun restoreSessionIfActive(): MonitoringSession? = _currentSession.value
    }

    @Test
    fun startUseCase_startsSessionCorrectly(): Unit = runBlocking {
        val manager = FakeSessionManager()
        val useCase = StartMonitoringSessionUseCase(manager)

        val session = useCase("Session Test", "Some notes")
        assertEquals("Session Test", session.name)
        assertEquals("Some notes", session.notes)
        assertEquals(SessionState.ACTIVE, manager.sessionState.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun startUseCase_rejectsBlankName(): Unit = runBlocking {
        val manager = FakeSessionManager()
        val useCase = StartMonitoringSessionUseCase(manager)
        useCase("   ")
        Unit
    }

    @Test
    fun pauseAndResumeUseCases_transitionState(): Unit = runBlocking {
        val manager = FakeSessionManager()
        val startUseCase = StartMonitoringSessionUseCase(manager)
        val pauseUseCase = PauseMonitoringSessionUseCase(manager)
        val resumeUseCase = ResumeMonitoringSessionUseCase(manager)

        startUseCase("Active Session")
        assertEquals(SessionState.ACTIVE, manager.sessionState.value)

        assertTrue(pauseUseCase())
        assertEquals(SessionState.PAUSED, manager.sessionState.value)

        assertTrue(resumeUseCase())
        assertEquals(SessionState.ACTIVE, manager.sessionState.value)
    }

    @Test
    fun stopUseCase_stopsSession(): Unit = runBlocking {
        val manager = FakeSessionManager()
        val startUseCase = StartMonitoringSessionUseCase(manager)
        val stopUseCase = StopMonitoringSessionUseCase(manager)

        startUseCase("Session to Stop")
        assertTrue(stopUseCase())
        assertEquals(SessionState.STOPPED, manager.sessionState.value)
    }

    @Test
    fun getAndObserveUseCases_provideSessionData(): Unit = runBlocking {
        val manager = FakeSessionManager()
        val startUseCase = StartMonitoringSessionUseCase(manager)
        val getActiveUseCase = GetActiveSessionUseCase(manager)
        val observeActiveUseCase = ObserveActiveSessionUseCase(manager)
        val observeStateUseCase = ObserveSessionStateUseCase(manager)

        startUseCase("Observed Session")

        val active = getActiveUseCase()
        assertNotNull(active)
        assertEquals("Observed Session", active?.name)

        assertEquals("Observed Session", observeActiveUseCase().value?.name)
        assertEquals(SessionState.ACTIVE, observeStateUseCase().value)
    }
}
