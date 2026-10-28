package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.session.SessionManager

/**
 * Use case to start a new monitoring session.
 */
class StartMonitoringSessionUseCase(
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(name: String, notes: String? = null): MonitoringSession {
        require(name.isNotBlank()) { "Session name cannot be blank" }
        return sessionManager.startSession(name.trim(), notes?.trim())
    }
}
