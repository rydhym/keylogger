package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.session.SessionManager
import kotlinx.coroutines.withContext

/**
 * Use case to completely purge all application data: all monitoring sessions,
 * all recorded interaction events, in-memory session tracking, and reset all application
 * preferences back to initial defaults (SRS.md §55, IMPLEMENTATION_PLAN.md §55).
 */
class DeleteAllApplicationDataUseCase(
    private val sessionRepository: SessionRepository,
    private val eventRepository: EventRepository,
    private val preferencesRepository: PreferencesRepository,
    private val sessionManager: SessionManager,
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            // 1. Purge all recorded interaction events
            eventRepository.deleteAllEvents()

            // 2. Purge all monitoring sessions
            sessionRepository.deleteAllSessions()

            // 3. Reset in-memory session tracking
            sessionManager.resetSessionState()

            // 4. Wipe stored preferences back to clean state
            preferencesRepository.clearAllPreferences()
        }
    }
}
