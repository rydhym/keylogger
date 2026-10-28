package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.session.SessionManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Use case to observe the current monitoring session as a StateFlow.
 */
class ObserveActiveSessionUseCase(
    private val sessionManager: SessionManager
) {
    operator fun invoke(): StateFlow<MonitoringSession?> {
        return sessionManager.currentSession
    }
}
