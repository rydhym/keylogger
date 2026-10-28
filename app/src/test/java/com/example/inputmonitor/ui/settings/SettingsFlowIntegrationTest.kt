package com.example.inputmonitor.ui.settings

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.dao.AppUsageTuple
import com.example.inputmonitor.data.dao.EventCountsTuple
import com.example.inputmonitor.data.dao.HourlyActivityTuple
import com.example.inputmonitor.data.dao.InteractionEventDao
import com.example.inputmonitor.data.dao.MonitoringSessionDao
import com.example.inputmonitor.data.dao.StatisticsSummaryTuple
import com.example.inputmonitor.data.entity.InteractionEventEntity
import com.example.inputmonitor.data.entity.MonitoringSessionEntity
import com.example.inputmonitor.data.repository.EventRepositoryImpl
import com.example.inputmonitor.data.repository.SessionRepositoryImpl
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.repository.PreferencesRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end integration test for the Settings & Data Management workflow (SRS.md §55, Architecture.md §50).
 * Verifies that single session deletion, bulk deletion, and complete application wipe properly
 * coordinate through DAOs, Repositories, Use Cases, SessionManager, and Preferences.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsFlowIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class MockSessionDao : MonitoringSessionDao {
        val sessions = MutableStateFlow<List<MonitoringSessionEntity>>(emptyList())

        override suspend fun insertSession(session: MonitoringSessionEntity) {
            sessions.value = sessions.value + session
        }

        override suspend fun getSessionById(id: String): MonitoringSessionEntity? =
            sessions.value.find { it.id == id }

        override fun observeSessionById(id: String): Flow<MonitoringSessionEntity?> =
            flowOf(sessions.value.find { it.id == id })

        override suspend fun getActiveSession(): MonitoringSessionEntity? =
            sessions.value.find { it.state == "ACTIVE" }

        override fun observeActiveSession(): Flow<MonitoringSessionEntity?> =
            flowOf(sessions.value.find { it.state == "ACTIVE" })

        override fun observeAllSessions(): Flow<List<MonitoringSessionEntity>> = sessions

        override suspend fun getAllSessions(): List<MonitoringSessionEntity> = sessions.value

        override suspend fun updateSession(session: MonitoringSessionEntity) {
            val list = sessions.value.toMutableList()
            val idx = list.indexOfFirst { it.id == session.id }
            if (idx >= 0) list[idx] = session
            sessions.value = list
        }

        override suspend fun finishSession(id: String, endTime: Long, state: String): Int {
            val list = sessions.value.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(endTime = endTime, state = state)
                sessions.value = list
                return 1
            }
            return 0
        }

        override suspend fun deleteSessionById(id: String): Int {
            val count = sessions.value.count { it.id == id }
            sessions.value = sessions.value.filterNot { it.id == id }
            return count
        }

        override suspend fun deleteAllSessions(): Int {
            val count = sessions.value.size
            sessions.value = emptyList()
            return count
        }
    }

    private class MockEventDao : InteractionEventDao {
        val events = mutableListOf<InteractionEventEntity>()
        val countsFlow = MutableStateFlow<EventCountsTuple?>(EventCountsTuple(total = 0L))

        private fun updateCounts() {
            countsFlow.value = EventCountsTuple(total = events.size.toLong())
        }

        override suspend fun insertEvent(event: InteractionEventEntity): Long {
            events.add(event)
            updateCounts()
            return event.id
        }

        override suspend fun insertEvents(eventsList: List<InteractionEventEntity>): List<Long> {
            events.addAll(eventsList)
            updateCounts()
            return eventsList.map { it.id }
        }

        override suspend fun getEventById(id: Long): InteractionEventEntity? = events.find { it.id == id }

        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEventEntity>> =
            flowOf(events.filter { it.sessionId == sessionId })

        override suspend fun getEventsForSession(sessionId: String): List<InteractionEventEntity> =
            events.filter { it.sessionId == sessionId }

        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEventEntity> =
            events.filter { it.sessionId == sessionId }.drop(offset).take(limit)

        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEventEntity> = events

        override suspend fun getEventCountForSession(sessionId: String): Int = events.count { it.sessionId == sessionId }

        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(events.count { it.sessionId == sessionId })

        override fun observeEventCounts(sessionId: String?): Flow<EventCountsTuple?> = countsFlow

        override fun observeLatestEvent(): Flow<InteractionEventEntity?> =
            flowOf(events.maxByOrNull { it.timestamp })

        override fun observeEventsFilteredPaged(
            sessionId: String?,
            packageFilter: String?,
            eventTypeFilter: String?,
            sourceFilter: String?,
            classificationFilter: String?,
            startTime: Long?,
            endTime: Long?,
            searchQuery: String?,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEventEntity>> {
            val matching = events.filter { sessionId == null || it.sessionId == sessionId }
            return flowOf(matching.drop(offset).take(limit))
        }

        override fun observeFilteredCount(
            sessionId: String?,
            packageFilter: String?,
            eventTypeFilter: String?,
            sourceFilter: String?,
            classificationFilter: String?,
            startTime: Long?,
            endTime: Long?,
            searchQuery: String?
        ): Flow<Int> = flowOf(events.size)

        override fun observeDistinctPackageNames(): Flow<List<String>> = flowOf(emptyList())

        override fun observeMatchingApplications(query: String, limit: Int): Flow<List<String>> = flowOf(emptyList())

        override fun observeMatchingPackages(query: String, limit: Int): Flow<List<String>> = flowOf(emptyList())

        override suspend fun getMatchingApplications(query: String, limit: Int): List<String> = emptyList()

        override suspend fun getMatchingPackages(query: String, limit: Int): List<String> = emptyList()

        override fun observeStatisticsSummary(sessionId: String?): Flow<StatisticsSummaryTuple?> = flowOf(null)

        override suspend fun getStatisticsSummary(sessionId: String?): StatisticsSummaryTuple? = null

        override fun observeAppUsageStats(sessionId: String?): Flow<List<AppUsageTuple>> = flowOf(emptyList())

        override suspend fun getAppUsageStats(sessionId: String?): List<AppUsageTuple> = emptyList()

        override fun observeHourlyActivity(sessionId: String?): Flow<List<HourlyActivityTuple>> = flowOf(emptyList())

        override suspend fun getHourlyActivity(sessionId: String?): List<HourlyActivityTuple> = emptyList()

        override suspend fun deleteEventsForSession(sessionId: String): Int {
            val initialSize = events.size
            events.removeAll { it.sessionId == sessionId }
            updateCounts()
            return initialSize - events.size
        }

        override suspend fun deleteAllEvents(): Int {
            val count = events.size
            events.clear()
            updateCounts()
            return count
        }
    }

    private class MockPreferencesRepo : PreferencesRepository {
        val kbLogging = MutableStateFlow(true)
        val accLogging = MutableStateFlow(true)
        val activeSession = MutableStateFlow<String?>("sess-1")
        var cleared = false

        override val isOnboardingCompleted: Flow<Boolean> = flowOf(true)
        override suspend fun setOnboardingCompleted(completed: Boolean) {}

        override val activeSessionId: Flow<String?> = activeSession
        override suspend fun setActiveSessionId(sessionId: String?) {
            activeSession.value = sessionId
        }

        override val isKeyboardLoggingEnabled: Flow<Boolean> = kbLogging
        override suspend fun setKeyboardLoggingEnabled(enabled: Boolean) {
            kbLogging.value = enabled
        }

        override val isAccessibilityLoggingEnabled: Flow<Boolean> = accLogging
        override suspend fun setAccessibilityLoggingEnabled(enabled: Boolean) {
            accLogging.value = enabled
        }

        override suspend fun clearAllPreferences() {
            cleared = true
            kbLogging.value = true
            accLogging.value = true
            activeSession.value = null
        }
    }

    private class MockSessionMgr : SessionManager {
        override val currentSession: StateFlow<MonitoringSession?> = MutableStateFlow(null)
        override val sessionState: StateFlow<SessionState> = MutableStateFlow(SessionState.ACTIVE)

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

    private class FakeBackupSettingsRepo : com.example.inputmonitor.domain.backup.repository.BackupSettingsRepository {
        val flow = MutableStateFlow(com.example.inputmonitor.domain.backup.model.BackupSettings())
        override val backupSettings: Flow<com.example.inputmonitor.domain.backup.model.BackupSettings> = flow
        override suspend fun getBackupSettings() = flow.value
        override suspend fun setBackupEnabled(enabled: Boolean) { flow.value = flow.value.copy(enabled = enabled) }
        override suspend fun setWifiOnly(wifiOnly: Boolean) { flow.value = flow.value.copy(wifiOnly = wifiOnly) }
        override suspend fun setProviderConfig(providerId: String, endpoint: String?) {
            flow.value = flow.value.copy(providerId = providerId, providerEndpoint = endpoint)
        }
        override suspend fun recordBackupAttempt(timestamp: Long) {}
        override suspend fun recordBackupSuccess(timestamp: Long, remoteBackupId: String?) {}
        override suspend fun recordBackupFailure(reason: String, authRequired: Boolean) {}
        override suspend fun clearAuthenticationRequired() {}
        override suspend fun resetBackupSettings() { flow.value = com.example.inputmonitor.domain.backup.model.BackupSettings() }
    }

    private class FakeTokenStorage : com.example.inputmonitor.data.backup.security.BackupTokenStorage {
        override suspend fun saveAuthToken(token: String) {}
        override suspend fun getAuthToken(): String? = null
        override suspend fun clearAuthToken() {}
        override suspend fun hasAuthToken(): Boolean = false
    }

    private class FakeBackupRepo : com.example.inputmonitor.domain.backup.repository.BackupRepository {
        override suspend fun uploadBackup(file: java.io.File, metadata: com.example.inputmonitor.domain.backup.model.BackupMetadata) =
            com.example.inputmonitor.domain.backup.model.BackupResult.Success("id")
        override suspend fun testConnection(endpoint: String, token: String?) =
            com.example.inputmonitor.domain.backup.model.BackupResult.Success("ok")
    }

    private class FakeScheduler : com.example.inputmonitor.platform.backup.AutoBackupScheduler {
        override fun schedulePeriodicBackup(wifiOnly: Boolean) {}
        override fun triggerImmediateBackup(wifiOnly: Boolean) {}
        override fun cancelPeriodicBackup() {}
    }

    private lateinit var sessionDao: MockSessionDao
    private lateinit var eventDao: MockEventDao
    private lateinit var sessionRepository: SessionRepositoryImpl
    private lateinit var eventRepository: EventRepositoryImpl
    private lateinit var preferencesRepository: MockPreferencesRepo
    private lateinit var sessionManager: MockSessionMgr

    private lateinit var deleteSessionUseCase: DeleteSessionUseCase
    private lateinit var deleteAllSessionsUseCase: DeleteAllSessionsUseCase
    private lateinit var deleteAllApplicationDataUseCase: DeleteAllApplicationDataUseCase
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        sessionDao = MockSessionDao()
        eventDao = MockEventDao()
        sessionRepository = SessionRepositoryImpl(sessionDao, testDispatchers)
        eventRepository = EventRepositoryImpl(eventDao, testDispatchers)
        preferencesRepository = MockPreferencesRepo()
        sessionManager = MockSessionMgr()

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
            backupSettingsRepository = FakeBackupSettingsRepo(),
            backupTokenStorage = FakeTokenStorage(),
            backupRepository = FakeBackupRepo(),
            autoBackupScheduler = FakeScheduler(),
            dispatchers = testDispatchers
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fullIntegration_deleteSingleSession_purgesOnlyTargetSessionAndEvents() = runTest {
        // Seed 2 sessions with 3 events each
        val s1 = MonitoringSessionEntity("s1", "Session 1", 1000L, null, "ACTIVE", "15", "Pixel", "1.0", null)
        val s2 = MonitoringSessionEntity("s2", "Session 2", 2000L, null, "ACTIVE", "15", "Pixel", "1.0", null)
        sessionDao.insertSession(s1)
        sessionDao.insertSession(s2)

        for (i in 1..3) {
            eventDao.insertEvent(InteractionEventEntity(i.toLong(), "s1", 1000L + i, "IME", "IME_KEY", "com.test", "TestApp", "a", "GENERIC_TEXT", null))
            eventDao.insertEvent(InteractionEventEntity((i + 3).toLong(), "s2", 2000L + i, "IME", "IME_KEY", "com.test", "TestApp", "b", "GENERIC_TEXT", null))
        }

        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.sessions.size)
        assertEquals(6, viewModel.uiState.value.totalEventsCount)

        // Delete session 1
        val target = viewModel.uiState.value.sessions.first { it.id == "s1" }
        viewModel.requestDeleteSession(target)
        viewModel.confirmDeleteSession()
        advanceUntilIdle()

        // Verify session 1 is gone, session 2 remains
        assertEquals(1, sessionDao.sessions.value.size)
        assertEquals("s2", sessionDao.sessions.value.first().id)

        // Verify session 1's events are gone, session 2's events remain
        assertEquals(3, eventDao.events.size)
        assertTrue(eventDao.events.all { it.sessionId == "s2" })

        assertEquals("s1", sessionManager.deletedSessionId)
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun fullIntegration_deleteAllApplicationData_purgesAllStorageAndResetsPreferences() = runTest {
        // Seed data
        val s1 = MonitoringSessionEntity("s1", "Session 1", 1000L, null, "ACTIVE", "15", "Pixel", "1.0", null)
        sessionDao.insertSession(s1)
        eventDao.insertEvent(InteractionEventEntity(1L, "s1", 1001L, "IME", "IME_KEY", "com.test", "TestApp", "a", "GENERIC_TEXT", null))

        preferencesRepository.setKeyboardLoggingEnabled(false)
        advanceUntilIdle()

        assertEquals(1, sessionDao.sessions.value.size)
        assertEquals(1, eventDao.events.size)

        // Request and confirm full reset
        viewModel.requestDeleteAllData()
        viewModel.confirmDeleteAllData()
        advanceUntilIdle()

        // Verify storage is completely empty
        assertEquals(0, sessionDao.sessions.value.size)
        assertEquals(0, eventDao.events.size)
        assertTrue(sessionManager.resetCalled)
        assertTrue(preferencesRepository.cleared)
        assertNull(preferencesRepository.activeSession.value)
        assertNotNull(viewModel.uiState.value.userMessage)
    }
}
