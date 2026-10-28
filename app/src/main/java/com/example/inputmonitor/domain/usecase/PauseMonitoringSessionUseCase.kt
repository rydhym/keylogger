package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.domain.session.SessionManager

/**
 * Use case to pause an active monitoring session.
 */
class PauseMonitoringSessionUseCase(
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(): Boolean {
        return sessionManager.pauseSession()
    }
}
