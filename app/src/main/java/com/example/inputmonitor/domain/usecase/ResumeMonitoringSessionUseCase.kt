package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.domain.session.SessionManager

/**
 * Use case to resume a paused monitoring session.
 */
class ResumeMonitoringSessionUseCase(
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(): Boolean {
        return sessionManager.resumeSession()
    }
}
