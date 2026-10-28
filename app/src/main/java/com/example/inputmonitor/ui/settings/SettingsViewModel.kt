package com.example.inputmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.backup.security.BackupTokenStorage
import com.example.inputmonitor.domain.backup.model.BackupResult
import com.example.inputmonitor.domain.backup.model.BackupSettings
import com.example.inputmonitor.domain.backup.repository.BackupRepository
import com.example.inputmonitor.domain.backup.repository.BackupSettingsRepository
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.usecase.DeleteAllApplicationDataUseCase
import com.example.inputmonitor.domain.usecase.DeleteAllSessionsUseCase
import com.example.inputmonitor.domain.usecase.DeleteSessionUseCase
import com.example.inputmonitor.platform.backup.AutoBackupScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating user settings, logging preferences, destructive data management,
 * and user-consented automatic daily cloud backup (SRS.md §55, Architecture.md §50).
 */
class SettingsViewModel(
    private val sessionRepository: SessionRepository,
    private val eventRepository: EventRepository,
    private val preferencesRepository: PreferencesRepository,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val deleteAllSessionsUseCase: DeleteAllSessionsUseCase,
    private val deleteAllApplicationDataUseCase: DeleteAllApplicationDataUseCase,
    private val backupSettingsRepository: BackupSettingsRepository,
    private val backupTokenStorage: BackupTokenStorage,
    private val backupRepository: BackupRepository,
    private val autoBackupScheduler: AutoBackupScheduler,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    private val _dialogState = MutableStateFlow(DialogState())
    private val _feedbackState = MutableStateFlow(FeedbackState())
    private val _isTestingConnection = MutableStateFlow(false)

    private val modalAndFeedbackFlow = combine(
        _dialogState,
        _feedbackState,
        _isTestingConnection
    ) { dialog, feedback, isTesting ->
        ModalAndFeedback(dialog, feedback, isTesting)
    }

    private data class LoggingPreferences(
        val keyboard: Boolean,
        val accessibility: Boolean
    )

    private val loggingPrefsFlow = combine(
        preferencesRepository.isKeyboardLoggingEnabled,
        preferencesRepository.isAccessibilityLoggingEnabled
    ) { kb, acc ->
        LoggingPreferences(kb, acc)
    }

    private val totalEventsCountFlow = eventRepository.observeEventCounts(null)
        .map { it.total.toInt() }

    val uiState: StateFlow<SettingsUiState> = combine(
        sessionRepository.observeAllSessions(),
        loggingPrefsFlow,
        totalEventsCountFlow,
        modalAndFeedbackFlow,
        backupSettingsRepository.backupSettings
    ) { sessions, loggingPrefs, eventsCount, modalAndFeedback, backupSettings ->
        SettingsUiState(
            sessions = sessions,
            totalEventsCount = eventsCount,
            isKeyboardLoggingEnabled = loggingPrefs.keyboard,
            isAccessibilityLoggingEnabled = loggingPrefs.accessibility,
            isLoading = false,
            isOperating = modalAndFeedback.feedback.isOperating,
            pendingDeleteSession = modalAndFeedback.dialog.pendingDeleteSession,
            showDeleteAllSessionsDialog = modalAndFeedback.dialog.showDeleteAllSessionsDialog,
            showDeleteAllDataDialog = modalAndFeedback.dialog.showDeleteAllDataDialog,
            userMessage = modalAndFeedback.feedback.userMessage,
            error = modalAndFeedback.feedback.error,
            backupSettings = backupSettings,
            showConnectBackupDialog = modalAndFeedback.dialog.showConnectBackupDialog,
            showDisconnectBackupDialog = modalAndFeedback.dialog.showDisconnectBackupDialog,
            isTestingConnection = modalAndFeedback.isTesting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(isLoading = true)
    )

    fun setKeyboardLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            preferencesRepository.setKeyboardLoggingEnabled(enabled)
        }
    }

    fun setAccessibilityLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            preferencesRepository.setAccessibilityLoggingEnabled(enabled)
        }
    }

    // --- Automatic Daily Cloud Backup Actions ---

    fun setAutomaticBackupEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            val settings = backupSettingsRepository.getBackupSettings()
            if (enabled && !settings.isConfigured) {
                // If attempting to enable without a configured destination, open connect dialog
                _dialogState.value = _dialogState.value.copy(showConnectBackupDialog = true)
                _feedbackState.value = _feedbackState.value.copy(
                    error = "Please connect a cloud destination before enabling automatic backup."
                )
                return@launch
            }

            backupSettingsRepository.setBackupEnabled(enabled)
            if (enabled) {
                autoBackupScheduler.schedulePeriodicBackup(settings.wifiOnly)
                _feedbackState.value = _feedbackState.value.copy(
                    userMessage = "Automatic Daily Backup enabled. Runs approximately every 24 hours."
                )
            } else {
                autoBackupScheduler.cancelPeriodicBackup()
                _feedbackState.value = _feedbackState.value.copy(
                    userMessage = "Automatic Daily Backup disabled."
                )
            }
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            backupSettingsRepository.setWifiOnly(wifiOnly)
            val settings = backupSettingsRepository.getBackupSettings()
            if (settings.enabled) {
                autoBackupScheduler.schedulePeriodicBackup(wifiOnly)
            }
        }
    }

    fun triggerBackupNow() {
        viewModelScope.launch(dispatchers.io) {
            val settings = backupSettingsRepository.getBackupSettings()
            if (!settings.isConfigured) {
                _feedbackState.value = _feedbackState.value.copy(
                    error = "Cannot back up: destination is not configured."
                )
                return@launch
            }
            autoBackupScheduler.triggerImmediateBackup(settings.wifiOnly)
            _feedbackState.value = _feedbackState.value.copy(
                userMessage = "Backup initiated in background."
            )
        }
    }

    fun showConnectBackupDialog(show: Boolean) {
        _dialogState.value = _dialogState.value.copy(showConnectBackupDialog = show)
    }

    fun showDisconnectBackupDialog(show: Boolean) {
        _dialogState.value = _dialogState.value.copy(showDisconnectBackupDialog = show)
    }

    fun connectBackup(endpoint: String, token: String?) {
        val trimmedEndpoint = endpoint.trim()
        val trimmedToken = token?.trim()?.ifBlank { null }

        if (!trimmedEndpoint.startsWith("https://", ignoreCase = true) &&
            !trimmedEndpoint.startsWith("http://localhost", ignoreCase = true) &&
            !trimmedEndpoint.startsWith("http://127.0.0.1", ignoreCase = true)
        ) {
            _feedbackState.value = _feedbackState.value.copy(
                error = "Security Error: Destination URL must use secure HTTPS."
            )
            return
        }

        _isTestingConnection.value = true
        viewModelScope.launch(dispatchers.io) {
            try {
                val testResult = backupRepository.testConnection(trimmedEndpoint, trimmedToken)
                _isTestingConnection.value = false

                when (testResult) {
                    is BackupResult.Success -> {
                        if (trimmedToken != null) {
                            backupTokenStorage.saveAuthToken(trimmedToken)
                        } else {
                            backupTokenStorage.clearAuthToken()
                        }
                        backupSettingsRepository.setProviderConfig("https_rest", trimmedEndpoint)
                        backupSettingsRepository.clearAuthenticationRequired()
                        _dialogState.value = _dialogState.value.copy(showConnectBackupDialog = false)
                        _feedbackState.value = _feedbackState.value.copy(
                            userMessage = "Cloud destination connected successfully."
                        )
                    }
                    is BackupResult.AuthenticationRequired -> {
                        _feedbackState.value = _feedbackState.value.copy(
                            error = "Authentication failed: Please verify your access token or credentials."
                        )
                    }
                    is BackupResult.RetryableFailure -> {
                        _feedbackState.value = _feedbackState.value.copy(
                            error = "Connection failed: ${testResult.message}"
                        )
                    }
                    is BackupResult.PermanentFailure -> {
                        _feedbackState.value = _feedbackState.value.copy(
                            error = "Connection rejected: ${testResult.message}"
                        )
                    }
                }
            } catch (t: Throwable) {
                _isTestingConnection.value = false
                _feedbackState.value = _feedbackState.value.copy(
                    error = "Failed to connect to destination: ${t.message}"
                )
            }
        }
    }

    fun disconnectBackup() {
        viewModelScope.launch(dispatchers.io) {
            autoBackupScheduler.cancelPeriodicBackup()
            backupSettingsRepository.setBackupEnabled(false)
            backupSettingsRepository.setProviderConfig("https_rest", null)
            backupTokenStorage.clearAuthToken()
            _dialogState.value = _dialogState.value.copy(showDisconnectBackupDialog = false)
            _feedbackState.value = _feedbackState.value.copy(
                userMessage = "Cloud backup destination disconnected."
            )
        }
    }

    // --- Session & Data Deletion Actions ---

    fun requestDeleteSession(session: MonitoringSession) {
        _dialogState.value = _dialogState.value.copy(pendingDeleteSession = session)
    }

    fun dismissDeleteSessionDialog() {
        _dialogState.value = _dialogState.value.copy(pendingDeleteSession = null)
    }

    fun confirmDeleteSession() {
        val session = _dialogState.value.pendingDeleteSession ?: return
        _dialogState.value = _dialogState.value.copy(pendingDeleteSession = null)
        _feedbackState.value = _feedbackState.value.copy(isOperating = true, error = null)

        viewModelScope.launch(dispatchers.io) {
            deleteSessionUseCase(session.id)
                .onSuccess {
                    _feedbackState.value = FeedbackState(
                        isOperating = false,
                        userMessage = "Session \"${session.name}\" and its events were deleted."
                    )
                }
                .onFailure { t ->
                    _feedbackState.value = FeedbackState(
                        isOperating = false,
                        error = t.message ?: "Failed to delete session."
                    )
                }
        }
    }

    fun requestDeleteAllSessions() {
        _dialogState.value = _dialogState.value.copy(showDeleteAllSessionsDialog = true)
    }

    fun dismissDeleteAllSessionsDialog() {
        _dialogState.value = _dialogState.value.copy(showDeleteAllSessionsDialog = false)
    }

    fun confirmDeleteAllSessions() {
        _dialogState.value = _dialogState.value.copy(showDeleteAllSessionsDialog = false)
        _feedbackState.value = _feedbackState.value.copy(isOperating = true, error = null)

        viewModelScope.launch(dispatchers.io) {
            deleteAllSessionsUseCase()
                .onSuccess {
                    _feedbackState.value = FeedbackState(
                        isOperating = false,
                        userMessage = "All sessions and interaction events were deleted."
                    )
                }
                .onFailure { t ->
                    _feedbackState.value = FeedbackState(
                        isOperating = false,
                        error = t.message ?: "Failed to delete all sessions."
                    )
                }
        }
    }

    fun requestDeleteAllData() {
        _dialogState.value = _dialogState.value.copy(showDeleteAllDataDialog = true)
    }

    fun dismissDeleteAllDataDialog() {
        _dialogState.value = _dialogState.value.copy(showDeleteAllDataDialog = false)
    }

    fun confirmDeleteAllData() {
        _dialogState.value = _dialogState.value.copy(showDeleteAllDataDialog = false)
        _feedbackState.value = _feedbackState.value.copy(isOperating = true, error = null)

        viewModelScope.launch(dispatchers.io) {
            autoBackupScheduler.cancelPeriodicBackup()
            backupTokenStorage.clearAuthToken()
            backupSettingsRepository.resetBackupSettings()

            deleteAllApplicationDataUseCase()
                .onSuccess {
                    _feedbackState.value = FeedbackState(
                        isOperating = false,
                        userMessage = "All application data, sessions, and backup settings have been permanently deleted."
                    )
                }
                .onFailure { t ->
                    _feedbackState.value = FeedbackState(
                        isOperating = false,
                        error = t.message ?: "Failed to purge application data."
                    )
                }
        }
    }

    fun dismissUserMessage() {
        _feedbackState.value = _feedbackState.value.copy(userMessage = null)
    }

    fun dismissError() {
        _feedbackState.value = _feedbackState.value.copy(error = null)
    }

    private data class DialogState(
        val pendingDeleteSession: MonitoringSession? = null,
        val showDeleteAllSessionsDialog: Boolean = false,
        val showDeleteAllDataDialog: Boolean = false,
        val showConnectBackupDialog: Boolean = false,
        val showDisconnectBackupDialog: Boolean = false
    )

    private data class FeedbackState(
        val isOperating: Boolean = false,
        val userMessage: String? = null,
        val error: String? = null
    )

    private data class ModalAndFeedback(
        val dialog: DialogState,
        val feedback: FeedbackState,
        val isTesting: Boolean
    )

    companion object {
        fun provideFactory(
            sessionRepository: SessionRepository,
            eventRepository: EventRepository,
            preferencesRepository: PreferencesRepository,
            deleteSessionUseCase: DeleteSessionUseCase,
            deleteAllSessionsUseCase: DeleteAllSessionsUseCase,
            deleteAllApplicationDataUseCase: DeleteAllApplicationDataUseCase,
            backupSettingsRepository: BackupSettingsRepository,
            backupTokenStorage: BackupTokenStorage,
            backupRepository: BackupRepository,
            autoBackupScheduler: AutoBackupScheduler,
            dispatchers: DispatcherProvider
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    sessionRepository = sessionRepository,
                    eventRepository = eventRepository,
                    preferencesRepository = preferencesRepository,
                    deleteSessionUseCase = deleteSessionUseCase,
                    deleteAllSessionsUseCase = deleteAllSessionsUseCase,
                    deleteAllApplicationDataUseCase = deleteAllApplicationDataUseCase,
                    backupSettingsRepository = backupSettingsRepository,
                    backupTokenStorage = backupTokenStorage,
                    backupRepository = backupRepository,
                    autoBackupScheduler = autoBackupScheduler,
                    dispatchers = dispatchers
                ) as T
            }
        }
    }
}
