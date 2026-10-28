package com.example.inputmonitor.ui.export

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.export.CsvEventExporterImpl
import com.example.inputmonitor.domain.export.ExportResult
import com.example.inputmonitor.domain.export.ExportValidatorImpl
import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.usecase.ExportSessionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.io.ByteArrayOutputStream

/**
 * Unit tests for [ExportViewModel] verifying session selection, confirmation dialogs,
 * streaming execution, and UI state management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeEventRepository : EventRepository {
        val events = mutableListOf<InteractionEvent>()

        override suspend fun insertEvent(event: InteractionEvent): Long = 1L
        override suspend fun insertEvents(events: List<InteractionEvent>): List<Long> = emptyList()
        override suspend fun getEventById(id: Long): InteractionEvent? = null
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEvent> = emptyList()
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent> = emptyList()
        override suspend fun getEventsFiltered(sessionId: String, packageFilter: String?, eventTypeFilter: String?, startTime: Long?, endTime: Long?): List<InteractionEvent> = emptyList()
        override suspend fun getEventCountForSession(sessionId: String): Int = events.count { it.sessionId == sessionId }
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(events.count { it.sessionId == sessionId })
        override fun observeEventCounts(sessionId: String?): Flow<com.example.inputmonitor.domain.model.EventCounts> =
            flowOf(com.example.inputmonitor.domain.model.EventCounts(total = events.size.toLong()))
        override fun observeLatestEvent(): Flow<InteractionEvent?> = flowOf(null)

        override fun observeEventsPaged(filter: EventFilterCriteria, limit: Int, offset: Int): Flow<List<InteractionEvent>> {
            val filtered = events.filter { filter.sessionId == null || it.sessionId == filter.sessionId }
            return flowOf(filtered.drop(offset).take(limit))
        }

        override fun observeFilteredCount(filter: EventFilterCriteria): Flow<Int> {
            val count = events.count { filter.sessionId == null || it.sessionId == filter.sessionId }
            return flowOf(count)
        }

        override fun observeDistinctPackageNames(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun deleteEventsForSession(sessionId: String): Boolean = true
        override suspend fun deleteAllEvents(): Boolean = true
    }

    private class FakeSessionRepository : SessionRepository {
        val sessionsFlow = MutableStateFlow<List<MonitoringSession>>(emptyList())
        override fun observeAllSessions(): Flow<List<MonitoringSession>> = sessionsFlow.asStateFlow()
        override suspend fun getAllSessions(): List<MonitoringSession> = sessionsFlow.value
        override fun observeActiveSession(): Flow<MonitoringSession?> = flowOf(null)
        override suspend fun getActiveSession(): MonitoringSession? = null
        override suspend fun createSession(session: MonitoringSession) {
            sessionsFlow.value = sessionsFlow.value + session
        }
        override suspend fun updateSession(session: MonitoringSession) {}
        override suspend fun getSessionById(id: String): MonitoringSession? = sessionsFlow.value.find { it.id == id }
        override fun observeSessionById(id: String): Flow<MonitoringSession?> = flowOf(sessionsFlow.value.find { it.id == id })
        override suspend fun finishSession(id: String, endTime: Long): Boolean = true
        override suspend fun deleteSession(id: String): Boolean = true
        override suspend fun deleteAllSessions(): Boolean = true
    }

    private lateinit var eventRepository: FakeEventRepository
    private lateinit var sessionRepository: FakeSessionRepository
    private lateinit var exportUseCase: ExportSessionUseCase
    private lateinit var viewModel: ExportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        eventRepository = FakeEventRepository()
        sessionRepository = FakeSessionRepository()
        exportUseCase = ExportSessionUseCase(
            eventRepository = eventRepository,
            exportValidator = ExportValidatorImpl(),
            csvEventExporter = CsvEventExporterImpl(),
            dispatchers = testDispatchers
        )
        viewModel = ExportViewModel(exportUseCase, sessionRepository, eventRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsSessionsAndEventCounts() = runTest(testDispatcher) {
        val session1 = MonitoringSession(id = "sess-1", name = "Test Session", state = SessionState.STOPPED, startTime = 1000L)
        sessionRepository.sessionsFlow.value = listOf(session1)
        eventRepository.events.add(
            InteractionEvent(
                sessionId = "sess-1",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.notes",
                applicationName = "Notes",
                eventData = "Test",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("All Sessions", state.selectedSessionName)
        assertNull(state.selectedSessionId)
        assertEquals(1, state.availableSessions.size)
        assertEquals(1L, state.eventCountForScope)
        assertTrue(state.canExport)
        assertFalse(state.isExporting)
    }

    @Test
    fun selectSession_updatesScopeAndEventCount() = runTest(testDispatcher) {
        val session1 = MonitoringSession(id = "sess-1", name = "First Session", state = SessionState.STOPPED, startTime = 1000L)
        val session2 = MonitoringSession(id = "sess-2", name = "Empty Session", state = SessionState.ACTIVE, startTime = 5000L)
        sessionRepository.sessionsFlow.value = listOf(session1, session2)

        eventRepository.events.add(
            InteractionEvent(
                sessionId = "sess-1",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.notes",
                applicationName = "Notes",
                eventData = "Test",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        advanceUntilIdle()

        // Select session 2 (empty)
        viewModel.selectSession("sess-2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("sess-2", state.selectedSessionId)
        assertEquals("Empty Session", state.selectedSessionName)
        assertEquals(0L, state.eventCountForScope)
        assertFalse(state.canExport)

        // Select session 1 (has 1 event)
        viewModel.selectSession("sess-1")
        advanceUntilIdle()

        val state1 = viewModel.uiState.value
        assertEquals("First Session", state1.selectedSessionName)
        assertEquals(1L, state1.eventCountForScope)
        assertTrue(state1.canExport)
    }

    @Test
    fun executeExport_updatesProgressAndEmitsSuccessResult() = runTest(testDispatcher) {
        eventRepository.events.add(
            InteractionEvent(
                sessionId = "sess-1",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.notes",
                applicationName = "Notes",
                eventData = "Test",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        advanceUntilIdle()

        val outputStream = ByteArrayOutputStream()
        viewModel.executeExport(outputStream, "content://com.android.providers.downloads/document/123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertNotNull(state.lastResult)
        assertTrue(state.lastResult is ExportResult.Success)

        val success = state.lastResult as ExportResult.Success
        assertEquals(1L, success.eventCount)
        assertEquals("content://com.android.providers.downloads/document/123", success.targetUriString)

        // Dismiss result
        viewModel.dismissResult()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.lastResult)
    }

    @Test
    fun factory_createsValidViewModel() {
        val factory = ExportViewModel.provideFactory(exportUseCase, sessionRepository, eventRepository)
        val vm = factory.create(ExportViewModel::class.java)
        assertNotNull(vm)
    }
}
