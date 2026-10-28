package com.example.inputmonitor.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SearchSuggestion
import com.example.inputmonitor.domain.model.SuggestionType
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.usecase.ObserveActiveSessionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the interaction event timeline screen (Architecture.md §56, SRS.md §48–§50).
 *
 * Implements bounded RAM consumption via paginated Room queries (AC-23, AC-24)
 * and debounced database-backed search filtering with auto-suggestions (IMPLEMENTATION_PLAN.md §52).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TimelineViewModel(
    private val eventRepository: EventRepository,
    private val sessionRepository: SessionRepository,
    observeActiveSessionUseCase: ObserveActiveSessionUseCase
) : ViewModel() {

    val pageSize: Int = 50

    private val filterFlow = MutableStateFlow(EventFilterCriteria())
    private val pageFlow = MutableStateFlow(0)
    private val selectedEventFlow = MutableStateFlow<InteractionEvent?>(null)
    private val isFilterDialogVisibleFlow = MutableStateFlow(false)
    private val searchInputFlow = MutableStateFlow("")

    private val searchSuggestionsFlow = searchInputFlow.flatMapLatest { input ->
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            flowOf(emptyList())
        } else {
            eventRepository.observeSearchSuggestions(trimmed, limit = 5)
        }
    }

    private val pagedEventsFlow = combine(filterFlow, pageFlow) { filter, page ->
        filter to page
    }.flatMapLatest { (filter, page) ->
        eventRepository.observeEventsPaged(
            filter = filter,
            limit = pageSize,
            offset = page * pageSize
        )
    }

    private val countFlow = filterFlow.flatMapLatest { filter ->
        eventRepository.observeFilteredCount(filter)
    }

    private data class EventsPagingState(
        val events: List<InteractionEvent>,
        val count: Int,
        val filter: EventFilterCriteria,
        val page: Int
    )

    private data class AuxiliaryState(
        val packages: List<String>,
        val sessions: List<MonitoringSession>,
        val activeSession: MonitoringSession?,
        val selectedEvent: InteractionEvent?,
        val isFilterDialogVisible: Boolean,
        val searchInput: String,
        val searchSuggestions: List<SearchSuggestion>
    )

    private val eventsPagingStateFlow = combine(
        pagedEventsFlow,
        countFlow,
        filterFlow,
        pageFlow
    ) { events, count, filter, page ->
        EventsPagingState(events, count, filter, page)
    }

    private data class DialogSelectionState(
        val selectedEvent: InteractionEvent?,
        val isFilterDialogVisible: Boolean
    )

    private val dialogSelectionFlow = combine(
        selectedEventFlow,
        isFilterDialogVisibleFlow
    ) { selected, isVisible ->
        DialogSelectionState(selected, isVisible)
    }

    private data class SearchState(
        val queryInput: String,
        val suggestions: List<SearchSuggestion>
    )

    private val searchStateFlow = combine(
        searchInputFlow,
        searchSuggestionsFlow
    ) { input, suggestions ->
        SearchState(input, suggestions)
    }

    private val auxiliaryStateFlow = combine(
        eventRepository.observeDistinctPackageNames(),
        sessionRepository.observeAllSessions(),
        observeActiveSessionUseCase(),
        dialogSelectionFlow,
        searchStateFlow
    ) { packages, sessions, activeSession, dialogState, searchState ->
        AuxiliaryState(
            packages = packages,
            sessions = sessions,
            activeSession = activeSession,
            selectedEvent = dialogState.selectedEvent,
            isFilterDialogVisible = dialogState.isFilterDialogVisible,
            searchInput = searchState.queryInput,
            searchSuggestions = searchState.suggestions
        )
    }

    val uiState: StateFlow<TimelineUiState> = combine(
        eventsPagingStateFlow,
        auxiliaryStateFlow
    ) { paging, aux ->
        TimelineUiState(
            events = paging.events,
            totalMatchingEvents = paging.count,
            filterCriteria = paging.filter,
            distinctPackages = aux.packages,
            sessions = aux.sessions,
            activeSession = aux.activeSession,
            currentPage = paging.page,
            pageSize = pageSize,
            selectedEventForDetail = aux.selectedEvent,
            isFilterDialogVisible = aux.isFilterDialogVisible,
            searchQueryInput = aux.searchInput,
            searchSuggestions = aux.searchSuggestions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TimelineUiState(pageSize = pageSize)
    )

    init {
        viewModelScope.launch {
            searchInputFlow
                .debounce { query ->
                    if (query.isBlank()) 0L else 300L
                }
                .distinctUntilChanged()
                .collect { debouncedQuery ->
                    val safeQuery = if (debouncedQuery.isBlank()) null else debouncedQuery.trim()
                    if (filterFlow.value.searchQuery != safeQuery) {
                        filterFlow.value = filterFlow.value.copy(searchQuery = safeQuery)
                        pageFlow.value = 0
                    }
                }
        }
    }

    fun setSessionFilter(sessionId: String?) {
        filterFlow.value = filterFlow.value.copy(sessionId = sessionId)
        pageFlow.value = 0
    }

    fun setPackageFilter(packageName: String?) {
        filterFlow.value = filterFlow.value.copy(packageName = packageName)
        pageFlow.value = 0
    }

    fun setEventTypeFilter(eventType: EventType?) {
        filterFlow.value = filterFlow.value.copy(eventType = eventType)
        pageFlow.value = 0
    }

    fun setSourceFilter(source: EventSource?) {
        filterFlow.value = filterFlow.value.copy(source = source)
        pageFlow.value = 0
    }

    fun setClassificationFilter(classification: InputClassification?) {
        filterFlow.value = filterFlow.value.copy(classification = classification)
        pageFlow.value = 0
    }

    /**
     * Updates the raw search text in real-time. Debounced 300ms propagation to database query.
     */
    fun onSearchQueryInputChanged(query: String) {
        searchInputFlow.value = query
        if (query.isBlank()) {
            filterFlow.value = filterFlow.value.copy(searchQuery = null)
            pageFlow.value = 0
        }
    }

    /**
     * Immediately applies a search query to the filter engine without debouncing delay.
     */
    fun setSearchQuery(query: String?) {
        val safeQuery = if (query.isNullOrBlank()) null else query.trim()
        searchInputFlow.value = query ?: ""
        filterFlow.value = filterFlow.value.copy(searchQuery = safeQuery)
        pageFlow.value = 0
    }

    /**
     * Applies an auto-complete search suggestion directly to the active filters.
     */
    fun applySuggestion(suggestion: SearchSuggestion) {
        when (suggestion.type) {
            SuggestionType.APPLICATION -> {
                setSearchQuery(suggestion.query)
            }
            SuggestionType.PACKAGE -> {
                setPackageFilter(suggestion.query)
                searchInputFlow.value = ""
                filterFlow.value = filterFlow.value.copy(searchQuery = null)
                pageFlow.value = 0
            }
            SuggestionType.EVENT_TYPE -> {
                try {
                    val eventType = EventType.valueOf(suggestion.query)
                    setEventTypeFilter(eventType)
                } catch (_: Exception) {
                    setSearchQuery(suggestion.query)
                }
                searchInputFlow.value = ""
                filterFlow.value = filterFlow.value.copy(searchQuery = null)
                pageFlow.value = 0
            }
        }
    }

    fun setTimeRange(startTime: Long?, endTime: Long?) {
        filterFlow.value = filterFlow.value.copy(startTime = startTime, endTime = endTime)
        pageFlow.value = 0
    }

    fun clearFilters() {
        searchInputFlow.value = ""
        filterFlow.value = EventFilterCriteria()
        pageFlow.value = 0
    }

    fun nextPage() {
        val currentState = uiState.value
        if (currentState.hasNextPage) {
            pageFlow.value += 1
        }
    }

    fun previousPage() {
        if (pageFlow.value > 0) {
            pageFlow.value -= 1
        }
    }

    fun goToPage(page: Int) {
        val total = uiState.value.totalPages
        if (page in 0 until total) {
            pageFlow.value = page
        }
    }

    fun selectEvent(event: InteractionEvent?) {
        selectedEventFlow.value = event
    }

    fun setFilterDialogVisible(visible: Boolean) {
        isFilterDialogVisibleFlow.value = visible
    }

    fun deleteEventsForSession(sessionId: String) {
        viewModelScope.launch {
            eventRepository.deleteEventsForSession(sessionId)
            if (filterFlow.value.sessionId == sessionId) {
                pageFlow.value = 0
            }
        }
    }

    companion object {
        fun provideFactory(
            eventRepository: EventRepository,
            sessionRepository: SessionRepository,
            observeActiveSessionUseCase: ObserveActiveSessionUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TimelineViewModel(
                    eventRepository,
                    sessionRepository,
                    observeActiveSessionUseCase
                ) as T
            }
        }
    }
}
