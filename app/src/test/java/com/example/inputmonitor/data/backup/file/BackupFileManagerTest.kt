package com.example.inputmonitor.data.backup.file

import android.content.Context
import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.export.CsvEventExporterImpl
import com.example.inputmonitor.domain.export.ExportValidatorImpl
import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.usecase.ExportSessionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.OutputStream
import java.nio.file.Files

class BackupFileManagerTest {

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

    private lateinit var tempDir: File
    private lateinit var fakeContext: Context
    private lateinit var repository: FakeEventRepository
    private lateinit var exportUseCase: ExportSessionUseCase
    private lateinit var backupFileManager: BackupFileManager

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("backup_test").toFile()

        fakeContext = object : android.content.ContextWrapper(null) {
            override fun getCacheDir(): File = tempDir
        }

        repository = FakeEventRepository()
        exportUseCase = ExportSessionUseCase(
            eventRepository = repository,
            exportValidator = ExportValidatorImpl(),
            csvEventExporter = CsvEventExporterImpl(),
            dispatchers = testDispatchers
        )

        backupFileManager = BackupFileManager(
            context = fakeContext,
            exportSessionUseCase = exportUseCase,
            dispatchers = testDispatchers
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun createBackupFile_emptyData_generatesValidCsvWithHeaders() = runBlocking {
        val result = backupFileManager.createBackupFile()

        assertTrue(result is BackupFileResult.Success)
        val success = result as BackupFileResult.Success
        assertTrue(success.file.exists())
        assertEquals(0L, success.eventCount)
        assertTrue(success.bytesWritten > 0L)

        val content = success.file.readText()
        assertTrue(content.startsWith("timestamp,formatted_time,session_id"))

        // Cleanup
        assertTrue(backupFileManager.deleteBackupFile(success.file))
        assertFalse(success.file.exists())
    }

    @Test
    fun createBackupFile_withEvents_writesRedactedRowsAndAtomicFile() = runBlocking {
        repository.insertEvent(
            InteractionEvent(
                sessionId = "test-session",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.test.notes",
                applicationName = "Notes",
                eventData = "Daily Notes",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )
        repository.insertEvent(
            InteractionEvent(
                sessionId = "test-session",
                timestamp = 2000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.test.bank",
                applicationName = "Bank",
                eventData = "TopSecret123",
                inputClassification = InputClassification.SENSITIVE_PASSWORD
            )
        )

        val result = backupFileManager.createBackupFile(sessionId = "test-session")

        assertTrue(result is BackupFileResult.Success)
        val success = result as BackupFileResult.Success
        assertTrue(success.file.name.contains("test-session"))
        assertEquals(2L, success.eventCount)

        val content = success.file.readText()
        assertTrue(content.contains("Daily Notes"))
        assertTrue(content.contains("[REDACTED]"))
        assertFalse(content.contains("TopSecret123"))

        // Verify no leftover .tmp files exist
        val backupDir = File(tempDir, BackupFileManager.BACKUP_DIR_NAME)
        val tmpFiles = backupDir.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals(0, tmpFiles.size)
    }

    @Test
    fun deleteStaleBackups_removesOldFilesAndAbandonedTmpFiles() = runBlocking {
        val backupDir = File(tempDir, BackupFileManager.BACKUP_DIR_NAME).apply { mkdirs() }

        // Fresh file (should be kept)
        val freshFile = File(backupDir, "backup_fresh.csv").apply {
            writeText("data")
            setLastModified(System.currentTimeMillis())
        }

        // Old file (should be deleted)
        val oldFile = File(backupDir, "backup_old.csv").apply {
            writeText("data")
            setLastModified(System.currentTimeMillis() - (48 * 60 * 60 * 1000L)) // 48h old
        }

        // Abandoned tmp file (should be deleted)
        val abandonedTmp = File(backupDir, "backup_abandoned.csv.tmp").apply {
            writeText("partial data")
            setLastModified(System.currentTimeMillis() - (2 * 60 * 60 * 1000L)) // 2h old
        }

        val deleted = backupFileManager.deleteStaleBackups(maxAgeMillis = 24 * 60 * 60 * 1000L)

        assertEquals(2, deleted)
        assertTrue(freshFile.exists())
        assertFalse(oldFile.exists())
        assertFalse(abandonedTmp.exists())
    }
}
