package com.example.inputmonitor.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * DataStore implementation of [PreferencesRepository] (DEC-009).
 */
class PreferencesRepositoryImpl(
    private val context: Context,
    private val dispatchers: DispatcherProvider
) : PreferencesRepository {

    companion object {
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
        private val KEY_KEYBOARD_LOGGING_ENABLED = booleanPreferencesKey("keyboard_logging_enabled")
        private val KEY_ACCESSIBILITY_LOGGING_ENABLED = booleanPreferencesKey("accessibility_logging_enabled")
    }

    override val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_ONBOARDING_COMPLETED] ?: false }
        .flowOn(dispatchers.io)

    override suspend fun setOnboardingCompleted(completed: Boolean) = withContext(dispatchers.io) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
        Unit
    }

    override val activeSessionId: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[KEY_ACTIVE_SESSION_ID] }
        .flowOn(dispatchers.io)

    override suspend fun setActiveSessionId(sessionId: String?) = withContext(dispatchers.io) {
        context.dataStore.edit { preferences ->
            if (sessionId != null) {
                preferences[KEY_ACTIVE_SESSION_ID] = sessionId
            } else {
                preferences.remove(KEY_ACTIVE_SESSION_ID)
            }
        }
        Unit
    }

    override val isKeyboardLoggingEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_KEYBOARD_LOGGING_ENABLED] ?: true }
        .flowOn(dispatchers.io)

    override suspend fun setKeyboardLoggingEnabled(enabled: Boolean) = withContext(dispatchers.io) {
        context.dataStore.edit { preferences ->
            preferences[KEY_KEYBOARD_LOGGING_ENABLED] = enabled
        }
        Unit
    }

    override val isAccessibilityLoggingEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_ACCESSIBILITY_LOGGING_ENABLED] ?: true }
        .flowOn(dispatchers.io)

    override suspend fun setAccessibilityLoggingEnabled(enabled: Boolean) = withContext(dispatchers.io) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ACCESSIBILITY_LOGGING_ENABLED] = enabled
        }
        Unit
    }

    override suspend fun clearAllPreferences() = withContext(dispatchers.io) {
        context.dataStore.edit { it.clear() }
        Unit
    }
}
