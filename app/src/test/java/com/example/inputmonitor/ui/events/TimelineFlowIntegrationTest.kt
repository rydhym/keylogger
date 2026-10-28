package com.example.inputmonitor.ui.events

import com.example.inputmonitor.domain.model.EventCounts
import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SearchSuggestion
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.model.SuggestionType
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.session.SessionManager
import com.example.inputmonitor.domain.usecase.ObserveActiveSessionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
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
 * End-to-end integration test verifying the full timeline observation, search, and filtering pipeline
 * (Architecture.md §56, SRS.md §48–§50, IMPLEMENTATION_PLAN.md §51).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineFlowIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    private class IntegrationEventRepository : EventRepository {
        val events = mutableListOf<InteractionEvent>()
        val eventsFlow = MutableStateFlow<List<InteractionEvent>>(emptyList())

        override suspend fun insertEvent(event: InteractionEvent): Long {
            events.add(event)
            eventsFlow.value = events.toList()
            return events.size.toLong()
        }

        override suspend fun insertEvents(eventsList: List<InteractionEvent>): List<Long> {
            events.addAll(eventsList)
            eventsFlow.value = events.toList()
            return eventsList.map { it.id }
        }

        override suspend fun getEventById(id: Long): InteractionEvent? = events.firstOrNull { it.id == id }
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>> =
            flowOf(events.filter { it.sessionId == sessionId })

        override suspend fun getEventsForSession(sessionId: String): List<InteractionEvent> =
            events.filter { it.sessionId == sessionId }

        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent> =
            events.filter { it.sessionId == sessionId }.drop(offset).take(limit)

        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEvent> = events

        override suspend fun getEventCountForSession(sessionId: String): Int = events.size
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(events.size)
        override fun observeEventCounts(sessionId: String?): Flow<EventCounts> =
            flowOf(EventCounts(total = events.size.toLong()))

        override fun observeLatestEvent(): Flow<InteractionEvent?> =
            flowOf(events.maxByOrNull { it.timestamp })

        override fun observeEventsPaged(
            filter: EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> {
            return eventsFlow.map { list ->
                filterEvents(list, filter).sortedByDescending { it.timestamp }.drop(offset).take(limit)
            }
        }

        override fun observeFilteredCount(filter: EventFilterCriteria): Flow<Int> {
            return eventsFlow.map { list ->
                filterEvents(list, filter).size
            }
        }

        override fun observeDistinctPackageNames(): Flow<List<String>> {
            return eventsFlow.map { list ->
                list.mapNotNull { it.packageName }.distinct().sorted()
            }
        }

        override fun observeMatchingApplications(query: String, limit: Int): Flow<List<String>> {
            return eventsFlow.map { list ->
                list.mapNotNull { it.applicationName }
                    .distinct()
                    .filter { it.contains(query, ignoreCase = true) }
                    .sorted()
                    .take(limit)
            }
        }

        override fun observeMatchingPackages(query: String, limit: Int): Flow<List<String>> {
            return eventsFlow.map { list ->
                list.mapNotNull { it.packageName }
                    .distinct()
                    .filter { it.contains(query, ignoreCase = true) }
                    .sorted()
                    .take(limit)
            }
        }

        override fun observeSearchSuggestions(query: String, limit: Int): Flow<List<SearchSuggestion>> {
            return eventsFlow.map { list ->
                val suggestions = mutableListOf<SearchSuggestion>()
                list.mapNotNull { it.applicationName }.distinct().filter { it.contains(query, ignoreCase = true) }.forEach {
                    if (suggestions.size < limit) suggestions.add(SearchSuggestion(it, SuggestionType.APPLICATION))
                }
                list.mapNotNull { it.packageName }.distinct().filter { it.contains(query, ignoreCase = true) }.forEach {
                    if (suggestions.size < limit && suggestions.none { s -> s.query.equals(it, ignoreCase = true) }) {
                        suggestions.add(SearchSuggestion(it, SuggestionType.PACKAGE))
                    }
                }
                suggestions
            }
        }

        override suspend fun deleteEventsForSession(sessionId: String): Boolean {
            events.removeAll { it.sessionId == sessionId }
            eventsFlow.value = events.toList()
            return true
        }

        override suspend fun deleteAllEvents(): Boolean {
            events.clear()
            eventsFlow.value = emptyList()
            return true
        }

        private fun filterEvents(list: List<InteractionEvent>, filter: EventFilterCriteria): List<InteractionEvent> {
            return list.filter {
                (filter.sessionId == null || it.sessionId == filter.sessionId) &&
                (filter.packageName == null || it.packageName == filter.packageName) &&
                (filter.eventType == null || it.eventType == filter.eventType) &&
                (filter.source == null || it.source == filter.source) &&
                (filter.classification == null || it.inputClassification == filter.classification) &&
                (filter.startTime == null || it.timestamp >= filter.startTime) &&
                (filter.endTime == null || it.timestamp <= filter.endTime) &&
                (filter.searchQuery == null || (
                    (it.packageName != null && it.packageName.contains(filter.searchQuery, ignoreCase = true)) ||
                    (it.applicationName != null && it.applicationName.contains(filter.searchQuery, ignoreCase = true)) ||
                    (it.eventType.name.contains(filter.searchQuery, ignoreCase = true)) ||
                    (it.sessionId.contains(filter.searchQuery, ignoreCase = true)) ||
                    (it.eventData != null && it.eventData.contains(filter.searchQuery, ignoreCase = true))
                ))
            }
        }
    }

    private class IntegrationSessionRepository : SessionRepository {
        val sessions = mutableListOf<MonitoringSession>()

        override suspend fun createSession(session: MonitoringSession) { sessions.add(session) }
        override suspend fun updateSession(session: MonitoringSession) {}
        override suspend fun getSessionById(id: String): MonitoringSession? = sessions.firstOrNull { it.id == id }
        override fun observeSessionById(id: String): Flow<MonitoringSession?> = flowOf(sessions.firstOrNull { it.id == id })
        override suspend fun getActiveSession(): MonitoringSession? = sessions.firstOrNull { it.state == SessionState.ACTIVE }
        override fun observeActiveSession(): Flow<MonitoringSession?> = flowOf(sessions.firstOrNull { it.state == SessionState.ACTIVE })
        override fun observeAllSessions(): Flow<List<MonitoringSession>> = flowOf(sessions)
        override suspend fun getAllSessions(): List<MonitoringSession> = sessions
        override suspend fun finishSession(id: String, endTime: Long): Boolean = true
        override suspend fun deleteSession(id: String): Boolean = true
        override suspend fun deleteAllSessions(): Boolean = true
    }

    private class IntegrationSessionManager(
        private val activeSession: MonitoringSession? = null
    ) : SessionManager {
        override val currentSession: MutableStateFlow<MonitoringSession?> =
            MutableStateFlow(activeSession)
        override val sessionState: MutableStateFlow<SessionState> =
            MutableStateFlow(activeSession?.state ?: SessionState.INACTIVE)

        override suspend fun startSession(name: String, notes: String?): MonitoringSession =
            throw UnsupportedOperationException()
        override suspend fun pauseSession(): Boolean = true
        override suspend fun resumeSession(): Boolean = true
        override suspend fun stopSession(): Boolean = true
        override suspend fun restoreSessionIfActive(): MonitoringSession? = activeSession
    }

    private lateinit var eventRepository: IntegrationEventRepository
    private lateinit var sessionRepository: IntegrationSessionRepository
    private lateinit var sessionManager: IntegrationSessionManager
    private lateinit var viewModel: TimelineViewModel

    private val activeSession = MonitoringSession(
        id = "session-e2e",
        name = "Field Usability Study",
        startTime = 1700000000000L,
        state = SessionState.ACTIVE,
        androidVersion = "14",
        deviceModel = "Pixel 8",
        appVersion = "1.0.0"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        eventRepository = IntegrationEventRepository()
        sessionRepository = IntegrationSessionRepository()
        sessionRepository.sessions.add(activeSession)
        sessionManager = IntegrationSessionManager(activeSession)

        viewModel = TimelineViewModel(
            eventRepository = eventRepository,
            sessionRepository = sessionRepository,
            observeActiveSessionUseCase = ObserveActiveSessionUseCase(sessionManager)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun endToEndTimelineFlow_verification() {
        testDispatcher.scheduler.advanceUntilIdle()

        // Step 1: Initial empty state
        var state = viewModel.uiState.value
        assertEquals(0, state.totalMatchingEvents)
        assertTrue(state.events.isEmpty())
        assertFalse(state.filterCriteria.hasActiveFilters)

        // Step 2: Ingest events simulating real-time user activity
        kotlinx.coroutines.runBlocking {
            // Typing in Notes App
            eventRepository.insertEvent(
                InteractionEvent(
                    id = 1L,
                    sessionId = "session-e2e",
                    timestamp = 1000L,
                    source = EventSource.KEYBOARD,
                    eventType = EventType.TEXT_COMMITTED,
                    packageName = "com.example.notes",
                    applicationName = "Notes Application",
                    eventData = "Research note line 1",
                    inputClassification = InputClassification.NORMAL_TEXT,
                    metadata = null
                )
            )
            // Focus change in Browser
            eventRepository.insertEvent(
                InteractionEvent(
                    id = 2L,
                    sessionId = "session-e2e",
                    timestamp = 1100L,
                    source = EventSource.ACCESSIBILITY,
                    eventType = EventType.VIEW_FOCUSED,
                    packageName = "com.example.browser",
                    applicationName = "Web Browser",
                    eventData = null,
                    inputClassification = InputClassification.NORMAL_TEXT,
                    metadata = null
                )
            )
            // Click in Browser
            eventRepository.insertEvent(
                InteractionEvent(
                    id = 3L,
                    sessionId = "session-e2e",
                    timestamp = 1200L,
                    source = EventSource.ACCESSIBILITY,
                    eventType = EventType.VIEW_CLICKED,
                    packageName = "com.example.browser",
                    applicationName = "Web Browser",
                    eventData = null,
                    inputClassification = InputClassification.NORMAL_TEXT,
                    metadata = null
                )
            )
            // Sensitive password entry in Login App (zero content stored)
            eventRepository.insertEvent(
                InteractionEvent(
                    id = 4L,
                    sessionId = "session-e2e",
                    timestamp = 1300L,
                    source = EventSource.KEYBOARD,
                    eventType = EventType.SENSITIVE_INPUT_DETECTED,
                    packageName = "com.example.bank",
                    applicationName = "Mobile Banking",
                    eventData = null,
                    inputClassification = InputClassification.SENSITIVE_PASSWORD,
                    metadata = null
                )
            )
        }

        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        // Step 3: Verify all 4 events appear in reverse-chronological order (newest first)
        assertEquals(4, state.totalMatchingEvents)
        assertEquals(4, state.events.size)
        assertEquals(4L, state.events[0].id) // 1300L (Bank)
        assertEquals(3L, state.events[1].id) // 1200L (Browser Click)
        assertEquals(2L, state.events[2].id) // 1100L (Browser Focus)
        assertEquals(1L, state.events[3].id) // 1000L (Notes Typing)

        // Step 4: Search filtering for "Browser"
        viewModel.setSearchQuery("Browser")
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        assertEquals(2, state.totalMatchingEvents)
        assertTrue(state.events.all { it.packageName == "com.example.browser" })

        // Step 5: Filter by event type "VIEW_CLICKED"
        viewModel.clearFilters()
        viewModel.setEventTypeFilter(EventType.VIEW_CLICKED)
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        assertEquals(1, state.totalMatchingEvents)
        assertEquals(3L, state.events[0].id)
        assertEquals(EventType.VIEW_CLICKED, state.events[0].eventType)

        // Step 6: Filter by classification "SENSITIVE_PASSWORD"
        viewModel.clearFilters()
        viewModel.setClassificationFilter(InputClassification.SENSITIVE_PASSWORD)
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        assertEquals(1, state.totalMatchingEvents)
        val sensitiveEvent = state.events[0]
        assertEquals("Mobile Banking", sensitiveEvent.applicationName)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, sensitiveEvent.inputClassification)
        assertNull("Defense in depth: sensitive eventData must be null", sensitiveEvent.eventData)

        // Step 7: Select event for detail view
        viewModel.selectEvent(sensitiveEvent)
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        assertNotNull(state.selectedEventForDetail)
        assertEquals(4L, state.selectedEventForDetail?.id)
        assertEquals("Mobile Banking", state.selectedEventForDetail?.applicationName)

        // Step 8: Clear selection and reset filters
        viewModel.selectEvent(null)
        viewModel.clearFilters()
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        assertNull(state.selectedEventForDetail)
        assertFalse(state.filterCriteria.hasActiveFilters)
        assertEquals(4, state.totalMatchingEvents)
    }

    @Test
    fun searchPipeline_debouncedQueryAndSuggestions_filtersTimelineAccurately() {
        // Step 1: Ingest distinct application events
        kotlinx.coroutines.runBlocking {
            eventRepository.insertEvent(
                InteractionEvent(
                    id = 101L,
                    sessionId = "search-sess-1",
                    timestamp = 1000L,
                    source = EventSource.KEYBOARD,
                    eventType = EventType.TEXT_COMMITTED,
                    packageName = "com.google.android.calculator",
                    applicationName = "Calculator",
                    eventData = "42 + 8",
                    inputClassification = InputClassification.NORMAL_TEXT
                )
            )
            eventRepository.insertEvent(
                InteractionEvent(
                    id = 102L,
                    sessionId = "search-sess-1",
                    timestamp = 1010L,
                    source = EventSource.ACCESSIBILITY,
                    eventType = EventType.VIEW_CLICKED,
                    packageName = "com.example.calendar",
                    applicationName = "Calendar Pro",
                    eventData = "Schedule Appointment",
                    inputClassification = InputClassification.NORMAL_TEXT
                )
            )
            eventRepository.insertEvent(
                InteractionEvent(
                    id = 103L,
                    sessionId = "search-sess-2",
                    timestamp = 1020L,
                    source = EventSource.KEYBOARD,
                    eventType = EventType.SENSITIVE_INPUT_DETECTED,
                    packageName = "com.secure.bank",
                    applicationName = "Secure Bank",
                    eventData = null, // Strictly suppressed
                    inputClassification = InputClassification.SENSITIVE_PASSWORD
                )
            )
        }

        testDispatcher.scheduler.advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals(3, state.totalMatchingEvents)

        // Step 2: User types "Cal" into search field
        viewModel.onSearchQueryInputChanged("Cal")
        testDispatcher.scheduler.runCurrent()

        // Before 300ms debounce threshold
        assertEquals("Cal", viewModel.uiState.value.searchQueryInput)
        assertNull(viewModel.uiState.value.filterCriteria.searchQuery)

        // Advance past debounce threshold
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        assertEquals("Cal", state.filterCriteria.searchQuery)
        assertEquals(2, state.totalMatchingEvents) // Calculator and Calendar Pro
        assertTrue(state.events.all { it.applicationName?.startsWith("Cal") == true })

        // Step 3: Verify auto-complete suggestions
        val suggestions = state.searchSuggestions
        assertTrue(suggestions.any { it.query == "Calculator" && it.type == SuggestionType.APPLICATION })
        assertTrue(suggestions.any { it.query == "Calendar Pro" && it.type == SuggestionType.APPLICATION })

        // Step 4: Click suggestion "Calendar Pro"
        val calProSuggestion = suggestions.first { it.query == "Calendar Pro" }
        viewModel.applySuggestion(calProSuggestion)
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        assertEquals("Calendar Pro", state.filterCriteria.searchQuery)
        assertEquals(1, state.totalMatchingEvents)
        assertEquals("Calendar Pro", state.events[0].applicationName)

        // Step 5: Privacy check - search for sensitive credential text yields 0 results
        viewModel.setSearchQuery("SecretPassword123")
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        assertEquals(0, state.totalMatchingEvents)
        assertTrue(state.events.isEmpty())

        // Step 6: Search by Session ID
        viewModel.setSearchQuery("search-sess-2")
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value

        assertEquals(1, state.totalMatchingEvents)
        assertEquals("Secure Bank", state.events[0].applicationName)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, state.events[0].inputClassification)
        assertNull(state.events[0].eventData)
    }
}
