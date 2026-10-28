package com.example.inputmonitor.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing persistent application preferences via DataStore (DEC-009).
 */
interface PreferencesRepository {
    val isOnboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    val activeSessionId: Flow<String?>
    suspend fun setActiveSessionId(sessionId: String?)

    val isKeyboardLoggingEnabled: Flow<Boolean>
    suspend fun setKeyboardLoggingEnabled(enabled: Boolean)

    val isAccessibilityLoggingEnabled: Flow<Boolean>
    suspend fun setAccessibilityLoggingEnabled(enabled: Boolean)

    suspend fun clearAllPreferences()
}
