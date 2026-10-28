package com.example.inputmonitor.data.backup.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.backup.model.BackupSettings
import com.example.inputmonitor.domain.backup.repository.BackupSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_preferences")

/**
 * DataStore implementation of [BackupSettingsRepository].
 *
 * Security: Does not store authentication tokens or credentials in plaintext.
 */
class BackupSettingsRepositoryImpl(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val customDataStore: DataStore<Preferences>? = null
) : BackupSettingsRepository {

    private val dataStore: DataStore<Preferences>
        get() = customDataStore ?: context.backupDataStore

    companion object {
        private val KEY_BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        private val KEY_PROVIDER_ID = stringPreferencesKey("provider_id")
        private val KEY_PROVIDER_ENDPOINT = stringPreferencesKey("provider_endpoint")
        private val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val KEY_LAST_ATTEMPT_AT = longPreferencesKey("last_attempt_at")
        private val KEY_LAST_SUCCESSFUL_BACKUP_AT = longPreferencesKey("last_successful_backup_at")
        private val KEY_LAST_FAILURE_REASON = stringPreferencesKey("last_failure_reason")
        private val KEY_LAST_REMOTE_BACKUP_ID = stringPreferencesKey("last_remote_backup_id")
        private val KEY_AUTH_REQUIRED = booleanPreferencesKey("is_auth_required")
    }

    override val backupSettings: Flow<BackupSettings> = dataStore.data
        .map { prefs ->
            BackupSettings(
                enabled = prefs[KEY_BACKUP_ENABLED] ?: false,
                providerId = prefs[KEY_PROVIDER_ID] ?: "https_rest",
                providerEndpoint = prefs[KEY_PROVIDER_ENDPOINT],
                wifiOnly = prefs[KEY_WIFI_ONLY] ?: true,
                lastAttemptAt = prefs[KEY_LAST_ATTEMPT_AT],
                lastSuccessfulBackupAt = prefs[KEY_LAST_SUCCESSFUL_BACKUP_AT],
                lastFailureReason = prefs[KEY_LAST_FAILURE_REASON],
                lastRemoteBackupId = prefs[KEY_LAST_REMOTE_BACKUP_ID],
                isAuthenticationRequired = prefs[KEY_AUTH_REQUIRED] ?: false
            )
        }
        .flowOn(dispatchers.io)

    override suspend fun getBackupSettings(): BackupSettings = withContext(dispatchers.io) {
        backupSettings.first()
    }

    override suspend fun setBackupEnabled(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[KEY_BACKUP_ENABLED] = enabled
        }
    }

    override suspend fun setWifiOnly(wifiOnly: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[KEY_WIFI_ONLY] = wifiOnly
        }
    }

    override suspend fun setProviderConfig(providerId: String, endpoint: String?): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[KEY_PROVIDER_ID] = providerId
            if (endpoint != null) {
                prefs[KEY_PROVIDER_ENDPOINT] = endpoint
            } else {
                prefs.remove(KEY_PROVIDER_ENDPOINT)
            }
        }
    }

    override suspend fun recordBackupAttempt(timestamp: Long): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_ATTEMPT_AT] = timestamp
        }
    }

    override suspend fun recordBackupSuccess(timestamp: Long, remoteBackupId: String?): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SUCCESSFUL_BACKUP_AT] = timestamp
            prefs.remove(KEY_LAST_FAILURE_REASON)
            prefs[KEY_AUTH_REQUIRED] = false
            if (remoteBackupId != null) {
                prefs[KEY_LAST_REMOTE_BACKUP_ID] = remoteBackupId
            }
        }
    }

    override suspend fun recordBackupFailure(reason: String, authRequired: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_FAILURE_REASON] = reason
            if (authRequired) {
                prefs[KEY_AUTH_REQUIRED] = true
            }
        }
    }

    override suspend fun clearAuthenticationRequired(): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTH_REQUIRED] = false
        }
    }

    override suspend fun resetBackupSettings(): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
