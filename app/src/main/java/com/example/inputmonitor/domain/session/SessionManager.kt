package com.example.inputmonitor.domain.session

import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Authority for recording state and monitoring session lifecycle (Architecture.md §43).
 *
 * State machine transitions:
 * - INACTIVE -> startSession() -> ACTIVE
 * - ACTIVE -> pauseSession() -> PAUSED
 * - PAUSED -> resumeSession() -> ACTIVE
 * - ACTIVE or PAUSED -> stopSession() -> STOPPED
 * - STOPPED -> startSession() -> ACTIVE (creates a new session)
 */
interface SessionManager {

    /**
     * Currently active or most recent session, or null if no session has been started.
     */
    val currentSession: StateFlow<MonitoringSession?>

    /**
     * Current session lifecycle state.
     */
    val sessionState: StateFlow<SessionState>

    /**
     * Starts a new monitoring session.
     * Transitions state machine to [SessionState.ACTIVE].
     *
     * @param name Descriptive name for the session.
     * @param notes Optional notes or context for the session.
     * @return The newly created [MonitoringSession].
     */
    suspend fun startSession(name: String, notes: String? = null): MonitoringSession

    /**
     * Pauses the active monitoring session.
     * Transitions state from [SessionState.ACTIVE] to [SessionState.PAUSED].
     *
     * @return true if successfully transitioned to PAUSED, false if not currently ACTIVE.
     */
    suspend fun pauseSession(): Boolean

    /**
     * Resumes a paused monitoring session.
     * Transitions state from [SessionState.PAUSED] to [SessionState.ACTIVE].
     *
     * @return true if successfully transitioned to ACTIVE, false if not currently PAUSED.
     */
    suspend fun resumeSession(): Boolean

    /**
     * Stops the monitoring session, recording its end time.
     * Transitions state from [SessionState.ACTIVE] or [SessionState.PAUSED] to [SessionState.STOPPED].
     *
     * @return true if successfully transitioned to STOPPED, false if no session was running.
     */
    suspend fun stopSession(): Boolean

    /**
     * Restores an active or paused session if one was persisted prior to application process restart.
     * Retains the same ID and state; stopped, ended, deleted and inactive records are not resumed.
     * Android starts enabled services after boot/unlock; this method does not grant permissions
     * or bypass force-stop, disabled services, or device background restrictions.
     */
    suspend fun restoreSessionIfActive(): MonitoringSession?

    /**
     * Resets active session state if the deleted session was the active or paused session.
     */
    suspend fun onSessionDeleted(sessionId: String) {}

    /**
     * Completely resets in-memory session tracking back to INACTIVE and clears active session reference.
     */
    suspend fun resetSessionState() {}
}

