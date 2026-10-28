package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.session.SessionManager
import kotlinx.coroutines.withContext

/**
 * Use case to delete all monitoring sessions and all recorded interaction events (SRS.md §55).
 */
class DeleteAllSessionsUseCase(
    private val sessionRepository: SessionRepository,
    private val eventRepository: EventRepository,
    private val sessionManager: SessionManager,
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            // 1. Delete all recorded events
            eventRepository.deleteAllEvents()

            // 2. Delete all monitoring sessions
            sessionRepository.deleteAllSessions()

            // 3. Reset in-memory session manager tracking
            sessionManager.resetSessionState()
        }
    }
}
