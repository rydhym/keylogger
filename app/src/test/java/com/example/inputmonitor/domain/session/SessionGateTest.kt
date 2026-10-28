package com.example.inputmonitor.domain.session

import com.example.inputmonitor.data.session.SessionGateImpl
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.domain.model.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGateTest {

    private class FakeSessionManager(
        initialState: SessionState = SessionState.INACTIVE,
        initialSession: MonitoringSession? = null
    ) : SessionManager {
        private val _sessionState = MutableStateFlow(initialState)
        override val sessionState: StateFlow<SessionState> = _sessionState

        private val _currentSession = MutableStateFlow(initialSession)
        override val currentSession: StateFlow<MonitoringSession?> = _currentSession

        fun setState(state: SessionState) {
            _sessionState.value = state
        }

        override suspend fun startSession(name: String, notes: String?): MonitoringSession {
            val session = MonitoringSession(
                id = "sess-gate",
                name = name,
                startTime = 1000L,
                endTime = null,
                state = SessionState.ACTIVE,
                androidVersion = "15",
                deviceModel = "Pixel",
                appVersion = "0.1.0"
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

    private val sampleRawEvent = RawInteractionEvent(
        timestamp = 1000L,
        source = EventSource.KEYBOARD,
        eventType = EventType.KEY_PRESS,
        packageName = "com.example.test",
        candidateData = "a",
        inputClassification = InputClassification.NORMAL_TEXT
    )

    @Test
    fun sessionGate_blocksEventsWhenInactive() {
        val fakeManager = FakeSessionManager(SessionState.INACTIVE)
        val gate = SessionGateImpl(fakeManager)

        assertFalse("Gate must reject events when session is INACTIVE", gate.canProcessEvents())
        assertFalse("Gate must reject raw events when session is INACTIVE", gate.shouldProcess(sampleRawEvent))
    }

    @Test
    fun sessionGate_allowsEventsWhenActive() {
        val fakeManager = FakeSessionManager(SessionState.ACTIVE)
        val gate = SessionGateImpl(fakeManager)

        assertTrue("Gate must permit events when session is ACTIVE", gate.canProcessEvents())
        assertTrue("Gate must permit raw events when session is ACTIVE", gate.shouldProcess(sampleRawEvent))
    }

    @Test
    fun sessionGate_blocksEventsWhenPaused() {
        val fakeManager = FakeSessionManager(SessionState.PAUSED)
        val gate = SessionGateImpl(fakeManager)

        assertFalse("Gate must reject events when session is PAUSED", gate.canProcessEvents())
        assertFalse("Gate must reject raw events when session is PAUSED", gate.shouldProcess(sampleRawEvent))
    }

    @Test
    fun sessionGate_blocksEventsWhenStopped() {
        val fakeManager = FakeSessionManager(SessionState.STOPPED)
        val gate = SessionGateImpl(fakeManager)

        assertFalse("Gate must reject events when session is STOPPED", gate.canProcessEvents())
        assertFalse("Gate must reject raw events when session is STOPPED", gate.shouldProcess(sampleRawEvent))
    }
}
