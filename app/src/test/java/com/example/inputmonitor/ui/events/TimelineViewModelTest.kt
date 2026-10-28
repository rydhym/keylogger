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
 * Unit tests for [TimelineViewModel] verifying filtering, pagination, and selection (SRS.md §48–§50).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeEventRepository : EventRepository {
        val events = mutableListOf<InteractionEvent>()

        override suspend fun insertEvent(event: InteractionEvent): Long {
            events.add(event)
            return events.size.toLong()
        }

        override suspend fun insertEvents(eventsList: List<InteractionEvent>): List<Long> {
            events.addAll(eventsList)
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

        override suspend fun getEventCountForSession(sessionId: String): Int =
            events.count { it.sessionId == sessionId }

        override fun observeEventCountForSession(sessionId: String): Flow<Int> =
            flowOf(events.count { it.sessionId == sessionId })

        override fun observeEventCounts(sessionId: String?): Flow<EventCounts> =
            flowOf(EventCounts(total = events.size.toLong()))

        override fun observeLatestEvent(): Flow<InteractionEvent?> =
            flowOf(events.maxByOrNull { it.timestamp })

        override fun observeEventsPaged(
            filter: EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> {
            val matching = filterEvents(filter)
            return flowOf(matching.sortedByDescending { it.timestamp }.drop(offset).take(limit))
        }

        override fun observeFilteredCount(filter: EventFilterCriteria): Flow<Int> {
            return flowOf(filterEvents(filter).size)
        }

        override fun observeDistinctPackageNames(): Flow<List<String>> {
            return flowOf(events.mapNotNull { it.packageName }.distinct().sorted())
        }

        override fun observeMatchingApplications(query: String, limit: Int): Flow<List<String>> {
            return flowOf(
                events.mapNotNull { it.applicationName }
                    .distinct()
                    .filter { it.contains(query, ignoreCase = true) }
                    .sorted()
                    .take(limit)
            )
        }

        override fun observeMatchingPackages(query: String, limit: Int): Flow<List<String>> {
            return flowOf(
                events.mapNotNull { it.packageName }
                    .distinct()
                    .filter { it.contains(query, ignoreCase = true) }
                    .sorted()
                    .take(limit)
            )
        }

        override fun observeSearchSuggestions(query: String, limit: Int): Flow<List<SearchSuggestion>> {
            val suggestions = mutableListOf<SearchSuggestion>()
            events.mapNotNull { it.applicationName }.distinct().filter { it.contains(query, ignoreCase = true) }.forEach {
                if (suggestions.size < limit) suggestions.add(SearchSuggestion(it, SuggestionType.APPLICATION))
            }
            events.mapNotNull { it.packageName }.distinct().filter { it.contains(query, ignoreCase = true) }.forEach {
                if (suggestions.size < limit && suggestions.none { s -> s.query.equals(it, ignoreCase = true) }) {
                    suggestions.add(SearchSuggestion(it, SuggestionType.PACKAGE))
                }
            }
            return flowOf(suggestions)
        }

        override suspend fun deleteEventsForSession(sessionId: String): Boolean {
            events.removeAll { it.sessionId == sessionId }
            return true
        }

        override suspend fun deleteAllEvents(): Boolean {
            events.clear()
            return true
        }

        private fun filterEvents(filter: EventFilterCriteria): List<InteractionEvent> {
            return events.filter {
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

    private class FakeSessionRepository : SessionRepository {
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

    private class FakeSessionManager(
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

    private lateinit var eventRepository: FakeEventRepository
    private lateinit var sessionRepository: FakeSessionRepository
    private lateinit var sessionManager: FakeSessionManager
    private lateinit var viewModel: TimelineViewModel

    private val sampleSession = MonitoringSession(
        id = "session-101",
        name = "Usability Study",
        startTime = 1000L,
        state = SessionState.ACTIVE,
        androidVersion = "14",
        deviceModel = "Pixel 8",
        appVersion = "1.0.0"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        eventRepository = FakeEventRepository()
        sessionRepository = FakeSessionRepository()
        sessionRepository.sessions.add(sampleSession)
        sessionManager = FakeSessionManager(sampleSession)

        // Seed sample events
        for (i in 1..75) {
            eventRepository.events.add(
                InteractionEvent(
                    id = i.toLong(),
                    sessionId = if (i % 2 == 0) "session-101" else "session-102",
                    timestamp = 1000L + (i * 10),
                    source = if (i % 3 == 0) EventSource.KEYBOARD else EventSource.ACCESSIBILITY,
                    eventType = when {
                        i % 5 == 0 -> EventType.SENSITIVE_INPUT_DETECTED
                        i % 3 == 0 -> EventType.TEXT_COMMITTED
                        else -> EventType.VIEW_CLICKED
                    },
                    packageName = if (i % 2 == 0) "com.example.notes" else "com.example.browser",
                    applicationName = if (i % 2 == 0) "Notes App" else "Web Browser",
                    eventData = if (i % 5 == 0) null else "Input-$i",
                    inputClassification = if (i % 5 == 0) InputClassification.SENSITIVE_PASSWORD else InputClassification.NORMAL_TEXT,
                    metadata = null
                )
            )
        }

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
    fun initialState_loadsFirstPageAndCalculatesTotalMetrics() {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(75, state.totalMatchingEvents)
        assertEquals(50, state.events.size) // Page size limit
        assertEquals(0, state.currentPage)
        assertEquals(2, state.totalPages)
        assertTrue(state.hasNextPage)
        assertFalse(state.hasPreviousPage)
        assertEquals(1, state.pageRangeStart)
        assertEquals(50, state.pageRangeEnd)
        assertEquals(2, state.distinctPackages.size)
        assertTrue(state.distinctPackages.contains("com.example.notes"))
    }

    @Test
    fun pagination_navigatesNextAndPreviousCorrectly() {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentPage)

        viewModel.nextPage()
        testDispatcher.scheduler.advanceUntilIdle()

        val secondPage = viewModel.uiState.value
        assertEquals(1, secondPage.currentPage)
        assertEquals(25, secondPage.events.size) // Remaining 25 on page 2
        assertFalse(secondPage.hasNextPage)
        assertTrue(secondPage.hasPreviousPage)
        assertEquals(51, secondPage.pageRangeStart)
        assertEquals(75, secondPage.pageRangeEnd)

        viewModel.previousPage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.currentPage)
        assertEquals(50, viewModel.uiState.value.events.size)
    }

    @Test
    fun filterByEventType_isolatesMatchingEvents() {
        viewModel.setEventTypeFilter(EventType.SENSITIVE_INPUT_DETECTED)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(15, state.totalMatchingEvents) // 75 / 5 = 15
        assertTrue(state.events.all { it.eventType == EventType.SENSITIVE_INPUT_DETECTED })
        assertTrue(state.events.all { it.inputClassification == InputClassification.SENSITIVE_PASSWORD })
        assertTrue(state.events.all { it.eventData == null })
    }

    @Test
    fun filterByPackage_isolatesSpecificApplication() {
        viewModel.setPackageFilter("com.example.notes")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(37, state.totalMatchingEvents) // Evens: 37
        assertTrue(state.events.all { it.packageName == "com.example.notes" })
        assertTrue(state.events.all { it.applicationName == "Notes App" })
    }

    @Test
    fun filterBySearchQuery_filtersAcrossMultipleFields() {
        viewModel.setSearchQuery("Web Browser")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(38, state.totalMatchingEvents) // Odds: 38
        assertTrue(state.events.all { it.applicationName == "Web Browser" })
    }

    @Test
    fun selectEvent_managesDetailSelectionState() {
        val testEvent = eventRepository.events.first()
        viewModel.selectEvent(testEvent)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.selectedEventForDetail)
        assertEquals(testEvent.id, viewModel.uiState.value.selectedEventForDetail?.id)

        viewModel.selectEvent(null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedEventForDetail)
    }

    @Test
    fun clearFilters_resetsAllFilterCriteriaAndPage() {
        viewModel.setPackageFilter("com.example.notes")
        viewModel.setEventTypeFilter(EventType.TEXT_COMMITTED)
        viewModel.setSearchQuery("query")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.filterCriteria.hasActiveFilters)

        viewModel.clearFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.filterCriteria.hasActiveFilters)
        assertEquals(0, viewModel.uiState.value.currentPage)
        assertEquals(75, viewModel.uiState.value.totalMatchingEvents)
    }

    @Test
    fun onSearchQueryInputChanged_debouncesQueryPropagation() {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryInputChanged("Notes App")
        testDispatcher.scheduler.runCurrent()
        // Raw text updates immediately in UI state
        assertEquals("Notes App", viewModel.uiState.value.searchQueryInput)
        // Database filter query has NOT updated yet due to 300ms debounce
        assertNull(viewModel.uiState.value.filterCriteria.searchQuery)

        // Advance virtual clock past debounce threshold
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Notes App", viewModel.uiState.value.filterCriteria.searchQuery)
        assertEquals(37, viewModel.uiState.value.totalMatchingEvents)
        assertTrue(viewModel.uiState.value.events.all { it.applicationName == "Notes App" })
    }

    @Test
    fun onSearchQueryInputChanged_clearingQuery_resetsImmediately() {
        viewModel.setSearchQuery("Notes App")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Notes App", viewModel.uiState.value.filterCriteria.searchQuery)

        viewModel.onSearchQueryInputChanged("")
        testDispatcher.scheduler.runCurrent()
        assertEquals("", viewModel.uiState.value.searchQueryInput)
        assertNull(viewModel.uiState.value.filterCriteria.searchQuery)
    }

    @Test
    fun searchSuggestions_updatesBasedOnInput() {
        viewModel.onSearchQueryInputChanged("Notes")
        testDispatcher.scheduler.advanceUntilIdle()

        val suggestions = viewModel.uiState.value.searchSuggestions
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.query == "Notes App" && it.type == SuggestionType.APPLICATION })
    }

    @Test
    fun applySuggestion_application_setsSearchFilter() {
        viewModel.applySuggestion(SearchSuggestion("Notes App", SuggestionType.APPLICATION))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Notes App", viewModel.uiState.value.filterCriteria.searchQuery)
        assertEquals("Notes App", viewModel.uiState.value.searchQueryInput)
        assertEquals(37, viewModel.uiState.value.totalMatchingEvents)
    }

    @Test
    fun applySuggestion_package_setsPackageFilter() {
        viewModel.applySuggestion(SearchSuggestion("com.example.notes", SuggestionType.PACKAGE))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("com.example.notes", viewModel.uiState.value.filterCriteria.packageName)
        assertNull(viewModel.uiState.value.filterCriteria.searchQuery)
        assertEquals("", viewModel.uiState.value.searchQueryInput)
        assertEquals(37, viewModel.uiState.value.totalMatchingEvents)
    }

    @Test
    fun applySuggestion_eventType_setsEventTypeFilter() {
        viewModel.applySuggestion(SearchSuggestion("VIEW_CLICKED", SuggestionType.EVENT_TYPE))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(EventType.VIEW_CLICKED, viewModel.uiState.value.filterCriteria.eventType)
        assertNull(viewModel.uiState.value.filterCriteria.searchQuery)
        assertEquals("", viewModel.uiState.value.searchQueryInput)
    }
}
