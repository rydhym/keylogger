package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.export.CsvEventExporterImpl
import com.example.inputmonitor.domain.export.ExportResult
import com.example.inputmonitor.domain.export.ExportValidatorImpl
import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.repository.EventRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Unit tests for [ExportSessionUseCase] verifying streaming pagination, chunked export,
 * progress callback delivery, and defense-in-depth sanitization.
 */
class ExportSessionUseCaseTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeEventRepository : EventRepository {
        val events = mutableListOf<InteractionEvent>()

        override suspend fun insertEvent(event: InteractionEvent): Long {
            events.add(event)
            return events.size.toLong()
        }

        override suspend fun insertEvents(events: List<InteractionEvent>): List<Long> = emptyList()
        override suspend fun getEventById(id: Long): InteractionEvent? = null
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEvent> = emptyList()
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent> = emptyList()
        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEvent> = emptyList()
        override suspend fun getEventCountForSession(sessionId: String): Int = events.count { it.sessionId == sessionId }
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(events.count { it.sessionId == sessionId })
        override fun observeEventCounts(sessionId: String?): Flow<com.example.inputmonitor.domain.model.EventCounts> =
            flowOf(com.example.inputmonitor.domain.model.EventCounts(total = events.size.toLong()))
        override fun observeLatestEvent(): Flow<InteractionEvent?> = flowOf(null)

        override fun observeEventsPaged(
            filter: EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> {
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

    private lateinit var repository: FakeEventRepository
    private lateinit var useCase: ExportSessionUseCase

    @Before
    fun setUp() {
        repository = FakeEventRepository()
        useCase = ExportSessionUseCase(
            eventRepository = repository,
            exportValidator = ExportValidatorImpl(),
            csvEventExporter = CsvEventExporterImpl(),
            dispatchers = testDispatchers
        )
    }

    @Test
    fun executeExport_emptyDatabase_emitsHeaderAndSuccessZeroEvents() = runBlocking {
        val outputStream = ByteArrayOutputStream()
        var progressCalled = false

        val result = useCase(
            outputStream = outputStream,
            sessionId = null,
            onProgress = { exported, total ->
                progressCalled = true
                assertEquals(0L, exported)
                assertEquals(0L, total)
            }
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(0L, success.eventCount)
        assertTrue(success.bytesWritten > 0) // Header written

        val output = outputStream.toString("UTF-8")
        assertTrue(output.startsWith("timestamp,formatted_time,session_id"))
        assertTrue(progressCalled)
    }

    @Test
    fun executeExport_streamsAndRedactsSensitiveEvents() = runBlocking {
        repository.insertEvent(
            InteractionEvent(
                sessionId = "session-1",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.notes",
                applicationName = "Notes",
                eventData = "Hello Note",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        repository.insertEvent(
            InteractionEvent(
                sessionId = "session-1",
                timestamp = 2000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.bank",
                applicationName = "Bank App",
                eventData = "SecretPassword123!",
                inputClassification = InputClassification.SENSITIVE_PASSWORD
            )
        )

        val outputStream = ByteArrayOutputStream()
        var lastExported = 0L

        val result = useCase(
            outputStream = outputStream,
            sessionId = "session-1",
            onProgress = { exported, total ->
                lastExported = exported
                assertEquals(2L, total)
            }
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(2L, success.eventCount)
        assertEquals(2L, lastExported)

        val csvOutput = outputStream.toString("UTF-8")
        assertTrue(csvOutput.contains("Hello Note"))
        assertTrue(csvOutput.contains("[REDACTED]"))
        assertTrue(!csvOutput.contains("SecretPassword123!"))
    }

    @Test
    fun executeExport_filtersBySessionId() = runBlocking {
        repository.insertEvent(
            InteractionEvent(
                sessionId = "sess-A",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.app",
                applicationName = "App A",
                eventData = "A data",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        repository.insertEvent(
            InteractionEvent(
                sessionId = "sess-B",
                timestamp = 2000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.app",
                applicationName = "App B",
                eventData = "B data",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        val outputStream = ByteArrayOutputStream()
        val result = useCase(
            outputStream = outputStream,
            sessionId = "sess-A"
        )

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(1L, success.eventCount)

        val csvOutput = outputStream.toString("UTF-8")
        assertTrue(csvOutput.contains("A data"))
        assertTrue(!csvOutput.contains("B data"))
    }
}
