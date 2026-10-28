package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.domain.session.SessionManager

/**
 * Use case to stop an active or paused monitoring session.
 */
class StopMonitoringSessionUseCase(
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(): Boolean {
        return sessionManager.stopSession()
    }
}
