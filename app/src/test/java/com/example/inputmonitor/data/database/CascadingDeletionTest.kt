package com.example.inputmonitor.data.database

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verification test for cascading event cleanup upon session deletion (SRS.md §55, IMPLEMENTATION_PLAN.md §55).
 * Tests that deleting a session cascades to clean up its associated interaction events,
 * and leaves unrelated sessions and events completely isolated and intact.
 */
class CascadingDeletionTest {

    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private class CascadingStorageEngine {
        val sessions = mutableMapOf<String, MonitoringSessionEntity>()
        val events = mutableMapOf<Long, InteractionEventEntity>()

        fun insertSession(session: MonitoringSessionEntity) {
            sessions[session.id] = session
        }

        fun insertEvent(event: InteractionEventEntity) {
            // Foreign key validation: sessionId must exist
            if (!sessions.containsKey(event.sessionId)) {
                throw IllegalStateException("Foreign key violation: session ${event.sessionId} does not exist")
            }
            events[event.id] = event
        }

        fun deleteSession(sessionId: String): Int {
            val removed = sessions.remove(sessionId)
            if (removed != null) {
                // SQLite ON DELETE CASCADE behavior
                events.values.filter { it.sessionId == sessionId }.forEach {
                    events.remove(it.id)
                }
                return 1
            }
            return 0
        }

        fun deleteAllSessions(): Int {
            val count = sessions.size
            sessions.clear()
            events.clear()
            return count
        }

        fun deleteEventsForSession(sessionId: String): Int {
            val toRemove = events.values.filter { it.sessionId == sessionId }.map { it.id }
            toRemove.forEach { events.remove(it) }
            return toRemove.size
        }

        fun deleteAllEvents(): Int {
            val count = events.size
            events.clear()
            return count
        }
    }

    private val storage = CascadingStorageEngine()

    private val sessionDao = object : MonitoringSessionDao {
        override suspend fun insertSession(session: MonitoringSessionEntity) {
            storage.insertSession(session)
        }

        override suspend fun updateSession(session: MonitoringSessionEntity) {
            storage.insertSession(session)
        }

        override suspend fun getSessionById(id: String): MonitoringSessionEntity? = storage.sessions[id]
        override fun observeSessionById(id: String): Flow<MonitoringSessionEntity?> = flowOf(storage.sessions[id])
        override suspend fun getActiveSession(): MonitoringSessionEntity? = storage.sessions.values.find { it.state == "ACTIVE" }
        override fun observeActiveSession(): Flow<MonitoringSessionEntity?> = flowOf(storage.sessions.values.find { it.state == "ACTIVE" })
        override fun observeAllSessions(): Flow<List<MonitoringSessionEntity>> = flowOf(storage.sessions.values.toList())
        override suspend fun getAllSessions(): List<MonitoringSessionEntity> = storage.sessions.values.toList()
        override suspend fun finishSession(id: String, endTime: Long, state: String): Int {
            val s = storage.sessions[id] ?: return 0
            storage.sessions[id] = s.copy(endTime = endTime, state = state)
            return 1
        }
        override suspend fun deleteSessionById(id: String): Int = storage.deleteSession(id)
        override suspend fun deleteAllSessions(): Int = storage.deleteAllSessions()
    }

    private val eventDao = object : InteractionEventDao {
        override suspend fun insertEvent(event: InteractionEventEntity): Long {
            storage.insertEvent(event)
            return event.id
        }

        override suspend fun insertEvents(eventsList: List<InteractionEventEntity>): List<Long> {
            eventsList.forEach { storage.insertEvent(it) }
            return eventsList.map { it.id }
        }

        override suspend fun getEventById(id: Long): InteractionEventEntity? = storage.events[id]
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEventEntity>> =
            flowOf(storage.events.values.filter { it.sessionId == sessionId })
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEventEntity> =
            storage.events.values.filter { it.sessionId == sessionId }
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEventEntity> =
            storage.events.values.filter { it.sessionId == sessionId }.drop(offset).take(limit)
        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEventEntity> = storage.events.values.toList()
        override suspend fun getEventCountForSession(sessionId: String): Int =
            storage.events.values.count { it.sessionId == sessionId }
        override fun observeEventCountForSession(sessionId: String): Flow<Int> =
            flowOf(storage.events.values.count { it.sessionId == sessionId })
        override fun observeEventCounts(sessionId: String?): Flow<EventCountsTuple?> =
            flowOf(EventCountsTuple(total = storage.events.size.toLong()))
        override fun observeLatestEvent(): Flow<InteractionEventEntity?> =
            flowOf(storage.events.values.maxByOrNull { it.timestamp })
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
        ): Flow<List<InteractionEventEntity>> = flowOf(storage.events.values.toList())
        override fun observeFilteredCount(
            sessionId: String?,
            packageFilter: String?,
            eventTypeFilter: String?,
            sourceFilter: String?,
            classificationFilter: String?,
            startTime: Long?,
            endTime: Long?,
            searchQuery: String?
        ): Flow<Int> = flowOf(storage.events.size)
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
        override suspend fun deleteEventsForSession(sessionId: String): Int =
            storage.deleteEventsForSession(sessionId)
        override suspend fun deleteAllEvents(): Int =
            storage.deleteAllEvents()
    }

    private val preferencesRepository = object : PreferencesRepository {
        val preferences = mutableMapOf<String, Any>()
        override val isOnboardingCompleted: Flow<Boolean> = flowOf(true)
        override suspend fun setOnboardingCompleted(completed: Boolean) { preferences["onboarding"] = completed }
        override val activeSessionId: Flow<String?> = flowOf(preferences["active_session"] as? String)
        override suspend fun setActiveSessionId(sessionId: String?) {
            if (sessionId != null) preferences["active_session"] = sessionId else preferences.remove("active_session")
        }
        override val isKeyboardLoggingEnabled: Flow<Boolean> = flowOf(preferences["keyboard"] as? Boolean ?: true)
        override suspend fun setKeyboardLoggingEnabled(enabled: Boolean) { preferences["keyboard"] = enabled }
        override val isAccessibilityLoggingEnabled: Flow<Boolean> = flowOf(preferences["acc"] as? Boolean ?: true)
        override suspend fun setAccessibilityLoggingEnabled(enabled: Boolean) { preferences["acc"] = enabled }
        override suspend fun clearAllPreferences() { preferences.clear() }
    }

    private val sessionManager = object : SessionManager {
        val state = MutableStateFlow(SessionState.ACTIVE)
        val current = MutableStateFlow<MonitoringSession?>(null)
        override val currentSession: StateFlow<MonitoringSession?> = current
        override val sessionState: StateFlow<SessionState> = state
        override suspend fun startSession(name: String, notes: String?): MonitoringSession = throw UnsupportedOperationException()
        override suspend fun pauseSession(): Boolean = true
        override suspend fun resumeSession(): Boolean = true
        override suspend fun stopSession(): Boolean = true
        override suspend fun restoreSessionIfActive(): MonitoringSession? = null
        override suspend fun onSessionDeleted(sessionId: String) {
            if (current.value?.id == sessionId) {
                current.value = null
                state.value = SessionState.INACTIVE
            }
        }
        override suspend fun resetSessionState() {
            current.value = null
            state.value = SessionState.INACTIVE
        }
    }

    private lateinit var sessionRepository: SessionRepositoryImpl
    private lateinit var eventRepository: EventRepositoryImpl
    private lateinit var deleteSessionUseCase: DeleteSessionUseCase
    private lateinit var deleteAllSessionsUseCase: DeleteAllSessionsUseCase
    private lateinit var deleteAllApplicationDataUseCase: DeleteAllApplicationDataUseCase

    @Before
    fun setUp() {
        sessionRepository = SessionRepositoryImpl(sessionDao, testDispatchers)
        eventRepository = EventRepositoryImpl(eventDao, testDispatchers)

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
    }

    @Test
    fun deleteSession_cascadesEventsForTargetSessionOnly() = runBlocking {
        // Arrange: 2 sessions
        val s1 = MonitoringSessionEntity("sess-A", "Session A", 1000L, null, "ACTIVE", "15", "Pixel", "1.0", null)
        val s2 = MonitoringSessionEntity("sess-B", "Session B", 2000L, null, "STOPPED", "15", "Pixel", "1.0", null)
        storage.insertSession(s1)
        storage.insertSession(s2)

        // Events for A (IDs 1, 2, 3)
        storage.insertEvent(InteractionEventEntity(1L, "sess-A", 1001L, "IME", "IME_KEY", "app.a", "App A", "x", "GENERIC_TEXT", null))
        storage.insertEvent(InteractionEventEntity(2L, "sess-A", 1002L, "IME", "IME_KEY", "app.a", "App A", "y", "GENERIC_TEXT", null))
        storage.insertEvent(InteractionEventEntity(3L, "sess-A", 1003L, "IME", "IME_KEY", "app.a", "App A", "z", "GENERIC_TEXT", null))

        // Events for B (IDs 4, 5)
        storage.insertEvent(InteractionEventEntity(4L, "sess-B", 2001L, "ACCESSIBILITY", "VIEW_CLICKED", "app.b", "App B", null, "NAVIGATION", null))
        storage.insertEvent(InteractionEventEntity(5L, "sess-B", 2002L, "ACCESSIBILITY", "VIEW_CLICKED", "app.b", "App B", null, "NAVIGATION", null))

        assertEquals(2, storage.sessions.size)
        assertEquals(5, storage.events.size)

        // Act: Delete Session A
        val result = deleteSessionUseCase("sess-A")
        assertTrue(result.isSuccess)

        // Assert:
        // Session A deleted, Session B still exists
        assertFalse(storage.sessions.containsKey("sess-A"))
        assertTrue(storage.sessions.containsKey("sess-B"))

        // Events 1, 2, 3 deleted; Events 4, 5 still exist
        assertFalse(storage.events.containsKey(1L))
        assertFalse(storage.events.containsKey(2L))
        assertFalse(storage.events.containsKey(3L))
        assertTrue(storage.events.containsKey(4L))
        assertTrue(storage.events.containsKey(5L))
        assertEquals(2, storage.events.size)
    }

    @Test
    fun deleteAllSessions_purgesAllSessionsAndCascadingEvents() = runBlocking {
        val s1 = MonitoringSessionEntity("sess-A", "Session A", 1000L, null, "ACTIVE", "15", "Pixel", "1.0", null)
        storage.insertSession(s1)
        storage.insertEvent(InteractionEventEntity(1L, "sess-A", 1001L, "IME", "IME_KEY", "app.a", "App A", "x", "GENERIC_TEXT", null))

        val result = deleteAllSessionsUseCase()
        assertTrue(result.isSuccess)
        assertEquals(0, storage.sessions.size)
        assertEquals(0, storage.events.size)
    }

    @Test
    fun deleteAllApplicationData_purgesAllAndResetsPreferences() = runBlocking {
        val s1 = MonitoringSessionEntity("sess-A", "Session A", 1000L, null, "ACTIVE", "15", "Pixel", "1.0", null)
        storage.insertSession(s1)
        storage.insertEvent(InteractionEventEntity(1L, "sess-A", 1001L, "IME", "IME_KEY", "app.a", "App A", "x", "GENERIC_TEXT", null))
        preferencesRepository.setKeyboardLoggingEnabled(false)

        val result = deleteAllApplicationDataUseCase()
        assertTrue(result.isSuccess)
        assertEquals(0, storage.sessions.size)
        assertEquals(0, storage.events.size)
        assertTrue(preferencesRepository.preferences.isEmpty())
    }
}
