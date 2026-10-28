package com.example.inputmonitor.data.session

import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.session.SessionGate
import com.example.inputmonitor.domain.session.SessionManager

/**
 * Implementation of [SessionGate] (Architecture.md §44).
 *
 * Enforces that no interaction event proceeds through the pipeline unless
 * the current session is explicitly in [SessionState.ACTIVE].
 * When INACTIVE, PAUSED, or STOPPED, incoming events are immediately dropped.
 */
class SessionGateImpl(
    private val sessionManager: SessionManager
) : SessionGate {

    override fun canProcessEvents(): Boolean {
        return sessionManager.sessionState.value == SessionState.ACTIVE
    }

    override fun shouldProcess(event: RawInteractionEvent): Boolean {
        return canProcessEvents()
    }
}
