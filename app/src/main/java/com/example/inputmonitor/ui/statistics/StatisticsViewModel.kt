package com.example.inputmonitor.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel coordinating interaction statistics observation, session selection, and aggregation (Architecture.md §57).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val eventRepository: EventRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    private val _isDropdownExpanded = MutableStateFlow(false)

    val uiState: StateFlow<StatisticsUiState> = combine(
        _selectedSessionId.flatMapLatest { sessionId ->
            eventRepository.observeStatistics(sessionId)
        },
        sessionRepository.observeAllSessions(),
        _selectedSessionId,
        _isDropdownExpanded
    ) { stats, sessions, selectedSessionId, isDropdownExpanded ->
        StatisticsUiState(
            statistics = stats,
            selectedSessionId = selectedSessionId,
            availableSessions = sessions,
            isLoading = false,
            isSessionDropdownExpanded = isDropdownExpanded
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StatisticsUiState(isLoading = true)
    )

    fun selectSession(sessionId: String?) {
        _selectedSessionId.value = sessionId
        _isDropdownExpanded.value = false
    }

    fun setDropdownExpanded(expanded: Boolean) {
        _isDropdownExpanded.value = expanded
    }

    companion object {
        fun provideFactory(
            eventRepository: EventRepository,
            sessionRepository: SessionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
                    return StatisticsViewModel(eventRepository, sessionRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
