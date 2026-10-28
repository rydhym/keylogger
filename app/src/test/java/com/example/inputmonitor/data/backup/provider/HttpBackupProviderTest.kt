package com.example.inputmonitor.data.backup.provider

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.backup.security.BackupTokenStorage
import com.example.inputmonitor.domain.backup.model.BackupMetadata
import com.example.inputmonitor.domain.backup.model.BackupResult
import com.example.inputmonitor.domain.backup.model.BackupSettings
import com.example.inputmonitor.domain.backup.repository.BackupSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HttpBackupProviderTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeTokenStorage : BackupTokenStorage {
        var token: String? = null
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
        override suspend fun recordBackupAttempt(timestamp: Long) {}
        override suspend fun recordBackupSuccess(timestamp: Long, remoteBackupId: String?) {}
        override suspend fun recordBackupFailure(reason: String, authRequired: Boolean) {}
        override suspend fun clearAuthenticationRequired() {}
        override suspend fun resetBackupSettings() {}
    }

    private lateinit var mockServer: MockWebServer
    private lateinit var fakeTokenStorage: FakeTokenStorage
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
    private lateinit var provider: HttpBackupProvider
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        fakeTokenStorage = FakeTokenStorage()
        fakeSettingsRepository = FakeSettingsRepository()

        provider = HttpBackupProvider(
            backupSettingsRepository = fakeSettingsRepository,
            tokenStorage = fakeTokenStorage,
            dispatchers = testDispatchers,
            okHttpClient = OkHttpClient.Builder().build()
        )

        val tempDir = Files.createTempDirectory("http_test").toFile()
        tempFile = File(tempDir, "test_backup.csv").apply {
            writeText("col1,col2\nval1,val2\n")
        }
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
        tempFile.delete()
        tempFile.parentFile?.delete()
    }

    @Test
    fun uploadBackup_rejectsCleartextNonLocalUrl() = runBlocking {
        fakeSettingsRepository.settings = BackupSettings(
            providerEndpoint = "http://remote-insecure-server.com/api/backup"
        )

        val result = provider.uploadBackup(
            file = tempFile,
            metadata = BackupMetadata(tempFile.name, tempFile.length(), 1L)
        )

        assertTrue(result is BackupResult.PermanentFailure)
        val failure = result as BackupResult.PermanentFailure
        assertTrue(failure.message.contains("Cleartext HTTP is prohibited"))
    }

    @Test
    fun uploadBackup_success_parsesRemoteIdAndSendsBearerToken() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("X-Backup-Id", "cloud_id_9999")
                .setBody("{\"status\":\"ok\"}")
        )

        fakeTokenStorage.token = "secret-token-xyz"
        val serverUrl = mockServer.url("/api/upload").toString()
        fakeSettingsRepository.settings = BackupSettings(providerEndpoint = serverUrl)

        val result = provider.uploadBackup(
            file = tempFile,
            metadata = BackupMetadata(tempFile.name, tempFile.length(), 2L)
        )

        assertTrue(result is BackupResult.Success)
        val success = result as BackupResult.Success
        assertEquals("cloud_id_9999", success.remoteBackupId)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("Bearer secret-token-xyz", recordedRequest.getHeader("Authorization"))
        assertTrue(recordedRequest.getHeader("Content-Type")?.startsWith("multipart/form-data") == true)
    }

    @Test
    fun uploadBackup_authError_returnsAuthenticationRequired() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized")
        )

        fakeSettingsRepository.settings = BackupSettings(
            providerEndpoint = mockServer.url("/api/upload").toString()
        )

        val result = provider.uploadBackup(
            file = tempFile,
            metadata = BackupMetadata(tempFile.name, tempFile.length(), 1L)
        )

        assertTrue(result is BackupResult.AuthenticationRequired)
    }

    @Test
    fun uploadBackup_server500_returnsRetryableFailure() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("Service Unavailable")
        )

        fakeSettingsRepository.settings = BackupSettings(
            providerEndpoint = mockServer.url("/api/upload").toString()
        )

        val result = provider.uploadBackup(
            file = tempFile,
            metadata = BackupMetadata(tempFile.name, tempFile.length(), 1L)
        )

        assertTrue(result is BackupResult.RetryableFailure)
    }

    @Test
    fun uploadBackup_client400_returnsPermanentFailure() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("Bad Request")
        )

        fakeSettingsRepository.settings = BackupSettings(
            providerEndpoint = mockServer.url("/api/upload").toString()
        )

        val result = provider.uploadBackup(
            file = tempFile,
            metadata = BackupMetadata(tempFile.name, tempFile.length(), 1L)
        )

        assertTrue(result is BackupResult.PermanentFailure)
    }
}
