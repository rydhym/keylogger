package com.example.inputmonitor.ui.export

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.dao.AppUsageTuple
import com.example.inputmonitor.data.dao.HourlyActivityTuple
import com.example.inputmonitor.data.dao.InteractionEventDao
import com.example.inputmonitor.data.dao.StatisticsSummaryTuple
import com.example.inputmonitor.data.entity.InteractionEventEntity
import com.example.inputmonitor.data.export.CsvEventExporterImpl
import com.example.inputmonitor.data.repository.EventRepositoryImpl
import com.example.inputmonitor.domain.export.ExportResult
import com.example.inputmonitor.domain.export.ExportValidatorImpl
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * End-to-end integration test verifying complete export pipeline:
 * SQLite/Room DAO paged queries -> Repository -> ExportSessionUseCase chunked streaming -> ExportValidator -> CsvEventExporter
 * (SRS.md §54, Architecture.md §58, §59, SEC-006, SEC-015).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportFlowIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class IntegrationEventDaoMock : InteractionEventDao {
        val storage = mutableListOf<InteractionEventEntity>()

        override suspend fun insertEvent(event: InteractionEventEntity): Long {
            storage.add(event)
            return event.id
        }

        override suspend fun insertEvents(events: List<InteractionEventEntity>): List<Long> {
            storage.addAll(events)
            return events.map { it.id }
        }

        override suspend fun getEventById(id: Long): InteractionEventEntity? = storage.find { it.id == id }
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEventEntity>> = flowOf(storage.filter { it.sessionId == sessionId })
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEventEntity> = storage.filter { it.sessionId == sessionId }
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEventEntity> =
            storage.filter { it.sessionId == sessionId }.drop(offset).take(limit)
        override suspend fun getEventsFiltered(sessionId: String, packageFilter: String?, eventTypeFilter: String?, startTime: Long?, endTime: Long?): List<InteractionEventEntity> = storage
        override suspend fun getEventCountForSession(sessionId: String): Int = storage.count { it.sessionId == sessionId }
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(storage.count { it.sessionId == sessionId })
        override fun observeEventCounts(sessionId: String?): Flow<com.example.inputmonitor.data.dao.EventCountsTuple?> =
            flowOf(com.example.inputmonitor.data.dao.EventCountsTuple(total = storage.size.toLong()))
        override fun observeLatestEvent(): Flow<InteractionEventEntity?> = flowOf(storage.maxByOrNull { it.timestamp })

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
            val matching = storage.filter { sessionId == null || it.sessionId == sessionId }
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
        ): Flow<Int> {
            val count = storage.count { sessionId == null || it.sessionId == sessionId }
            return flowOf(count)
        }

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
        override suspend fun deleteEventsForSession(sessionId: String): Int = 0
        override suspend fun deleteAllEvents(): Int = 0
    }

    private class FakeSessionRepository : SessionRepository {
        val sessionsFlow = MutableStateFlow<List<MonitoringSession>>(emptyList())
        override fun observeAllSessions(): Flow<List<MonitoringSession>> = sessionsFlow.asStateFlow()
        override suspend fun getAllSessions(): List<MonitoringSession> = sessionsFlow.value
        override fun observeActiveSession(): Flow<MonitoringSession?> = flowOf(null)
        override suspend fun getActiveSession(): MonitoringSession? = null
        override suspend fun createSession(session: MonitoringSession) {}
        override suspend fun updateSession(session: MonitoringSession) {}
        override suspend fun getSessionById(id: String): MonitoringSession? = sessionsFlow.value.find { it.id == id }
        override fun observeSessionById(id: String): Flow<MonitoringSession?> = flowOf(sessionsFlow.value.find { it.id == id })
        override suspend fun finishSession(id: String, endTime: Long): Boolean = true
        override suspend fun deleteSession(id: String): Boolean = true
        override suspend fun deleteAllSessions(): Boolean = true
    }

    private lateinit var daoMock: IntegrationEventDaoMock
    private lateinit var eventRepository: EventRepositoryImpl
    private lateinit var sessionRepository: FakeSessionRepository
    private lateinit var exportUseCase: ExportSessionUseCase
    private lateinit var viewModel: ExportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        daoMock = IntegrationEventDaoMock()
        eventRepository = EventRepositoryImpl(daoMock, testDispatchers)
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
    fun endToEndExport_multipleChunks_streamsSafelyWithRedaction() = runTest(testDispatcher) {
        // Generate 600 events across 2 sessions (exceeds CHUNK_SIZE=250, testing pagination)
        for (i in 1..600) {
            val isSessionA = i <= 400
            val isSensitive = i == 50 || i == 350
            daoMock.storage.add(
                InteractionEventEntity(
                    id = i.toLong(),
                    sessionId = if (isSessionA) "session-A" else "session-B",
                    timestamp = 1000L * i,
                    source = if (i % 2 == 0) "KEYBOARD" else "ACCESSIBILITY",
                    eventType = if (isSensitive) "SENSITIVE_INPUT_DETECTED" else "TEXT_COMMITTED",
                    packageName = if (isSessionA) "com.example.notes" else "com.example.browser",
                    applicationName = if (isSessionA) "Notes" else "Browser",
                    eventData = if (isSensitive) "SuperSecretPassword!" else "Input $i",
                    inputClassification = if (isSensitive) "SENSITIVE_PASSWORD" else "NORMAL_TEXT",
                    metadata = null
                )
            )
        }

        // Export Session A only (400 events across 2 chunks: 250 + 150)
        viewModel.selectSession("session-A")
        advanceUntilIdle()

        assertEquals(400L, viewModel.uiState.value.eventCountForScope)

        val outputStream = ByteArrayOutputStream()
        viewModel.executeExport(outputStream, "content://test/export.csv")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertNotNull(state.lastResult)
        assertTrue(state.lastResult is ExportResult.Success)

        val success = state.lastResult as ExportResult.Success
        assertEquals(400L, success.eventCount)
        assertTrue(success.bytesWritten > 0)

        // Verify CSV structure and defense-in-depth sanitization
        val csvString = outputStream.toString("UTF-8")
        val lines = csvString.trimEnd().split("\r\n")

        // 1 header + 400 event lines = 401 lines
        assertEquals(401, lines.size)
        assertTrue(lines[0].startsWith("timestamp,formatted_time,session_id"))

        // Ensure "SuperSecretPassword!" was NEVER exported in plaintext
        assertFalse(csvString.contains("SuperSecretPassword!"))
        // Ensure redacted marker is present
        assertTrue(csvString.contains("[REDACTED]"))

        // Ensure session-B data was not leaked
        assertFalse(csvString.contains("session-B"))
        assertFalse(csvString.contains("Browser"))
    }
}
