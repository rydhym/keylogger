package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.session.SessionManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Use case to observe the current session lifecycle state (INACTIVE, ACTIVE, PAUSED, STOPPED).
 */
class ObserveSessionStateUseCase(
    private val sessionManager: SessionManager
) {
    operator fun invoke(): StateFlow<SessionState> {
        return sessionManager.sessionState
    }
}
