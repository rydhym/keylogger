package com.example.inputmonitor.domain.session

import com.example.inputmonitor.domain.model.RawInteractionEvent

/**
 * Gate in the event processing pipeline that determines whether incoming raw interaction events
 * may proceed to privacy filtering and persistence (Architecture.md §44).
 *
 * Rule:
 * Events are only permitted when a monitoring session is in [com.example.inputmonitor.domain.model.SessionState.ACTIVE].
 * When INACTIVE, PAUSED, or STOPPED, interaction events MUST be dropped.
 */
interface SessionGate {

    /**
     * Checks if interaction events can currently be processed.
     */
    fun canProcessEvents(): Boolean

    /**
     * Evaluates whether a specific [RawInteractionEvent] should be permitted through the gate.
     *
     * @return true if permitted, false if the event must be dropped.
     */
    fun shouldProcess(event: RawInteractionEvent): Boolean
}
