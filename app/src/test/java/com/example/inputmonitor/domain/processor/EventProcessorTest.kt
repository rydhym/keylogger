package com.example.inputmonitor.domain.processor

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.context.ApplicationContextInfo
import com.example.inputmonitor.domain.context.ContextResolver
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.privacy.PrivacyFilterImpl
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.session.SessionGate
import com.example.inputmonitor.domain.session.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [EventProcessorImpl] (Architecture.md §37, §38, SECURITY.md §4, TESTING.md §4).
 */
class EventProcessorTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeSessionGate(var canProcess: Boolean = true) : SessionGate {
        override fun canProcessEvents(): Boolean = canProcess
        override fun shouldProcess(event: RawInteractionEvent): Boolean = canProcess
    }

    private class FakeSessionManager(
        initialSession: MonitoringSession? = null,
        initialState: SessionState = SessionState.ACTIVE
    ) : SessionManager {
        private val _currentSession = MutableStateFlow(initialSession)
        override val currentSession: StateFlow<MonitoringSession?> = _currentSession

        private val _sessionState = MutableStateFlow(initialState)
        override val sessionState: StateFlow<SessionState> = _sessionState

        fun setSession(session: MonitoringSession?, state: SessionState) {
            _currentSession.value = session
            _sessionState.value = state
        }

        override suspend fun startSession(name: String, notes: String?): MonitoringSession = throw NotImplementedError()
        override suspend fun pauseSession(): Boolean = throw NotImplementedError()
        override suspend fun resumeSession(): Boolean = throw NotImplementedError()
        override suspend fun stopSession(): Boolean = throw NotImplementedError()
        override suspend fun restoreSessionIfActive(): MonitoringSession? = _currentSession.value
    }

    private class FakeContextResolver : ContextResolver {
        var labelToReturn: String = "Test Application"

        override suspend fun resolve(packageName: String?): ApplicationContextInfo {
            return ApplicationContextInfo(
                packageName = packageName,
                applicationLabel = if (packageName != null) labelToReturn else null
            )
        }
    }

    private class FakeEventRepository : EventRepository {
        val insertedEvents = mutableListOf<InteractionEvent>()
        var shouldThrow: Boolean = false

        override suspend fun insertEvent(event: InteractionEvent): Long {
            if (shouldThrow) throw IllegalStateException("Simulated SQLite Disk Error")
            insertedEvents.add(event)
            return insertedEvents.size.toLong()
        }

        override suspend fun insertEvents(events: List<InteractionEvent>): List<Long> {
            if (shouldThrow) throw IllegalStateException("Simulated SQLite Disk Error")
            insertedEvents.addAll(events)
            return events.mapIndexed { index, _ -> (insertedEvents.size - events.size + index + 1).toLong() }
        }

        override suspend fun getEventById(id: Long): InteractionEvent? = null
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEvent> = emptyList()
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent> = emptyList()
        override suspend fun getEventsFiltered(sessionId: String, packageFilter: String?, eventTypeFilter: String?, startTime: Long?, endTime: Long?): List<InteractionEvent> = emptyList()
        override suspend fun getEventCountForSession(sessionId: String): Int = insertedEvents.size
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(insertedEvents.size)
        override fun observeEventCounts(sessionId: String?): Flow<com.example.inputmonitor.domain.model.EventCounts> = flowOf(com.example.inputmonitor.domain.model.EventCounts(total = insertedEvents.size.toLong()))
        override fun observeLatestEvent(): Flow<InteractionEvent?> = flowOf(insertedEvents.lastOrNull())
        override fun observeEventsPaged(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> = flowOf(insertedEvents)
        override fun observeFilteredCount(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria
        ): Flow<Int> = flowOf(insertedEvents.size)
        override fun observeDistinctPackageNames(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun deleteEventsForSession(sessionId: String): Boolean = true
        override suspend fun deleteAllEvents(): Boolean = true
    }

    private lateinit var sessionGate: FakeSessionGate
    private lateinit var sessionManager: FakeSessionManager
    private lateinit var privacyFilter: PrivacyFilterImpl
    private lateinit var contextResolver: FakeContextResolver
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var eventProcessor: EventProcessorImpl

    private val sampleSession = MonitoringSession(
        id = "session-12345",
        name = "Test Session",
        startTime = 1700000000000L,
        state = SessionState.ACTIVE,
        androidVersion = "14",
        deviceModel = "Pixel 8",
        appVersion = "1.0.0"
    )

    @Before
    fun setUp() {
        sessionGate = FakeSessionGate(canProcess = true)
        sessionManager = FakeSessionManager(initialSession = sampleSession, initialState = SessionState.ACTIVE)
        privacyFilter = PrivacyFilterImpl()
        contextResolver = FakeContextResolver()
        eventRepository = FakeEventRepository()

        eventProcessor = EventProcessorImpl(
            sessionGate = sessionGate,
            sessionManager = sessionManager,
            privacyFilter = privacyFilter,
            contextResolver = contextResolver,
            eventRepository = eventRepository,
            dispatchers = testDispatchers
        )
    }

    @Test
    fun process_whenSessionGateRejects_dropsEventImmediately() {
        runBlocking {
            sessionGate.canProcess = false

            val event = RawInteractionEvent(
                timestamp = 1700000001000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.notes",
                candidateData = "hello"
            )

            val processed = eventProcessor.process(event)

            assertFalse(processed)
            assertEquals(0, eventRepository.insertedEvents.size)
        }
    }

    @Test
    fun process_whenNoActiveSessionPresent_dropsEvent() {
        runBlocking {
            sessionManager.setSession(null, SessionState.INACTIVE)

            val event = RawInteractionEvent(
                timestamp = 1700000001000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.notes",
                candidateData = "hello"
            )

            val processed = eventProcessor.process(event)

            assertFalse(processed)
            assertEquals(0, eventRepository.insertedEvents.size)
        }
    }

    @Test
    fun process_normalTextEvent_resolvesContextAndPersistsSuccessfully() {
        runBlocking {
            contextResolver.labelToReturn = "Notes App"

            val event = RawInteractionEvent(
                timestamp = 1700000001000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.notes",
                candidateData = "Hello World",
                inputClassification = InputClassification.NORMAL_TEXT,
                metadata = mapOf("fieldCategory" to "NORMAL_TEXT")
            )

            val processed = eventProcessor.process(event)

            assertTrue(processed)
            assertEquals(1, eventRepository.insertedEvents.size)

            val persisted = eventRepository.insertedEvents.first()
            assertEquals("session-12345", persisted.sessionId)
            assertEquals(1700000001000L, persisted.timestamp)
            assertEquals(EventSource.KEYBOARD, persisted.source)
            assertEquals(EventType.TEXT_COMMITTED, persisted.eventType)
            assertEquals("com.example.notes", persisted.packageName)
            assertEquals("Notes App", persisted.applicationName)
            assertEquals("Hello World", persisted.eventData)
            assertEquals(InputClassification.NORMAL_TEXT, persisted.inputClassification)
            assertTrue(persisted.metadata!!.contains("\"fieldCategory\":\"NORMAL_TEXT\""))
        }
    }

    @Test
    fun process_sensitivePasswordEvent_nullifiesDataAndSetsSensitiveEventType() {
        runBlocking {
            val event = RawInteractionEvent(
                timestamp = 1700000001000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.bank",
                candidateData = "SuperSecretPassword123!",
                inputClassification = InputClassification.SENSITIVE_PASSWORD,
                metadata = mapOf("fieldCategory" to "PASSWORD")
            )

            val processed = eventProcessor.process(event)

            assertTrue(processed)
            assertEquals(1, eventRepository.insertedEvents.size)

            val persisted = eventRepository.insertedEvents.first()
            assertNull("Sensitive event must have null eventData in repository", persisted.eventData)
            assertEquals(EventType.SENSITIVE_INPUT_DETECTED, persisted.eventType)
            assertEquals(InputClassification.SENSITIVE_PASSWORD, persisted.inputClassification)
        }
    }

    @Test
    fun process_whenRepositoryThrowsException_handlesGracefullyAndReturnsFalse() {
        runBlocking {
            eventRepository.shouldThrow = true

            val event = RawInteractionEvent(
                timestamp = 1700000001000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.notes",
                candidateData = "a"
            )

            val processed = eventProcessor.process(event)

            assertFalse(processed)
        }
    }
}
