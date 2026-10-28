package com.example.inputmonitor.domain.processor

import android.text.InputType
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
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
import com.example.inputmonitor.platform.accessibility.AccessibilityConfig
import com.example.inputmonitor.platform.accessibility.AccessibilityEventFilter
import com.example.inputmonitor.platform.accessibility.AccessibilityEventFilterImpl
import com.example.inputmonitor.platform.accessibility.AccessibilityEventMapper
import com.example.inputmonitor.platform.accessibility.AccessibilityEventMapperImpl
import com.example.inputmonitor.platform.accessibility.AccessibilityEventPublisher
import com.example.inputmonitor.platform.accessibility.AccessibilityEventPublisherImpl
import com.example.inputmonitor.platform.accessibility.SimpleAccessibilityEventInfo
import com.example.inputmonitor.platform.ime.CustomKeyboardService
import com.example.inputmonitor.platform.ime.ImeEventPublisher
import com.example.inputmonitor.platform.ime.ImeEventPublisherImpl
import com.example.inputmonitor.platform.ime.KeyAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 9 Unified Event Processing & Integration Test Suite (IMPLEMENTATION_PLAN.md §47, Architecture.md §37).
 *
 * Verifies end-to-end integration across both IME and Accessibility event ingestion pipelines:
 * 1. Shared Session Gate enforcement across both sources
 * 2. Shared Privacy Filter sanitization & zero sensitive persistence invariant
 * 3. Shared Context Resolver with caching
 * 4. Concurrent event interleaving (UI interactions followed immediately by typing)
 * 5. Self-monitoring loop suppression
 * 6. Scroll storm rate limiting alongside concurrent typing
 * 7. Failure tolerance under simulated repository exceptions
 */
class UnifiedEventProcessingIntegrationTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class IntegrationSessionManager(
        initialSession: MonitoringSession? = null,
        initialState: SessionState = SessionState.ACTIVE
    ) : SessionManager {
        private val _currentSession = MutableStateFlow(initialSession)
        override val currentSession: StateFlow<MonitoringSession?> = _currentSession

        private val _sessionState = MutableStateFlow(initialState)
        override val sessionState: StateFlow<SessionState> = _sessionState

        fun updateState(state: SessionState) {
            _sessionState.value = state
            if (state == SessionState.STOPPED || state == SessionState.INACTIVE) {
                _currentSession.value = null
            } else if (_currentSession.value == null) {
                _currentSession.value = MonitoringSession(
                    id = "session-unified-test",
                    name = "Unified Test Session",
                    startTime = 1700000000000L,
                    state = state,
                    androidVersion = "14",
                    deviceModel = "Pixel 8",
                    appVersion = "1.0.0"
                )
            } else {
                _currentSession.value = _currentSession.value?.copy(state = state)
            }
        }

        override suspend fun startSession(name: String, notes: String?): MonitoringSession {
            val session = MonitoringSession(
                id = "session-unified-test",
                name = name,
                startTime = System.currentTimeMillis(),
                state = SessionState.ACTIVE,
                androidVersion = "14",
                deviceModel = "Pixel 8",
                appVersion = "1.0.0"
            )
            _currentSession.value = session
            _sessionState.value = SessionState.ACTIVE
            return session
        }

        override suspend fun pauseSession(): Boolean {
            updateState(SessionState.PAUSED)
            return true
        }

        override suspend fun resumeSession(): Boolean {
            updateState(SessionState.ACTIVE)
            return true
        }

        override suspend fun stopSession(): Boolean {
            updateState(SessionState.STOPPED)
            return true
        }

        override suspend fun restoreSessionIfActive(): MonitoringSession? = _currentSession.value
    }

    private class IntegrationSessionGate(
        private val sessionManager: SessionManager
    ) : SessionGate {
        override fun canProcessEvents(): Boolean {
            return sessionManager.sessionState.value == SessionState.ACTIVE &&
                sessionManager.currentSession.value != null
        }

        override fun shouldProcess(event: RawInteractionEvent): Boolean {
            return canProcessEvents()
        }
    }

    private class IntegrationContextResolver : ContextResolver {
        val lookupCount = AtomicInteger(0)
        private val cache = ConcurrentHashMap<String, String>()

        fun registerApp(pkg: String, label: String) {
            cache[pkg] = label
        }

        override suspend fun resolve(packageName: String?): ApplicationContextInfo {
            if (packageName.isNullOrBlank()) return ApplicationContextInfo(null, null)
            lookupCount.incrementAndGet()
            val label = cache[packageName] ?: packageName
            return ApplicationContextInfo(packageName, label)
        }
    }

    private class IntegrationEventRepository : EventRepository {
        val events = mutableListOf<InteractionEvent>()
        var failOnInsert: Boolean = false

        override suspend fun insertEvent(event: InteractionEvent): Long {
            if (failOnInsert) throw IllegalStateException("Simulated SQLite Disk I/O Failure")
            // Apply defense-in-depth sanitization identical to EventRepositoryImpl
            val safe = if (event.inputClassification.isSensitive && event.eventData != null) {
                event.copy(eventData = null)
            } else {
                event
            }
            val assignedId = (events.size + 1).toLong()
            events.add(safe.copy(id = assignedId))
            return assignedId
        }

        override suspend fun insertEvents(eventsList: List<InteractionEvent>): List<Long> {
            return eventsList.map { insertEvent(it) }
        }

        override suspend fun getEventById(id: Long): InteractionEvent? = events.firstOrNull { it.id == id }
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>> = flowOf(events.filter { it.sessionId == sessionId })
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEvent> = events.filter { it.sessionId == sessionId }
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent> = events.drop(offset).take(limit)
        override suspend fun getEventsFiltered(sessionId: String, packageFilter: String?, eventTypeFilter: String?, startTime: Long?, endTime: Long?): List<InteractionEvent> = events
        override suspend fun getEventCountForSession(sessionId: String): Int = events.filter { it.sessionId == sessionId }.size
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(events.filter { it.sessionId == sessionId }.size)
        override fun observeEventCounts(sessionId: String?): Flow<com.example.inputmonitor.domain.model.EventCounts> {
            val matching = if (sessionId != null) events.filter { it.sessionId == sessionId } else events
            return flowOf(
                com.example.inputmonitor.domain.model.EventCounts(
                    total = matching.size.toLong(),
                    keyboard = matching.count { it.source == EventSource.KEYBOARD }.toLong(),
                    accessibility = matching.count { it.source == EventSource.ACCESSIBILITY }.toLong(),
                    clicks = matching.count { it.eventType == EventType.VIEW_CLICKED }.toLong(),
                    focus = matching.count { it.eventType == EventType.VIEW_FOCUSED }.toLong(),
                    scrolls = matching.count { it.eventType == EventType.VIEW_SCROLLED }.toLong(),
                    windowChanges = matching.count { it.eventType == EventType.WINDOW_STATE_CHANGED || it.eventType == EventType.WINDOWS_CHANGED }.toLong()
                )
            )
        }
        override fun observeLatestEvent(): Flow<InteractionEvent?> = flowOf(events.maxByOrNull { it.timestamp })
        override fun observeEventsPaged(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> = flowOf(events)
        override fun observeFilteredCount(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria
        ): Flow<Int> = flowOf(events.size)
        override fun observeDistinctPackageNames(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun deleteEventsForSession(sessionId: String): Boolean {
            events.removeAll { it.sessionId == sessionId }
            return true
        }
        override suspend fun deleteAllEvents(): Boolean {
            events.clear()
            return true
        }
    }

    private lateinit var sessionManager: IntegrationSessionManager
    private lateinit var sessionGate: IntegrationSessionGate
    private lateinit var privacyFilter: PrivacyFilterImpl
    private lateinit var contextResolver: IntegrationContextResolver
    private lateinit var eventRepository: IntegrationEventRepository
    private lateinit var eventProcessor: EventProcessorImpl

    private lateinit var imePublisher: ImeEventPublisher
    private lateinit var accessibilityFilter: AccessibilityEventFilter
    private lateinit var accessibilityMapper: AccessibilityEventMapper
    private lateinit var accessibilityPublisher: AccessibilityEventPublisher

    private val ownPackage = "com.example.inputmonitor"
    private val targetAppPackage = "com.example.notes"

    @Before
    fun setUp() {
        sessionManager = IntegrationSessionManager()
        sessionManager.updateState(SessionState.ACTIVE)

        sessionGate = IntegrationSessionGate(sessionManager)
        privacyFilter = PrivacyFilterImpl()
        contextResolver = IntegrationContextResolver().apply {
            registerApp(targetAppPackage, "Notes Application")
            registerApp("com.example.bank", "Secure Bank")
        }
        eventRepository = IntegrationEventRepository()

        eventProcessor = EventProcessorImpl(
            sessionGate = sessionGate,
            sessionManager = sessionManager,
            privacyFilter = privacyFilter,
            contextResolver = contextResolver,
            eventRepository = eventRepository,
            dispatchers = testDispatchers
        )

        val synchronousScope = CoroutineScope(testDispatcher)
        imePublisher = ImeEventPublisherImpl(eventProcessor, testDispatchers, synchronousScope)

        accessibilityFilter = AccessibilityEventFilterImpl(
            ownPackageName = ownPackage,
            config = AccessibilityConfig(minScrollIntervalMs = 100L)
        )
        accessibilityMapper = AccessibilityEventMapperImpl()
        accessibilityPublisher = AccessibilityEventPublisherImpl(eventProcessor, testDispatchers, synchronousScope)
    }

    @Test
    fun unifiedPipeline_concurrentInterleaving_persistsEventsWithCorrectContextAndOrdering() {
        runBlocking {
            // Step 1: User focuses an input field in Notes app (Accessibility VIEW_FOCUSED)
            val focusEvent = SimpleAccessibilityEventInfo(
                eventType = AccessibilityEvent.TYPE_VIEW_FOCUSED,
                packageName = targetAppPackage,
                className = "android.widget.EditText",
                contentDescription = null,
                isPassword = false,
                itemCount = 0,
                eventTime = 1000L
            )
            assertTrue(accessibilityFilter.shouldProcess(focusEvent))
            val rawAccessibilityEvent = accessibilityMapper.map(focusEvent)
            accessibilityPublisher.publish(rawAccessibilityEvent)

            // Step 2: User types "Hi" on the custom keyboard (IME TEXT_COMMITTED)
            val imeEventH = RawInteractionEvent(
                timestamp = 1050L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = targetAppPackage,
                candidateData = "H",
                inputClassification = InputClassification.NORMAL_TEXT
            )
            val imeEventI = RawInteractionEvent(
                timestamp = 1100L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = targetAppPackage,
                candidateData = "i",
                inputClassification = InputClassification.NORMAL_TEXT
            )
            imePublisher.publish(imeEventH)
            imePublisher.publish(imeEventI)

            // Step 3: User clicks "Done" button in Notes app (Accessibility VIEW_CLICKED)
            val clickEvent = SimpleAccessibilityEventInfo(
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                packageName = targetAppPackage,
                className = "android.widget.Button",
                contentDescription = "Save Note",
                isPassword = false,
                itemCount = 0,
                eventTime = 1200L
            )
            assertTrue(accessibilityFilter.shouldProcess(clickEvent))
            val rawClickEvent = accessibilityMapper.map(clickEvent)
            accessibilityPublisher.publish(rawClickEvent)

            // Verify all 4 interleaved events were persisted correctly in the unified repository
            assertEquals(4, eventRepository.events.size)

            val event1 = eventRepository.events[0]
            assertEquals(EventSource.ACCESSIBILITY, event1.source)
            assertEquals(EventType.VIEW_FOCUSED, event1.eventType)
            assertEquals("Notes Application", event1.applicationName)
            assertEquals("session-unified-test", event1.sessionId)

            val event2 = eventRepository.events[1]
            assertEquals(EventSource.KEYBOARD, event2.source)
            assertEquals(EventType.TEXT_COMMITTED, event2.eventType)
            assertEquals("H", event2.eventData)
            assertEquals("Notes Application", event2.applicationName)

            val event3 = eventRepository.events[2]
            assertEquals(EventSource.KEYBOARD, event3.source)
            assertEquals(EventType.TEXT_COMMITTED, event3.eventType)
            assertEquals("i", event3.eventData)

            val event4 = eventRepository.events[3]
            assertEquals(EventSource.ACCESSIBILITY, event4.source)
            assertEquals(EventType.VIEW_CLICKED, event4.eventType)
            assertEquals("Save Note", event4.eventData)
            assertEquals("Notes Application", event4.applicationName)
        }
    }

    @Test
    fun unifiedPipeline_sessionStateTransitions_gateBothSourcesUniformly() {
        runBlocking {
            // 1. Session ACTIVE: both sources persist
            val imeEventActive = RawInteractionEvent(
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = targetAppPackage,
                candidateData = "a",
                inputClassification = InputClassification.NORMAL_TEXT
            )
            imePublisher.publish(imeEventActive)

            val accessEventActive = SimpleAccessibilityEventInfo(
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                packageName = targetAppPackage,
                className = "android.widget.Button",
                eventTime = 1050L
            )
            accessibilityPublisher.publish(accessibilityMapper.map(accessEventActive))

            assertEquals(2, eventRepository.events.size)

            // 2. Pause session: events from BOTH sources must be dropped
            sessionManager.pauseSession()

            val imeEventPaused = RawInteractionEvent(
                timestamp = 2000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = targetAppPackage,
                candidateData = "b"
            )
            imePublisher.publish(imeEventPaused)

            val accessEventPaused = SimpleAccessibilityEventInfo(
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                packageName = targetAppPackage,
                eventTime = 2050L
            )
            accessibilityPublisher.publish(accessibilityMapper.map(accessEventPaused))

            // Count must still be 2 (both dropped)
            assertEquals(2, eventRepository.events.size)

            // 3. Resume session to ACTIVE: events from BOTH sources persist again
            sessionManager.resumeSession()

            val imeEventResumed = RawInteractionEvent(
                timestamp = 3000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = targetAppPackage,
                candidateData = "c",
                inputClassification = InputClassification.NORMAL_TEXT
            )
            imePublisher.publish(imeEventResumed)

            val accessEventResumed = SimpleAccessibilityEventInfo(
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                packageName = targetAppPackage,
                eventTime = 3050L
            )
            accessibilityPublisher.publish(accessibilityMapper.map(accessEventResumed))

            assertEquals(4, eventRepository.events.size)

            // 4. Stop session: events from BOTH sources are permanently dropped
            sessionManager.stopSession()

            val imeEventStopped = RawInteractionEvent(
                timestamp = 4000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = targetAppPackage,
                candidateData = "d"
            )
            imePublisher.publish(imeEventStopped)

            val accessEventStopped = SimpleAccessibilityEventInfo(
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                packageName = targetAppPackage,
                eventTime = 4050L
            )
            accessibilityPublisher.publish(accessibilityMapper.map(accessEventStopped))

            assertEquals(4, eventRepository.events.size)
        }
    }

    @Test
    fun unifiedPipeline_privacyInvariant_guaranteesZeroSensitivePersistenceFromEitherSource() {
        runBlocking {
            // Case A: Accessibility detects a sensitive password field
            val sensitiveAccessEvent = SimpleAccessibilityEventInfo(
                eventType = AccessibilityEvent.TYPE_VIEW_FOCUSED,
                packageName = "com.example.bank",
                className = "android.widget.PasswordField",
                isPassword = true,
                contentDescription = "Password input field",
                eventTime = 1000L
            )
            val rawAccessEvent = accessibilityMapper.map(sensitiveAccessEvent)
            assertEquals(InputClassification.SENSITIVE_PASSWORD, rawAccessEvent.inputClassification)
            assertNull("Accessibility mapper must nullify candidateData for passwords", rawAccessEvent.candidateData)

            accessibilityPublisher.publish(rawAccessEvent)

            // Case B: IME receives password typing (classified as SENSITIVE_PASSWORD)
            val sensitiveImeEvent = RawInteractionEvent(
                timestamp = 1100L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.bank",
                candidateData = "SuperSecretPin123",
                inputClassification = InputClassification.SENSITIVE_PASSWORD,
                metadata = mapOf("fieldCategory" to "PASSWORD")
            )
            imePublisher.publish(sensitiveImeEvent)

            // Verify persistent storage in repository
            assertEquals(2, eventRepository.events.size)

            val persistedAccess = eventRepository.events[0]
            assertEquals(EventSource.ACCESSIBILITY, persistedAccess.source)
            assertEquals(EventType.SENSITIVE_INPUT_DETECTED, persistedAccess.eventType)
            assertEquals(InputClassification.SENSITIVE_PASSWORD, persistedAccess.inputClassification)
            assertNull("Persistent accessibility password event must have null eventData", persistedAccess.eventData)

            val persistedIme = eventRepository.events[1]
            assertEquals(EventSource.KEYBOARD, persistedIme.source)
            assertEquals(EventType.SENSITIVE_INPUT_DETECTED, persistedIme.eventType)
            assertEquals(InputClassification.SENSITIVE_PASSWORD, persistedIme.inputClassification)
            assertNull("Persistent IME password event must have null eventData", persistedIme.eventData)
        }
    }

    @Test
    fun unifiedPipeline_selfMonitoringSuppression_rejectsOwnAppEvents() {
        runBlocking {
            // An accessibility event originating from our own app package
            val ownAppEvent = SimpleAccessibilityEventInfo(
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                packageName = ownPackage,
                className = "android.widget.Button",
                contentDescription = "Start Session",
                eventTime = 1000L
            )

            val shouldProcess = accessibilityFilter.shouldProcess(ownAppEvent)
            assertFalse("AccessibilityFilter must reject own app package to prevent feedback loops", shouldProcess)

            // External app event passes
            val externalAppEvent = SimpleAccessibilityEventInfo(
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                packageName = targetAppPackage,
                className = "android.widget.Button",
                contentDescription = "Save",
                eventTime = 1050L
            )
            assertTrue(accessibilityFilter.shouldProcess(externalAppEvent))
            accessibilityPublisher.publish(accessibilityMapper.map(externalAppEvent))

            assertEquals(1, eventRepository.events.size)
            assertEquals(targetAppPackage, eventRepository.events.first().packageName)
        }
    }

    @Test
    fun unifiedPipeline_scrollStormRateLimiting_doesNotDropConcurrentTyping() {
        runBlocking {
            val filterWithRateLimit = AccessibilityEventFilterImpl(
                ownPackageName = ownPackage,
                config = AccessibilityConfig(minScrollIntervalMs = 100L)
            )

            // Simulate 5 rapid scroll events in 10ms
            var passedScrolls = 0
            for (i in 0 until 5) {
                val scroll = SimpleAccessibilityEventInfo(
                    eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                    packageName = targetAppPackage,
                    eventTime = 1000L + i * 2L
                )
                if (filterWithRateLimit.shouldProcess(scroll)) {
                    passedScrolls++
                    accessibilityPublisher.publish(accessibilityMapper.map(scroll))
                }
            }

            // Only 1 scroll event should pass rate limiting
            assertEquals(1, passedScrolls)

            // Concurrently, 3 keystrokes arrive from the keyboard
            for (char in listOf("x", "y", "z")) {
                val imeEvent = RawInteractionEvent(
                    timestamp = 1005L,
                    source = EventSource.KEYBOARD,
                    eventType = EventType.TEXT_COMMITTED,
                    packageName = targetAppPackage,
                    candidateData = char,
                    inputClassification = InputClassification.NORMAL_TEXT
                )
                imePublisher.publish(imeEvent)
            }

            // All 3 keystrokes plus the 1 allowed scroll event are persisted
            assertEquals(4, eventRepository.events.size)
            assertEquals(EventType.VIEW_SCROLLED, eventRepository.events[0].eventType)
            assertEquals("x", eventRepository.events[1].eventData)
            assertEquals("y", eventRepository.events[2].eventData)
            assertEquals("z", eventRepository.events[3].eventData)
        }
    }

    @Test
    fun unifiedPipeline_repositoryFailure_doesNotThrowOrCrashPublishers() {
        runBlocking {
            // Simulate catastrophic disk failure in repository
            eventRepository.failOnInsert = true

            val imeEvent = RawInteractionEvent(
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = targetAppPackage,
                candidateData = "SafeKey"
            )

            val accessEvent = SimpleAccessibilityEventInfo(
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                packageName = targetAppPackage,
                eventTime = 1050L
            )

            // Neither publisher should throw
            imePublisher.publish(imeEvent)
            accessibilityPublisher.publish(accessibilityMapper.map(accessEvent))

            // Zero events inserted, but callers suffered zero exceptions
            assertEquals(0, eventRepository.events.size)
        }
    }
}
