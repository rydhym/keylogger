package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.session.SessionManager
import kotlinx.coroutines.withContext

/**
 * Use case to delete a specific monitoring session and all of its associated events (Architecture.md §10, SRS.md §55).
 *
 * Enforces cascading event deletion as a multi-layer defense-in-depth alongside
 * SQLite Room foreign key cascade constraints.
 */
class DeleteSessionUseCase(
    private val sessionRepository: SessionRepository,
    private val eventRepository: EventRepository,
    private val sessionManager: SessionManager,
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(sessionId: String): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            require(sessionId.isNotBlank()) { "Session ID cannot be blank" }

            // 1. Explicitly clean up associated events first (defense-in-depth)
            eventRepository.deleteEventsForSession(sessionId)

            // 2. Delete the session record
            sessionRepository.deleteSession(sessionId)

            // 3. Reset in-memory session manager state if the deleted session was active
            sessionManager.onSessionDeleted(sessionId)
        }
    }
}
