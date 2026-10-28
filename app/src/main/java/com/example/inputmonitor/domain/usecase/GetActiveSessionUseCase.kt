package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.session.SessionManager

/**
 * Use case to retrieve the current monitoring session synchronously/instantaneously from the in-memory state.
 */
class GetActiveSessionUseCase(
    private val sessionManager: SessionManager
) {
    operator fun invoke(): MonitoringSession? {
        return sessionManager.currentSession.value
    }
}
