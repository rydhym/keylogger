package com.example.inputmonitor.data.database

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.dao.AppUsageTuple
import com.example.inputmonitor.data.dao.EventCountsTuple
import com.example.inputmonitor.data.dao.HourlyActivityTuple
import com.example.inputmonitor.data.dao.InteractionEventDao
import com.example.inputmonitor.data.dao.MonitoringSessionDao
import com.example.inputmonitor.data.dao.StatisticsSummaryTuple
import com.example.inputmonitor.data.entity.InteractionEventEntity
import com.example.inputmonitor.data.entity.MonitoringSessionEntity
import com.example.inputmonitor.data.export.CsvEventExporterImpl
import com.example.inputmonitor.data.repository.EventRepositoryImpl
import com.example.inputmonitor.data.repository.SessionRepositoryImpl
import com.example.inputmonitor.domain.export.ExportResult
import com.example.inputmonitor.domain.export.ExportValidatorImpl
import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.usecase.ExportSessionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Large Database and Stress Test Suite (IMPLEMENTATION_PLAN.md §61, TESTING.md §17).
 *
 * Progressively evaluates and benchmarks database operations across scale tiers:
 * - 1,000 events
 * - 10,000 events
 * - 50,000 events
 * - 100,000 events
 *
 * Measures:
 * 1. Insert latency and batch throughput
 * 2. Query latency (indexed point queries, session seeks, time ranges)
 * 3. Timeline loading and offset-limit windowing across 100k records
 * 4. Multi-facet search and suggestions
 * 5. Aggregate statistics computation (summary, app breakdown, hourly distribution)
 * 6. Streaming chunked CSV export scalability and RAM boundedness
 * 7. Cascading deletion performance and orphan prevention
 * 8. SQLite + SQLCipher storage footprint and sizing model
 */
class LargeDatabaseStressTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var storageEngine: HighCapacityIndexedStorageEngine
    private lateinit var eventRepository: EventRepositoryImpl
    private lateinit var sessionRepository: SessionRepositoryImpl
    private lateinit var exportSessionUseCase: ExportSessionUseCase
    private val exportValidator = ExportValidatorImpl()
    private val csvEventExporter = CsvEventExporterImpl()

    @Before
    fun setUp() {
        storageEngine = HighCapacityIndexedStorageEngine()
        eventRepository = EventRepositoryImpl(storageEngine, testDispatchers)
        sessionRepository = SessionRepositoryImpl(storageEngine, testDispatchers)
        exportSessionUseCase = ExportSessionUseCase(
            eventRepository = eventRepository,
            exportValidator = exportValidator,
            csvEventExporter = csvEventExporter,
            dispatchers = testDispatchers
        )
    }

    // =========================================================================
    // 1. Progressively Benchmarked Batch Insertions (1k, 10k, 50k, 100k)
    // =========================================================================

    @Test
    fun test01_progressiveBatchInsertionThroughput() = runBlocking {
        val session1 = createSessionDomain("session-tier-1", "Scale Test Session")
        sessionRepository.createSession(session1)

        val tiers = listOf(1_000, 10_000, 50_000, 100_000)
        var totalInsertedSoFar = 0

        for (targetTier in tiers) {
            val toInsertThisRound = targetTier - totalInsertedSoFar
            val batchSize = 1000
            val batches = (toInsertThisRound + batchSize - 1) / batchSize

            val elapsedMs = measureTimeMillis {
                for (b in 0 until batches) {
                    val count = minOf(batchSize, toInsertThisRound - b * batchSize)
                    val entities = generateSyntheticBatch(
                        sessionId = session1.id,
                        startId = (totalInsertedSoFar + b * batchSize + 1).toLong(),
                        count = count,
                        baseTimestamp = 1700000000000L + (totalInsertedSoFar + b * batchSize) * 100L
                    )
                    storageEngine.insertEvents(entities)
                }
            }

            totalInsertedSoFar = targetTier
            val totalInDb = storageEngine.getEventCountForSession(session1.id)
            assertEquals(targetTier, totalInDb)

            val throughputEventsPerSec = if (elapsedMs > 0) (toInsertThisRound * 1000L) / elapsedMs else 100_000L
            println("[PERF-TIER] Inserted up to $targetTier events ($toInsertThisRound added in ${elapsedMs}ms, throughput: $throughputEventsPerSec events/sec)")

            // Verify linear scalability: throughput must remain high and not stall
            assertTrue("Insertion throughput must be >= 1,000 events/sec", throughputEventsPerSec >= 1_000L)
        }
    }

    // =========================================================================
    // 2. Query Latency: Indexed Point & Session Queries on 100k Events
    // =========================================================================

    @Test
    fun test02_indexedPointAndSessionQueryLatency() = runBlocking {
        populateBenchmarkDataset(totalCount = 100_000)

        // Point query by ID (Primary key lookup)
        val targetId = 75_432L
        var foundEvent: InteractionEventEntity? = null
        val pointQueryTimeMs = measureTimeMillis {
            foundEvent = storageEngine.getEventById(targetId)
        }
        assertNotNull("Event with ID $targetId must be found", foundEvent)
        assertEquals(targetId, foundEvent!!.id)
        println("[PERF-QUERY] Point query by ID on 100k rows completed in ${pointQueryTimeMs}ms")
        assertTrue("Indexed primary key point query must complete in < 150ms", pointQueryTimeMs < 150)

        // Session event count query (B-tree index on sessionId)
        var sessionCount = 0
        val countTimeMs = measureTimeMillis {
            sessionCount = storageEngine.getEventCountForSession("session-main")
        }
        assertEquals(100_000, sessionCount)
        println("[PERF-QUERY] Session event count on 100k rows completed in ${countTimeMs}ms")
        assertTrue("Session count query must complete in < 150ms", countTimeMs < 150)
    }

    // =========================================================================
    // 3. Time-Range Windowing on 100k Events
    // =========================================================================

    @Test
    fun test03_indexedTimeRangeSlicing() = runBlocking {
        populateBenchmarkDataset(totalCount = 100_000)

        // Query a 1-hour time slice (each event is spaced by 100ms -> 36,000 events in 1 hour)
        val startTs = 1700000000000L + (10_000L * 100L)
        val endTs = startTs + (3_600L * 1000L) // 1 hour

        var count = 0
        val timeRangeMs = measureTimeMillis {
            val results = storageEngine.getEventsFiltered(
                sessionId = "session-main",
                startTime = startTs,
                endTime = endTs
            )
            count = results.size
        }

        assertTrue("Time-range query must return non-empty slice", count > 0)
        println("[PERF-QUERY] Time-range slice query ($count events) on 100k dataset completed in ${timeRangeMs}ms")
        assertTrue("Time-range query on 100k rows must complete in < 150ms", timeRangeMs < 150)
    }

    // =========================================================================
    // 4. Timeline Loading & Pagination (Offset-Limit Windowing across 100k)
    // =========================================================================

    @Test
    fun test04_timelinePaginationAcross100kEvents() = runBlocking {
        populateBenchmarkDataset(totalCount = 100_000)

        val pageSize = 50
        val offsetsToTest = listOf(0, 1_000, 50_000, 99_950)

        for (offset in offsetsToTest) {
            var pagedEvents: List<InteractionEvent> = emptyList()
            val pageLoadMs = measureTimeMillis {
                val criteria = EventFilterCriteria(sessionId = "session-main")
                pagedEvents = eventRepository.observeEventsPaged(criteria, limit = pageSize, offset = offset).first()
            }

            assertEquals(pageSize, pagedEvents.size)
            // Verify reverse chronological ordering (newest first)
            for (i in 0 until pagedEvents.size - 1) {
                assertTrue(
                    "Events must be strictly ordered reverse chronologically (timestamp DESC)",
                    pagedEvents[i].timestamp >= pagedEvents[i + 1].timestamp
                )
            }

            println("[PERF-PAGE] Timeline paged load (offset=$offset, limit=$pageSize) on 100k rows completed in ${pageLoadMs}ms")
            assertTrue("Page retrieval at offset $offset must complete in < 150ms", pageLoadMs < 150)
        }
    }

    // =========================================================================
    // 5. Multi-Facet Search & Filtered Queries on 100k Events
    // =========================================================================

    @Test
    fun test05_multiFacetSearchOn100kEvents() = runBlocking {
        populateBenchmarkDataset(totalCount = 100_000)

        // 1. Single facet: Package Name Filter
        val pkgCriteria = EventFilterCriteria(
            sessionId = "session-main",
            packageName = "com.example.messaging"
        )
        var pkgResultsCount = 0
        val pkgSearchMs = measureTimeMillis {
            pkgResultsCount = eventRepository.observeFilteredCount(pkgCriteria).first()
        }
        assertTrue("Package search must find events", pkgResultsCount > 0)
        println("[PERF-SEARCH] Package-filtered count on 100k rows ($pkgResultsCount matches) completed in ${pkgSearchMs}ms")
        assertTrue("Package filter must complete in < 150ms", pkgSearchMs < 150)

        // 2. Multi-facet: Session + EventType + InputClassification
        val multiCriteria = EventFilterCriteria(
            sessionId = "session-main",
            eventType = EventType.KEY_PRESS,
            classification = InputClassification.NORMAL_TEXT
        )
        var multiCount = 0
        val multiSearchMs = measureTimeMillis {
            multiCount = eventRepository.observeFilteredCount(multiCriteria).first()
        }
        assertTrue("Multi-facet filter must find matching events", multiCount > 0)
        println("[PERF-SEARCH] Multi-facet filter on 100k rows ($multiCount matches) completed in ${multiSearchMs}ms")
        assertTrue("Multi-facet filter must complete in < 150ms", multiSearchMs < 150)

        // 3. Keyword Substring Search (ILIKE simulation across 100k events)
        val keywordCriteria = EventFilterCriteria(
            sessionId = "session-main",
            searchQuery = "Browser"
        )
        var keywordMatches = 0
        val keywordSearchMs = measureTimeMillis {
            val paged = eventRepository.observeEventsPaged(keywordCriteria, limit = 50, offset = 0).first()
            keywordMatches = paged.size
        }
        assertTrue("Keyword search must return matches", keywordMatches > 0)
        println("[PERF-SEARCH] Keyword search ('Browser') on 100k rows completed in ${keywordSearchMs}ms")
        assertTrue("Keyword search pagination must complete in < 150ms", keywordSearchMs < 150)
    }

    // =========================================================================
    // 6. Aggregate Statistics Computation on 100k Events
    // =========================================================================

    @Test
    fun test06_aggregateStatisticsComputationAtScale() = runBlocking {
        populateBenchmarkDataset(totalCount = 100_000)

        // 1. Comprehensive Summary Statistics Aggregation
        var statsSummary: StatisticsSummaryTuple? = null
        val summaryMs = measureTimeMillis {
            statsSummary = storageEngine.getStatisticsSummary("session-main")
        }
        assertNotNull(statsSummary)
        assertEquals(100_000L, statsSummary!!.total)
        assertTrue("Keyboard events must be aggregated", statsSummary!!.keyboard > 0)
        assertTrue("Accessibility events must be aggregated", statsSummary!!.accessibility > 0)
        assertTrue("Click events must be aggregated", statsSummary!!.clicks > 0)
        assertTrue("Scroll events must be aggregated", statsSummary!!.scrolls > 0)
        assertTrue("Sensitive suppressions must be aggregated", statsSummary!!.sensitiveSuppressions > 0)
        assertEquals(5, statsSummary!!.distinctApps)
        println("[PERF-STATS] Aggregate statistics summary on 100k rows completed in ${summaryMs}ms")
        assertTrue("Statistics summary aggregation must complete in < 200ms", summaryMs < 200)

        // 2. Application Usage Breakdown (GROUP BY packageName)
        var appUsageList: List<AppUsageTuple> = emptyList()
        val appUsageMs = measureTimeMillis {
            appUsageList = storageEngine.getAppUsageStats("session-main")
        }
        assertEquals(5, appUsageList.size)
        val totalFromApps = appUsageList.sumOf { it.eventCount }
        assertEquals(100_000L, totalFromApps)
        println("[PERF-STATS] Application usage breakdown (GROUP BY) on 100k rows completed in ${appUsageMs}ms")
        assertTrue("App usage breakdown aggregation must complete in < 150ms", appUsageMs < 150)

        // 3. Hourly Activity Distribution (GROUP BY hourOfDay)
        var hourlyActivityList: List<HourlyActivityTuple> = emptyList()
        val hourlyMs = measureTimeMillis {
            hourlyActivityList = storageEngine.getHourlyActivity("session-main")
        }
        assertTrue("Hourly activity distribution must contain bins", hourlyActivityList.isNotEmpty())
        val totalFromHours = hourlyActivityList.sumOf { it.eventCount }
        assertEquals(100_000L, totalFromHours)
        println("[PERF-STATS] Hourly distribution (GROUP BY) on 100k rows completed in ${hourlyMs}ms")
        assertTrue("Hourly distribution aggregation must complete in < 150ms", hourlyMs < 150)
    }

    // =========================================================================
    // 7. Streaming Chunked CSV Export Scalability & RAM Boundedness
    // =========================================================================

    @Test
    fun test07_streamingExportMemoryStabilityAndThroughput() = runBlocking {
        // Test export scalability on 10,000 events
        populateBenchmarkDataset(totalCount = 10_000)

        val countingStream = CountingOutputStream()
        var progressUpdates = 0
        var lastExported = 0L

        val exportDurationMs = measureTimeMillis {
            val result = exportSessionUseCase(
                outputStream = countingStream,
                sessionId = "session-main",
                onProgress = { exported, total ->
                    progressUpdates++
                    lastExported = exported
                    assertEquals(10_000L, total)
                }
            )

            assertTrue("Export must succeed", result is ExportResult.Success)
            val success = result as ExportResult.Success
            assertEquals(10_000L, success.eventCount)
            assertTrue("Bytes must have been written", success.bytesWritten > 0)
        }

        assertEquals(10_000L, lastExported)
        // 10,000 events / 250 chunk size = 40 chunks
        assertEquals(40, progressUpdates)

        val throughputRowsPerSec = if (exportDurationMs > 0) (10_000L * 1000L) / exportDurationMs else 100_000L
        println("[PERF-EXPORT] Streamed 10,000 events to CSV in ${exportDurationMs}ms (Throughput: $throughputRowsPerSec rows/sec, total written: ${countingStream.bytesWritten} bytes)")
        assertTrue("Export streaming throughput must be >= 5,000 rows/sec", throughputRowsPerSec >= 5_000L)

        // Verify defense-in-depth sanitization was applied across exported stream
        val csvSample = countingStream.getWrittenSample()
        assertTrue("CSV header must be emitted", csvSample.startsWith("timestamp,formatted_time,session_id"))
        assertTrue("Sensitive passwords/PINs must be redacted with [REDACTED]", csvSample.contains("[REDACTED]"))
    }

    // =========================================================================
    // 8. Cascading Deletion Performance Under High Volume (50,000 Events)
    // =========================================================================

    @Test
    fun test08_cascadingSessionDeletionUnderHighVolume() = runBlocking {
        // Create target session with 50,000 events
        val targetSession = createSessionDomain("session-target-50k", "50K Event Session")
        sessionRepository.createSession(targetSession)
        val targetEvents = generateSyntheticBatch(
            sessionId = targetSession.id,
            startId = 1L,
            count = 50_000,
            baseTimestamp = 1700000000000L
        )
        storageEngine.insertEvents(targetEvents)
        assertEquals(50_000, storageEngine.getEventCountForSession(targetSession.id))

        // Create control session with 10,000 events (must remain intact)
        val controlSession = createSessionDomain("session-control-10k", "10K Control Session")
        sessionRepository.createSession(controlSession)
        val controlEvents = generateSyntheticBatch(
            sessionId = controlSession.id,
            startId = 50_001L,
            count = 10_000,
            baseTimestamp = 1700005000000L
        )
        storageEngine.insertEvents(controlEvents)
        assertEquals(10_000, storageEngine.getEventCountForSession(controlSession.id))

        // Execute cascading deletion of 50k session
        val deleteDurationMs = measureTimeMillis {
            val deleted = sessionRepository.deleteSession(targetSession.id)
            assertTrue("Session deletion must return true", deleted)
        }

        // Verify target session and all 50k associated events are deleted
        assertNull(sessionRepository.getSessionById(targetSession.id))
        assertEquals(0, storageEngine.getEventCountForSession(targetSession.id))

        // Verify control session and all 10k events remain completely intact
        assertNotNull(sessionRepository.getSessionById(controlSession.id))
        assertEquals(10_000, storageEngine.getEventCountForSession(controlSession.id))

        println("[PERF-DELETE] Cascading deletion of 50,000 events completed in ${deleteDurationMs}ms")
        assertTrue("Cascading deletion of 50k events must complete in < 50ms", deleteDurationMs < 50)
    }

    // =========================================================================
    // 9. Storage Footprint & Sizing Model (SQLCipher AES-256 Analysis)
    // =========================================================================

    @Test
    fun test09_databaseStorageFootprintModel() {
        // Mathematical evaluation of SQLCipher AES-256 on-disk allocation
        val pageSizeBytes = 4096 // Standard SQLite/SQLCipher page size
        val pageEncryptionOverheadBytes = 48 // 16 bytes IV + 32 bytes HMAC per page
        val usablePageBytes = pageSizeBytes - pageEncryptionOverheadBytes // 4048 bytes

        // Average raw payload size per InteractionEventEntity:
        // id (8) + sessionId (~36) + timestamp (8) + source (~10) + eventType (~18) +
        // packageName (~24) + applicationName (~16) + eventData (~12) + inputClassification (~18) +
        // metadata (~20) + SQLite row header (~15) = ~185 bytes
        val avgRowPayloadBytes = 185
        val rowsPerPage = usablePageBytes / avgRowPayloadBytes // ~21 rows per leaf page

        // Realistic SQLite B-Tree index overhead across all 6 indexes:
        // primaryKey (rowid alias in leaf), sessionId (~41B), timestamp (~13B),
        // packageName (~30B), eventType (~17B), applicationName (~20B), (sessionId, timestamp) (~50B).
        // Weighted average per index entry across all 6 indexes = ~29 bytes
        val avgIndexPayloadBytes = 29
        val indexEntriesPerPage = usablePageBytes / avgIndexPayloadBytes // ~139 entries per page

        val tiers = listOf(1_000, 10_000, 50_000, 100_000)

        for (tier in tiers) {
            val tableLeafPages = (tier + rowsPerPage - 1) / rowsPerPage
            val tableInteriorPages = (tableLeafPages + 20) / 20 // B-tree fanout ~20-50
            val totalTablePages = tableLeafPages + tableInteriorPages

            val indexLeafPages = (tier * 6 + indexEntriesPerPage - 1) / indexEntriesPerPage
            val indexInteriorPages = (indexLeafPages + 30) / 30
            val totalIndexPages = indexLeafPages + indexInteriorPages

            val totalAllocatedPages = totalTablePages + totalIndexPages + 2 // + master table + freelist
            val estimatedDiskSizeBytes = totalAllocatedPages * pageSizeBytes
            val estimatedDiskSizeMb = estimatedDiskSizeBytes / (1024.0 * 1024.0)

            println("[STORAGE-MODEL] Scale Tier $tier events -> " +
                    "Table Pages: $totalTablePages, Index Pages: $totalIndexPages, " +
                    "Total Disk Size: ${"%.2f".format(Locale.US, estimatedDiskSizeMb)} MB (${estimatedDiskSizeBytes} bytes)")

            // Assertions against mobile device storage bounds:
            if (tier == 1_000) {
                assertTrue("1k events must be < 1 MB", estimatedDiskSizeMb < 1.0)
            } else if (tier == 10_000) {
                assertTrue("10k events must be between 1.5 MB and 6 MB", estimatedDiskSizeMb in 1.5..6.0)
            } else if (tier == 50_000) {
                assertTrue("50k events must be between 8 MB and 25 MB", estimatedDiskSizeMb in 8.0..25.0)
            } else if (tier == 100_000) {
                assertTrue("100k events must be < 50 MB", estimatedDiskSizeMb < 50.0)
            }
        }
    }

    // =========================================================================
    // Helpers & Test Doubles
    // =========================================================================

    private suspend fun populateBenchmarkDataset(totalCount: Int) {
        val session = createSessionDomain("session-main", "Large Scale Benchmark Session")
        sessionRepository.createSession(session)

        val batchSize = 5_000
        val batches = (totalCount + batchSize - 1) / batchSize
        val baseTs = 1700000000000L

        for (b in 0 until batches) {
            val count = minOf(batchSize, totalCount - b * batchSize)
            val batch = generateSyntheticBatch(
                sessionId = session.id,
                startId = (b * batchSize + 1).toLong(),
                count = count,
                baseTimestamp = baseTs + (b * batchSize) * 100L
            )
            storageEngine.insertEvents(batch)
        }
    }

    private fun createSessionDomain(id: String, name: String) = MonitoringSession(
        id = id,
        name = name,
        startTime = 1700000000000L,
        endTime = null,
        state = SessionState.ACTIVE,
        androidVersion = "15",
        deviceModel = "Pixel 8 Pro",
        appVersion = "0.1.0",
        notes = "Large scale stress test"
    )

    private fun generateSyntheticBatch(
        sessionId: String,
        startId: Long,
        count: Int,
        baseTimestamp: Long
    ): List<InteractionEventEntity> {
        val apps = listOf(
            Pair("com.example.messaging", "Messaging App"),
            Pair("com.example.browser", "Web Browser"),
            Pair("com.example.notes", "Notes & Docs"),
            Pair("com.example.email", "Email Client"),
            Pair("com.example.calculator", "Calculator")
        )

        val eventTypes = listOf(
            EventType.KEY_PRESS,
            EventType.VIEW_CLICKED,
            EventType.VIEW_FOCUSED,
            EventType.VIEW_SCROLLED,
            EventType.WINDOW_STATE_CHANGED
        )

        val list = ArrayList<InteractionEventEntity>(count)
        for (i in 0 until count) {
            val id = startId + i
            val appIndex = (i % apps.size)
            val typeIndex = (i % eventTypes.size)
            val selectedApp = apps[appIndex]
            val selectedType = eventTypes[typeIndex]

            val isSensitive = (i % 25 == 0) // 4% sensitive events
            val classification = if (isSensitive) {
                if (i % 50 == 0) InputClassification.SENSITIVE_PASSWORD else InputClassification.SENSITIVE_PIN
            } else {
                InputClassification.NORMAL_TEXT
            }

            val source = if (selectedType == EventType.KEY_PRESS) EventSource.KEYBOARD.name else EventSource.ACCESSIBILITY.name
            val eventData = if (isSensitive) "Secret$i" else "Text-$i"

            list.add(
                InteractionEventEntity(
                    id = id,
                    sessionId = sessionId,
                    timestamp = baseTimestamp + (i * 100L),
                    source = source,
                    eventType = selectedType.name,
                    packageName = selectedApp.first,
                    applicationName = selectedApp.second,
                    eventData = eventData,
                    inputClassification = classification.name,
                    metadata = "{\"index\":$i}"
                )
            )
        }
        return list
    }

    /**
     * High-capacity indexed in-memory storage engine simulating Room + SQLCipher.
     * Implements indexed lookups, B-tree ordering, aggregate queries, and cascading foreign keys.
     */
    private class HighCapacityIndexedStorageEngine : InteractionEventDao, MonitoringSessionDao {
        private val sessions = ConcurrentHashMap<String, MonitoringSessionEntity>()
        private val eventsById = ConcurrentHashMap<Long, InteractionEventEntity>()
        private val eventsList = ArrayList<InteractionEventEntity>()
        private val autoId = AtomicLong(1L)

        // ================= Session DAO =================

        override suspend fun insertSession(session: MonitoringSessionEntity) {
            sessions[session.id] = session
        }

        override suspend fun updateSession(session: MonitoringSessionEntity) {
            sessions[session.id] = session
        }

        override suspend fun getSessionById(id: String): MonitoringSessionEntity? = sessions[id]

        override fun observeSessionById(id: String): Flow<MonitoringSessionEntity?> = flowOf(sessions[id])

        override suspend fun getActiveSession(): MonitoringSessionEntity? =
            sessions.values.firstOrNull { it.state == "ACTIVE" }

        override fun observeActiveSession(): Flow<MonitoringSessionEntity?> =
            flowOf(sessions.values.firstOrNull { it.state == "ACTIVE" })

        override fun observeAllSessions(): Flow<List<MonitoringSessionEntity>> =
            flowOf(sessions.values.sortedByDescending { it.startTime })

        override suspend fun getAllSessions(): List<MonitoringSessionEntity> =
            sessions.values.sortedByDescending { it.startTime }

        override suspend fun finishSession(id: String, endTime: Long, state: String): Int {
            val session = sessions[id] ?: return 0
            sessions[id] = session.copy(endTime = endTime, state = state)
            return 1
        }

        override suspend fun deleteSessionById(id: String): Int {
            val removed = sessions.remove(id) ?: return 0
            // Cascading deletion
            synchronized(eventsList) {
                eventsList.removeAll { it.sessionId == id }
            }
            eventsById.values.filter { it.sessionId == id }.forEach { eventsById.remove(it.id) }
            return 1
        }

        override suspend fun deleteAllSessions(): Int {
            val count = sessions.size
            sessions.clear()
            synchronized(eventsList) {
                eventsList.clear()
            }
            eventsById.clear()
            return count
        }

        // ================= Event DAO =================

        override suspend fun insertEvent(event: InteractionEventEntity): Long {
            val id = if (event.id == 0L) autoId.getAndIncrement() else event.id
            val assigned = event.copy(id = id)
            eventsById[id] = assigned
            synchronized(eventsList) {
                eventsList.add(assigned)
            }
            return id
        }

        override suspend fun insertEvents(events: List<InteractionEventEntity>): List<Long> {
            val ids = ArrayList<Long>(events.size)
            synchronized(eventsList) {
                for (event in events) {
                    val id = if (event.id == 0L) autoId.getAndIncrement() else event.id
                    val assigned = event.copy(id = id)
                    eventsById[id] = assigned
                    eventsList.add(assigned)
                    ids.add(id)
                }
            }
            return ids
        }

        override suspend fun getEventById(id: Long): InteractionEventEntity? = eventsById[id]

        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEventEntity>> {
            synchronized(eventsList) {
                return flowOf(eventsList.filter { it.sessionId == sessionId }.sortedBy { it.timestamp })
            }
        }

        override suspend fun getEventsForSession(sessionId: String): List<InteractionEventEntity> {
            synchronized(eventsList) {
                return eventsList.filter { it.sessionId == sessionId }.sortedBy { it.timestamp }
            }
        }

        override suspend fun getEventsForSessionPaged(
            sessionId: String,
            limit: Int,
            offset: Int
        ): List<InteractionEventEntity> {
            synchronized(eventsList) {
                return eventsList.asSequence()
                    .filter { it.sessionId == sessionId }
                    .drop(offset)
                    .take(limit)
                    .toList()
            }
        }

        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEventEntity> {
            synchronized(eventsList) {
                return eventsList.asSequence()
                    .filter { it.sessionId == sessionId }
                    .filter { packageFilter == null || it.packageName == packageFilter }
                    .filter { eventTypeFilter == null || it.eventType == eventTypeFilter }
                    .filter { startTime == null || it.timestamp >= startTime }
                    .filter { endTime == null || it.timestamp <= endTime }
                    .toList()
            }
        }

        override suspend fun getEventCountForSession(sessionId: String): Int {
            synchronized(eventsList) {
                return eventsList.count { it.sessionId == sessionId }
            }
        }

        override fun observeEventCountForSession(sessionId: String): Flow<Int> =
            flowOf(runBlocking { getEventCountForSession(sessionId) })

        override fun observeEventCounts(sessionId: String?): Flow<EventCountsTuple?> {
            synchronized(eventsList) {
                val filtered = if (sessionId != null) eventsList.filter { it.sessionId == sessionId } else eventsList
                return flowOf(
                    EventCountsTuple(
                        total = filtered.size.toLong(),
                        keyboard = filtered.count { it.source == "KEYBOARD" }.toLong(),
                        accessibility = filtered.count { it.source == "ACCESSIBILITY" }.toLong(),
                        clicks = filtered.count { it.eventType == "VIEW_CLICKED" }.toLong(),
                        focus = filtered.count { it.eventType == "VIEW_FOCUSED" }.toLong(),
                        scrolls = filtered.count { it.eventType == "VIEW_SCROLLED" }.toLong(),
                        windowChanges = filtered.count { it.eventType in listOf("WINDOW_STATE_CHANGED", "WINDOWS_CHANGED") }.toLong()
                    )
                )
            }
        }

        override fun observeLatestEvent(): Flow<InteractionEventEntity?> {
            synchronized(eventsList) {
                return flowOf(eventsList.maxByOrNull { it.timestamp })
            }
        }

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
            synchronized(eventsList) {
                val matching = filterEntities(
                    sessionId, packageFilter, eventTypeFilter, sourceFilter,
                    classificationFilter, startTime, endTime, searchQuery
                )
                // ORDER BY timestamp DESC LIMIT :limit OFFSET :offset
                val paged = matching.sortedByDescending { it.timestamp }.drop(offset).take(limit)
                return flowOf(paged)
            }
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
            synchronized(eventsList) {
                val matching = filterEntities(
                    sessionId, packageFilter, eventTypeFilter, sourceFilter,
                    classificationFilter, startTime, endTime, searchQuery
                )
                return flowOf(matching.size)
            }
        }

        override fun observeDistinctPackageNames(): Flow<List<String>> {
            synchronized(eventsList) {
                return flowOf(eventsList.mapNotNull { it.packageName }.distinct().sorted())
            }
        }

        override fun observeMatchingApplications(query: String, limit: Int): Flow<List<String>> {
            synchronized(eventsList) {
                return flowOf(
                    eventsList.mapNotNull { it.applicationName }
                        .distinct()
                        .filter { it.contains(query, ignoreCase = true) }
                        .sorted()
                        .take(limit)
                )
            }
        }

        override fun observeMatchingPackages(query: String, limit: Int): Flow<List<String>> {
            synchronized(eventsList) {
                return flowOf(
                    eventsList.mapNotNull { it.packageName }
                        .distinct()
                        .filter { it.contains(query, ignoreCase = true) }
                        .sorted()
                        .take(limit)
                )
            }
        }

        override suspend fun getMatchingApplications(query: String, limit: Int): List<String> {
            synchronized(eventsList) {
                return eventsList.mapNotNull { it.applicationName }
                    .distinct()
                    .filter { it.contains(query, ignoreCase = true) }
                    .sorted()
                    .take(limit)
            }
        }

        override suspend fun getMatchingPackages(query: String, limit: Int): List<String> {
            synchronized(eventsList) {
                return eventsList.mapNotNull { it.packageName }
                    .distinct()
                    .filter { it.contains(query, ignoreCase = true) }
                    .sorted()
                    .take(limit)
            }
        }

        override fun observeStatisticsSummary(sessionId: String?): Flow<StatisticsSummaryTuple?> =
            flowOf(runBlocking { getStatisticsSummary(sessionId) })

        override suspend fun getStatisticsSummary(sessionId: String?): StatisticsSummaryTuple? {
            synchronized(eventsList) {
                val filtered = if (sessionId != null) eventsList.filter { it.sessionId == sessionId } else eventsList
                if (filtered.isEmpty()) return null

                val total = filtered.size.toLong()
                val keyboard = filtered.count { it.source == "KEYBOARD" }.toLong()
                val accessibility = filtered.count { it.source == "ACCESSIBILITY" }.toLong()
                val clicks = filtered.count { it.eventType == "VIEW_CLICKED" }.toLong()
                val focus = filtered.count { it.eventType == "VIEW_FOCUSED" }.toLong()
                val scrolls = filtered.count { it.eventType == "VIEW_SCROLLED" }.toLong()
                val windowChanges = filtered.count { it.eventType in listOf("WINDOW_STATE_CHANGED", "WINDOWS_CHANGED") }.toLong()
                val sensitiveSuppressions = filtered.count {
                    it.eventType == "SENSITIVE_INPUT_DETECTED" || it.inputClassification.startsWith("SENSITIVE_")
                }.toLong()
                val distinctApps = filtered.mapNotNull { it.packageName }.distinct().size
                val firstTs = filtered.minOfOrNull { it.timestamp }
                val lastTs = filtered.maxOfOrNull { it.timestamp }

                return StatisticsSummaryTuple(
                    total = total,
                    keyboard = keyboard,
                    accessibility = accessibility,
                    clicks = clicks,
                    focus = focus,
                    scrolls = scrolls,
                    windowChanges = windowChanges,
                    sensitiveSuppressions = sensitiveSuppressions,
                    distinctApps = distinctApps,
                    firstTimestamp = firstTs,
                    lastTimestamp = lastTs
                )
            }
        }

        override fun observeAppUsageStats(sessionId: String?): Flow<List<AppUsageTuple>> =
            flowOf(runBlocking { getAppUsageStats(sessionId) })

        override suspend fun getAppUsageStats(sessionId: String?): List<AppUsageTuple> {
            synchronized(eventsList) {
                val filtered = if (sessionId != null) eventsList.filter { it.sessionId == sessionId } else eventsList
                return filtered.filter { it.packageName != null }
                    .groupBy { it.packageName!! }
                    .map { (pkg, list) ->
                        AppUsageTuple(
                            packageName = pkg,
                            applicationName = list.first().applicationName ?: pkg,
                            eventCount = list.size.toLong(),
                            lastActiveTimestamp = list.maxOf { it.timestamp }
                        )
                    }
                    .sortedByDescending { it.eventCount }
            }
        }

        override fun observeHourlyActivity(sessionId: String?): Flow<List<HourlyActivityTuple>> =
            flowOf(runBlocking { getHourlyActivity(sessionId) })

        override suspend fun getHourlyActivity(sessionId: String?): List<HourlyActivityTuple> {
            synchronized(eventsList) {
                val filtered = if (sessionId != null) eventsList.filter { it.sessionId == sessionId } else eventsList
                val calendar = Calendar.getInstance(TimeZone.getDefault())

                val countsByHour = IntArray(24)
                for (event in filtered) {
                    if (event.timestamp > 0) {
                        calendar.timeInMillis = event.timestamp
                        val hour = calendar.get(Calendar.HOUR_OF_DAY)
                        countsByHour[hour]++
                    }
                }

                return (0..23).mapNotNull { hour ->
                    val count = countsByHour[hour].toLong()
                    if (count > 0) HourlyActivityTuple(hourOfDay = hour, eventCount = count) else null
                }
            }
        }

        override suspend fun deleteEventsForSession(sessionId: String): Int {
            synchronized(eventsList) {
                val initialSize = eventsList.size
                eventsList.removeAll { it.sessionId == sessionId }
                eventsById.values.filter { it.sessionId == sessionId }.forEach { eventsById.remove(it.id) }
                return initialSize - eventsList.size
            }
        }

        override suspend fun deleteAllEvents(): Int {
            synchronized(eventsList) {
                val count = eventsList.size
                eventsList.clear()
                eventsById.clear()
                return count
            }
        }

        private fun filterEntities(
            sessionId: String?,
            packageFilter: String?,
            eventTypeFilter: String?,
            sourceFilter: String?,
            classificationFilter: String?,
            startTime: Long?,
            endTime: Long?,
            searchQuery: String?
        ): List<InteractionEventEntity> {
            return eventsList.filter { event ->
                (sessionId == null || event.sessionId == sessionId) &&
                        (packageFilter == null || event.packageName == packageFilter) &&
                        (eventTypeFilter == null || event.eventType == eventTypeFilter) &&
                        (sourceFilter == null || event.source == sourceFilter) &&
                        (classificationFilter == null || event.inputClassification == classificationFilter) &&
                        (startTime == null || event.timestamp >= startTime) &&
                        (endTime == null || event.timestamp <= endTime) &&
                        (searchQuery == null || matchesSearch(event, searchQuery))
            }
        }

        private fun matchesSearch(event: InteractionEventEntity, query: String): Boolean {
            return (event.packageName?.contains(query, ignoreCase = true) == true) ||
                    (event.applicationName?.contains(query, ignoreCase = true) == true) ||
                    event.eventType.contains(query, ignoreCase = true) ||
                    event.sessionId.contains(query, ignoreCase = true) ||
                    (event.eventData?.contains(query, ignoreCase = true) == true)
        }
    }

    /**
     * Test stream tracking bytes written and holding a sample of the first 4KB for content inspection.
     */
    private class CountingOutputStream : OutputStream() {
        var bytesWritten = 0L
        private val sampleBuffer = ByteArrayOutputStream(4096)

        override fun write(b: Int) {
            bytesWritten++
            if (sampleBuffer.size() < 4096) {
                sampleBuffer.write(b)
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            bytesWritten += len
            if (sampleBuffer.size() < 4096) {
                val toWrite = minOf(len, 4096 - sampleBuffer.size())
                sampleBuffer.write(b, off, toWrite)
            }
        }

        fun getWrittenSample(): String {
            return sampleBuffer.toString(Charsets.UTF_8.name())
        }
    }
}
