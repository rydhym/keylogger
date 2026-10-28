package com.example.inputmonitor.ui.settings

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.EventCounts
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.InteractionStatistics
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.session.SessionManager
import com.example.inputmonitor.domain.usecase.DeleteAllApplicationDataUseCase
import com.example.inputmonitor.domain.usecase.DeleteAllSessionsUseCase
import com.example.inputmonitor.domain.usecase.DeleteSessionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel] testing UI states, dialog flows, logging toggles,
 * and destructive data management operations (SRS.md §55).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeSessionRepository : SessionRepository {
        val sessions = MutableStateFlow<List<MonitoringSession>>(emptyList())
        var deleteSessionCalled = false
        var deleteAllSessionsCalled = false

        override suspend fun createSession(session: MonitoringSession) {
            sessions.value = sessions.value + session
        }

        override suspend fun updateSession(session: MonitoringSession) {
            val list = sessions.value.toMutableList()
            val idx = list.indexOfFirst { it.id == session.id }
            if (idx >= 0) list[idx] = session
            sessions.value = list
        }

        override suspend fun getSessionById(id: String): MonitoringSession? = sessions.value.find { it.id == id }
        override fun observeSessionById(id: String): Flow<MonitoringSession?> = flowOf(sessions.value.find { it.id == id })
        override suspend fun getActiveSession(): MonitoringSession? = sessions.value.find { it.state == SessionState.ACTIVE }
        override fun observeActiveSession(): Flow<MonitoringSession?> = flowOf(sessions.value.find { it.state == SessionState.ACTIVE })
        override fun observeAllSessions(): Flow<List<MonitoringSession>> = sessions
        override suspend fun getAllSessions(): List<MonitoringSession> = sessions.value
        override suspend fun finishSession(id: String, endTime: Long): Boolean = true

        override suspend fun deleteSession(id: String): Boolean {
            deleteSessionCalled = true
            sessions.value = sessions.value.filterNot { it.id == id }
            return true
        }

        override suspend fun deleteAllSessions(): Boolean {
            deleteAllSessionsCalled = true
            sessions.value = emptyList()
            return true
        }
    }

    private class FakeEventRepository : EventRepository {
        val eventCountsFlow = MutableStateFlow(EventCounts(total = 42L))
        var deleteAllCalled = false

        override suspend fun insertEvent(event: InteractionEvent): Long = 1L
        override suspend fun insertEvents(events: List<InteractionEvent>): List<Long> = emptyList()
        override suspend fun getEventById(id: Long): InteractionEvent? = null
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEvent> = emptyList()
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent> = emptyList()
        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEvent> = emptyList()
        override suspend fun getEventCountForSession(sessionId: String): Int = eventCountsFlow.value.total.toInt()
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(eventCountsFlow.value.total.toInt())
        override fun observeEventCounts(sessionId: String?): Flow<EventCounts> = eventCountsFlow
        override fun observeLatestEvent(): Flow<InteractionEvent?> = flowOf(null)
        override fun observeEventsPaged(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override fun observeFilteredCount(filter: com.example.inputmonitor.domain.model.EventFilterCriteria): Flow<Int> = flowOf(0)
        override fun observeDistinctPackageNames(): Flow<List<String>> = flowOf(emptyList())

        override suspend fun deleteEventsForSession(sessionId: String): Boolean {
            eventCountsFlow.value = EventCounts(total = 0L)
            return true
        }

        override suspend fun deleteAllEvents(): Boolean {
            deleteAllCalled = true
            eventCountsFlow.value = EventCounts(total = 0L)
            return true
        }

        override fun observeStatistics(sessionId: String?): Flow<InteractionStatistics> =
            flowOf(InteractionStatistics())
        override suspend fun getStatistics(sessionId: String?): InteractionStatistics =
            InteractionStatistics()
    }

    private class FakePreferencesRepository : PreferencesRepository {
        val keyboardEnabled = MutableStateFlow(true)
        val accessibilityEnabled = MutableStateFlow(true)
        val activeSession = MutableStateFlow<String?>("sess-1")
        var clearCalled = false

        override val isOnboardingCompleted: Flow<Boolean> = flowOf(true)
        override suspend fun setOnboardingCompleted(completed: Boolean) {}

        override val activeSessionId: Flow<String?> = activeSession
        override suspend fun setActiveSessionId(sessionId: String?) {
            activeSession.value = sessionId
        }

        override val isKeyboardLoggingEnabled: Flow<Boolean> = keyboardEnabled
        override suspend fun setKeyboardLoggingEnabled(enabled: Boolean) {
            keyboardEnabled.value = enabled
        }

        override val isAccessibilityLoggingEnabled: Flow<Boolean> = accessibilityEnabled
        override suspend fun setAccessibilityLoggingEnabled(enabled: Boolean) {
            accessibilityEnabled.value = enabled
        }

        override suspend fun clearAllPreferences() {
            clearCalled = true
            keyboardEnabled.value = true
            accessibilityEnabled.value = true
            activeSession.value = null
        }
    }

    private class FakeSessionManager : SessionManager {
        override val currentSession: StateFlow<MonitoringSession?> = MutableStateFlow(null)
        override val sessionState: StateFlow<SessionState> = MutableStateFlow(SessionState.INACTIVE)

        var resetCalled = false
        var deletedSessionId: String? = null

        override suspend fun startSession(name: String, notes: String?): MonitoringSession =
            throw UnsupportedOperationException()
        override suspend fun pauseSession(): Boolean = true
        override suspend fun resumeSession(): Boolean = true
        override suspend fun stopSession(): Boolean = true
        override suspend fun restoreSessionIfActive(): MonitoringSession? = null

        override suspend fun onSessionDeleted(sessionId: String) {
            deletedSessionId = sessionId
        }

        override suspend fun resetSessionState() {
            resetCalled = true
        }
    }

    private lateinit var sessionRepository: FakeSessionRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var preferencesRepository: FakePreferencesRepository
    private lateinit var sessionManager: FakeSessionManager
    private lateinit var deleteSessionUseCase: DeleteSessionUseCase
    private lateinit var deleteAllSessionsUseCase: DeleteAllSessionsUseCase
    private lateinit var deleteAllApplicationDataUseCase: DeleteAllApplicationDataUseCase
    private lateinit var viewModel: SettingsViewModel

    private val sampleSession = MonitoringSession(
        id = "session-test-1",
        name = "Alpha Session",
        startTime = 1000L,
        endTime = 2000L,
        state = SessionState.STOPPED,
        androidVersion = "15",
        deviceModel = "Pixel",
        appVersion = "1.0",
        notes = null
    )

    private class FakeBackupSettingsRepository : com.example.inputmonitor.domain.backup.repository.BackupSettingsRepository {
        val settingsFlow = MutableStateFlow(com.example.inputmonitor.domain.backup.model.BackupSettings())
        override val backupSettings: Flow<com.example.inputmonitor.domain.backup.model.BackupSettings> = settingsFlow
        override suspend fun getBackupSettings(): com.example.inputmonitor.domain.backup.model.BackupSettings = settingsFlow.value
        override suspend fun setBackupEnabled(enabled: Boolean) { settingsFlow.value = settingsFlow.value.copy(enabled = enabled) }
        override suspend fun setWifiOnly(wifiOnly: Boolean) { settingsFlow.value = settingsFlow.value.copy(wifiOnly = wifiOnly) }
        override suspend fun setProviderConfig(providerId: String, endpoint: String?) {
            settingsFlow.value = settingsFlow.value.copy(providerId = providerId, providerEndpoint = endpoint)
        }
        override suspend fun recordBackupAttempt(timestamp: Long) {}
        override suspend fun recordBackupSuccess(timestamp: Long, remoteBackupId: String?) {}
        override suspend fun recordBackupFailure(reason: String, authRequired: Boolean) {}
        override suspend fun clearAuthenticationRequired() {}
        override suspend fun resetBackupSettings() { settingsFlow.value = com.example.inputmonitor.domain.backup.model.BackupSettings() }
    }

    private class FakeBackupTokenStorage : com.example.inputmonitor.data.backup.security.BackupTokenStorage {
        var token: String? = null
        override suspend fun saveAuthToken(token: String) { this.token = token }
        override suspend fun getAuthToken(): String? = token
        override suspend fun clearAuthToken() { token = null }
        override suspend fun hasAuthToken(): Boolean = token != null
    }

    private class FakeBackupRepository : com.example.inputmonitor.domain.backup.repository.BackupRepository {
        override suspend fun uploadBackup(file: java.io.File, metadata: com.example.inputmonitor.domain.backup.model.BackupMetadata): com.example.inputmonitor.domain.backup.model.BackupResult =
            com.example.inputmonitor.domain.backup.model.BackupResult.Success("id")
        override suspend fun testConnection(endpoint: String, token: String?): com.example.inputmonitor.domain.backup.model.BackupResult =
            com.example.inputmonitor.domain.backup.model.BackupResult.Success("ok")
    }

    private class FakeAutoBackupScheduler : com.example.inputmonitor.platform.backup.AutoBackupScheduler {
        var isPeriodicScheduled = false
        var isImmediateTriggered = false
        var lastWifiOnly: Boolean? = null
        var isCancelled = false

        override fun schedulePeriodicBackup(wifiOnly: Boolean) {
            isPeriodicScheduled = true
            lastWifiOnly = wifiOnly
            isCancelled = false
        }
        override fun triggerImmediateBackup(wifiOnly: Boolean) {
            isImmediateTriggered = true
        }
        override fun cancelPeriodicBackup() {
            isCancelled = true
            isPeriodicScheduled = false
        }
    }

    private lateinit var backupSettingsRepository: FakeBackupSettingsRepository
    private lateinit var backupTokenStorage: FakeBackupTokenStorage
    private lateinit var backupRepository: FakeBackupRepository
    private lateinit var autoBackupScheduler: FakeAutoBackupScheduler

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        sessionRepository = FakeSessionRepository()
        eventRepository = FakeEventRepository()
        preferencesRepository = FakePreferencesRepository()
        sessionManager = FakeSessionManager()
        backupSettingsRepository = FakeBackupSettingsRepository()
        backupTokenStorage = FakeBackupTokenStorage()
        backupRepository = FakeBackupRepository()
        autoBackupScheduler = FakeAutoBackupScheduler()

        deleteSessionUseCase = DeleteSessionUseCase(
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            sessionManager = sessionManager,
            dispatchers = testDispatchers
        )

        deleteAllSessionsUseCase = DeleteAllSessionsUseCase(
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            sessionManager = sessionManager,
            dispatchers = testDispatchers
        )

        deleteAllApplicationDataUseCase = DeleteAllApplicationDataUseCase(
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            preferencesRepository = preferencesRepository,
            sessionManager = sessionManager,
            dispatchers = testDispatchers
        )

        viewModel = SettingsViewModel(
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
            dispatchers = testDispatchers
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_observesRepositoryStateCorrectly() = runTest {
        sessionRepository.createSession(sampleSession)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.sessions.size)
        assertEquals("Alpha Session", state.sessions[0].name)
        assertEquals(42, state.totalEventsCount)
        assertTrue(state.isKeyboardLoggingEnabled)
        assertTrue(state.isAccessibilityLoggingEnabled)
        assertFalse(state.isOperating)
        assertNull(state.pendingDeleteSession)
        assertFalse(state.showDeleteAllSessionsDialog)
        assertFalse(state.showDeleteAllDataDialog)
    }

    @Test
    fun loggingToggles_updatePreferences() = runTest {
        advanceUntilIdle()

        viewModel.setKeyboardLoggingEnabled(false)
        advanceUntilIdle()
        assertFalse(preferencesRepository.keyboardEnabled.value)
        assertFalse(viewModel.uiState.value.isKeyboardLoggingEnabled)

        viewModel.setAccessibilityLoggingEnabled(false)
        advanceUntilIdle()
        assertFalse(preferencesRepository.accessibilityEnabled.value)
        assertFalse(viewModel.uiState.value.isAccessibilityLoggingEnabled)
    }

    @Test
    fun singleSessionDelete_dialogAndExecutionFlow() = runTest {
        sessionRepository.createSession(sampleSession)
        advanceUntilIdle()

        // 1. Request deletion -> Dialog appears
        viewModel.requestDeleteSession(sampleSession)
        advanceUntilIdle()
        assertEquals(sampleSession, viewModel.uiState.value.pendingDeleteSession)

        // 2. Dismiss dialog -> Cleared without action
        viewModel.dismissDeleteSessionDialog()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.pendingDeleteSession)
        assertEquals(1, sessionRepository.sessions.value.size)

        // 3. Request again & Confirm -> Deleted with confirmation message
        viewModel.requestDeleteSession(sampleSession)
        advanceUntilIdle()
        viewModel.confirmDeleteSession()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingDeleteSession)
        assertEquals(0, sessionRepository.sessions.value.size)
        assertTrue(sessionRepository.deleteSessionCalled)
        assertEquals("session-test-1", sessionManager.deletedSessionId)
        assertNotNull(viewModel.uiState.value.userMessage)
        assertTrue(viewModel.uiState.value.userMessage!!.contains("Alpha Session"))
    }

    @Test
    fun bulkDeleteAllSessions_dialogAndExecutionFlow() = runTest {
        sessionRepository.createSession(sampleSession)
        advanceUntilIdle()

        // 1. Request dialog
        viewModel.requestDeleteAllSessions()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDeleteAllSessionsDialog)

        // 2. Dismiss
        viewModel.dismissDeleteAllSessionsDialog()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showDeleteAllSessionsDialog)
        assertEquals(1, sessionRepository.sessions.value.size)

        // 3. Confirm
        viewModel.requestDeleteAllSessions()
        advanceUntilIdle()
        viewModel.confirmDeleteAllSessions()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showDeleteAllSessionsDialog)
        assertEquals(0, sessionRepository.sessions.value.size)
        assertTrue(sessionRepository.deleteAllSessionsCalled)
        assertTrue(eventRepository.deleteAllCalled)
        assertTrue(sessionManager.resetCalled)
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun deleteAllApplicationData_dialogAndExecutionFlow() = runTest {
        sessionRepository.createSession(sampleSession)
        advanceUntilIdle()

        // 1. Request dialog
        viewModel.requestDeleteAllData()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDeleteAllDataDialog)

        // 2. Dismiss
        viewModel.dismissDeleteAllDataDialog()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showDeleteAllDataDialog)

        // 3. Confirm
        viewModel.requestDeleteAllData()
        advanceUntilIdle()
        viewModel.confirmDeleteAllData()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showDeleteAllDataDialog)
        assertEquals(0, sessionRepository.sessions.value.size)
        assertTrue(sessionRepository.deleteAllSessionsCalled)
        assertTrue(eventRepository.deleteAllCalled)
        assertTrue(preferencesRepository.clearCalled)
        assertTrue(sessionManager.resetCalled)
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun dismissFeedbackMessages_clearsState() = runTest {
        sessionRepository.createSession(sampleSession)
        advanceUntilIdle()

        viewModel.requestDeleteSession(sampleSession)
        viewModel.confirmDeleteSession()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.userMessage)
        viewModel.dismissUserMessage()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun setAutomaticBackupEnabled_whenNotConfigured_opensDialogAndSetsError() = runTest {
        viewModel.setAutomaticBackupEnabled(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showConnectBackupDialog)
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.backupSettings.enabled)
        assertFalse(autoBackupScheduler.isPeriodicScheduled)
    }

    @Test
    fun connectBackup_success_persistsConfigAndClearsDialog() = runTest {
        viewModel.connectBackup("https://backup.example.com/api", "my-secret-token")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showConnectBackupDialog)
        assertEquals("https://backup.example.com/api", backupSettingsRepository.settingsFlow.value.providerEndpoint)
        assertEquals("my-secret-token", backupTokenStorage.token)
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun setAutomaticBackupEnabled_whenConfigured_schedulesPeriodicWork() = runTest {
        backupSettingsRepository.setProviderConfig("https_rest", "https://cloud.example.com/api")
        advanceUntilIdle()

        viewModel.setAutomaticBackupEnabled(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.backupSettings.enabled)
        assertTrue(autoBackupScheduler.isPeriodicScheduled)

        // Now disable
        viewModel.setAutomaticBackupEnabled(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.backupSettings.enabled)
        assertTrue(autoBackupScheduler.isCancelled)
    }

    @Test
    fun triggerBackupNow_triggersImmediateWork() = runTest {
        backupSettingsRepository.setProviderConfig("https_rest", "https://cloud.example.com/api")
        advanceUntilIdle()

        viewModel.triggerBackupNow()
        advanceUntilIdle()

        assertTrue(autoBackupScheduler.isImmediateTriggered)
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun disconnectBackup_cancelsWorkClearsTokensAndDisables() = runTest {
        backupSettingsRepository.setProviderConfig("https_rest", "https://cloud.example.com/api")
        backupSettingsRepository.setBackupEnabled(true)
        backupTokenStorage.saveAuthToken("token")
        autoBackupScheduler.schedulePeriodicBackup(true)
        advanceUntilIdle()

        viewModel.disconnectBackup()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.backupSettings.enabled)
        assertNull(backupSettingsRepository.settingsFlow.value.providerEndpoint)
        assertNull(backupTokenStorage.token)
        assertTrue(autoBackupScheduler.isCancelled)
    }
}
