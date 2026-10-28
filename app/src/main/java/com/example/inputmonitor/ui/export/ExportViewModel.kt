package com.example.inputmonitor.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.inputmonitor.domain.export.ExportResult
import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.usecase.ExportSessionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStream

/**
 * ViewModel coordinating user-initiated local CSV export and progress observation (Architecture.md §58, SRS.md §54).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModel(
    private val exportSessionUseCase: ExportSessionUseCase,
    private val sessionRepository: SessionRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private data class ExportProgressState(
        val exported: Long = 0L,
        val total: Long = 0L,
        val progress: Float = 0f,
        val result: ExportResult? = null,
        val isExporting: Boolean = false
    )

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    private val _isSessionDropdownExpanded = MutableStateFlow(false)
    private val _progressState = MutableStateFlow(ExportProgressState())

    val uiState: StateFlow<ExportUiState> = combine(
        sessionRepository.observeAllSessions(),
        _selectedSessionId,
        _selectedSessionId.flatMapLatest { sessionId ->
            eventRepository.observeFilteredCount(EventFilterCriteria(sessionId = sessionId))
        },
        _isSessionDropdownExpanded,
        _progressState
    ) { sessions, selectedSessionId, count, isDropdownExpanded, progressState ->
        ExportUiState(
            availableSessions = sessions,
            selectedSessionId = selectedSessionId,
            isSessionDropdownExpanded = isDropdownExpanded,
            isExporting = progressState.isExporting,
            exportedCount = progressState.exported,
            totalCount = progressState.total,
            exportProgress = progressState.progress,
            lastResult = progressState.result,
            eventCountForScope = count.toLong()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ExportUiState()
    )

    fun selectSession(sessionId: String?) {
        _selectedSessionId.value = sessionId
        _isSessionDropdownExpanded.value = false
        _progressState.value = _progressState.value.copy(result = null)
    }

    fun setSessionDropdownExpanded(expanded: Boolean) {
        _isSessionDropdownExpanded.value = expanded
    }

    fun dismissResult() {
        _progressState.value = _progressState.value.copy(result = null)
    }

    fun executeExport(
        outputStream: OutputStream,
        targetUriString: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        _progressState.value = ExportProgressState(isExporting = true)

        viewModelScope.launch {
            var isSuccess = false
            try {
                val result = exportSessionUseCase(
                    outputStream = outputStream,
                    sessionId = _selectedSessionId.value,
                    onProgress = { exported, total ->
                        _progressState.value = _progressState.value.copy(
                            exported = exported,
                            total = total,
                            progress = if (total > 0L) exported.toFloat() / total else 1.0f
                        )
                    }
                )

                _progressState.value = _progressState.value.copy(
                    isExporting = false,
                    result = when (result) {
                        is ExportResult.Success -> {
                            isSuccess = true
                            result.copy(targetUriString = targetUriString)
                        }
                        is ExportResult.Error -> result
                    }
                )
            } catch (t: Throwable) {
                _progressState.value = _progressState.value.copy(
                    isExporting = false,
                    result = ExportResult.Error(
                        message = t.message ?: "An unexpected error occurred during export",
                        throwable = t
                    )
                )
            } finally {
                try {
                    outputStream.flush()
                    outputStream.close()
                } catch (_: Throwable) {}
                onComplete?.invoke(isSuccess)
            }
        }
    }

    companion object {
        fun provideFactory(
            exportSessionUseCase: ExportSessionUseCase,
            sessionRepository: SessionRepository,
            eventRepository: EventRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ExportViewModel::class.java)) {
                    return ExportViewModel(exportSessionUseCase, sessionRepository, eventRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
