package com.example.inputmonitor.platform.backup

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.backup.file.BackupFileManager
import com.example.inputmonitor.data.backup.security.BackupTokenStorage
import com.example.inputmonitor.data.export.CsvEventExporterImpl
import com.example.inputmonitor.domain.backup.model.BackupMetadata
import com.example.inputmonitor.domain.backup.model.BackupResult
import com.example.inputmonitor.domain.backup.model.BackupSettings
import com.example.inputmonitor.domain.backup.repository.BackupRepository
import com.example.inputmonitor.domain.backup.repository.BackupSettingsRepository
import com.example.inputmonitor.domain.export.ExportValidatorImpl
import com.example.inputmonitor.domain.model.EventFilterCriteria
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AutoBackupWorkerTest {

    private val testDispatcher = Dispatchers.Unconfined
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
        override suspend fun getEventCountForSession(sessionId: String): Int = 0
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(0)
        override fun observeEventCounts(sessionId: String?): Flow<com.example.inputmonitor.domain.model.EventCounts> =
            flowOf(com.example.inputmonitor.domain.model.EventCounts(total = 0L))
        override fun observeLatestEvent(): Flow<InteractionEvent?> = flowOf(null)
        override fun observeEventsPaged(filter: EventFilterCriteria, limit: Int, offset: Int): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override fun observeFilteredCount(filter: EventFilterCriteria): Flow<Int> = flowOf(0)
        override fun observeDistinctPackageNames(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun deleteEventsForSession(sessionId: String): Boolean = true
        override suspend fun deleteAllEvents(): Boolean = true
    }

    private class FakeTokenStorage : BackupTokenStorage {
        var token: String? = "valid-token"
        override suspend fun saveAuthToken(token: String) { this.token = token }
        override suspend fun getAuthToken(): String? = token
        override suspend fun clearAuthToken() { token = null }
        override suspend fun hasAuthToken(): Boolean = token != null
    }

    private class FakeSettingsRepository : BackupSettingsRepository {
        var settings = BackupSettings()
        override val backupSettings: Flow<BackupSettings> get() = flowOf(settings)
        override suspend fun getBackupSettings(): BackupSettings = settings
        override suspend fun setBackupEnabled(enabled: Boolean) { settings = settings.copy(enabled = enabled) }
        override suspend fun setWifiOnly(wifiOnly: Boolean) { settings = settings.copy(wifiOnly = wifiOnly) }
        override suspend fun setProviderConfig(providerId: String, endpoint: String?) {
            settings = settings.copy(providerId = providerId, providerEndpoint = endpoint)
        }
        override suspend fun recordBackupAttempt(timestamp: Long) {
            settings = settings.copy(lastAttemptAt = timestamp)
        }
        override suspend fun recordBackupSuccess(timestamp: Long, remoteBackupId: String?) {
            settings = settings.copy(
                lastSuccessfulBackupAt = timestamp,
                lastRemoteBackupId = remoteBackupId,
                lastFailureReason = null,
                isAuthenticationRequired = false
            )
        }
        override suspend fun recordBackupFailure(reason: String, authRequired: Boolean) {
            settings = settings.copy(
                lastFailureReason = reason,
                isAuthenticationRequired = if (authRequired) true else settings.isAuthenticationRequired
            )
        }
        override suspend fun clearAuthenticationRequired() {
            settings = settings.copy(isAuthenticationRequired = false)
        }
        override suspend fun resetBackupSettings() { settings = BackupSettings() }
    }

    private class FakeBackupRepository : BackupRepository {
        var resultToReturn: BackupResult = BackupResult.Success("remote_id_123")
        var lastUploadedFile: File? = null

        override suspend fun uploadBackup(file: File, metadata: BackupMetadata): BackupResult {
            lastUploadedFile = file
            return resultToReturn
        }

        override suspend fun testConnection(endpoint: String, token: String?): BackupResult {
            return BackupResult.Success("connected")
        }
    }

    private lateinit var tempDir: File
    private lateinit var fakeContext: Context
    private lateinit var fileManager: BackupFileManager
    private lateinit var fakeSettings: FakeSettingsRepository
    private lateinit var fakeToken: FakeTokenStorage
    private lateinit var fakeBackupRepo: FakeBackupRepository
    private lateinit var worker: AutoBackupWorker

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("worker_test").toFile()
        fakeContext = object : android.content.ContextWrapper(null) {
            override fun getCacheDir(): File = tempDir
        }

        val exportUseCase = ExportSessionUseCase(
            eventRepository = FakeEventRepository(),
            exportValidator = ExportValidatorImpl(),
            csvEventExporter = CsvEventExporterImpl(),
            dispatchers = testDispatchers
        )

        fileManager = BackupFileManager(fakeContext, exportUseCase, testDispatchers)
        fakeSettings = FakeSettingsRepository()
        fakeToken = FakeTokenStorage()
        fakeBackupRepo = FakeBackupRepository()

        worker = androidx.work.testing.TestListenableWorkerBuilder.from(fakeContext, AutoBackupWorker::class.java)
            .build()
            .apply {
                dependenciesOverride = AutoBackupWorker.Dependencies(
                    backupFileManager = fileManager,
                    backupRepository = fakeBackupRepo,
                    backupSettingsRepository = fakeSettings,
                    tokenStorage = fakeToken
                )
            }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun doWork_whenDisabled_returnsSuccessWithoutUploading() = runBlocking {
        fakeSettings.settings = BackupSettings(enabled = false)

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertEquals(null, fakeBackupRepo.lastUploadedFile)
    }

    @Test
    fun doWork_whenNotConfigured_returnsFailureWithoutRetry() = runBlocking {
        fakeSettings.settings = BackupSettings(enabled = true, providerEndpoint = null)

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        assertTrue(fakeSettings.settings.lastFailureReason?.contains("not configured") == true)
        assertEquals(null, fakeBackupRepo.lastUploadedFile)
    }

    @Test
    fun doWork_whenUploadSucceeds_updatesSettingsAndCleansFile() = runBlocking {
        fakeSettings.settings = BackupSettings(
            enabled = true,
            providerEndpoint = "https://cloud.example.com/api"
        )
        fakeBackupRepo.resultToReturn = BackupResult.Success("remote_id_456")

        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertEquals("remote_id_456", fakeSettings.settings.lastRemoteBackupId)
        assertNotNull(fakeSettings.settings.lastSuccessfulBackupAt)
        assertNotNull(fakeSettings.settings.lastAttemptAt)

        // Verify temporary file was deleted after upload
        val backupDir = File(tempDir, BackupFileManager.BACKUP_DIR_NAME)
        val files = backupDir.listFiles() ?: emptyArray()
        assertEquals(0, files.size)
    }

    @Test
    fun doWork_whenAuthRequired_returnsFailureAndMarksFlag() = runBlocking {
        fakeSettings.settings = BackupSettings(
            enabled = true,
            providerEndpoint = "https://cloud.example.com/api"
        )
        fakeBackupRepo.resultToReturn = BackupResult.AuthenticationRequired("Token expired")

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        assertTrue(fakeSettings.settings.isAuthenticationRequired)
        assertEquals("Token expired", fakeSettings.settings.lastFailureReason)

        // Verify file was deleted
        val backupDir = File(tempDir, BackupFileManager.BACKUP_DIR_NAME)
        assertEquals(0, backupDir.listFiles()?.size ?: 0)
    }

    @Test
    fun doWork_whenRetryableFailure_returnsRetry() = runBlocking {
        fakeSettings.settings = BackupSettings(
            enabled = true,
            providerEndpoint = "https://cloud.example.com/api"
        )
        fakeBackupRepo.resultToReturn = BackupResult.RetryableFailure("503 Gateway Timeout")

        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        assertEquals("503 Gateway Timeout", fakeSettings.settings.lastFailureReason)

        // Verify file was deleted
        val backupDir = File(tempDir, BackupFileManager.BACKUP_DIR_NAME)
        assertEquals(0, backupDir.listFiles()?.size ?: 0)
    }
}
