package com.example.inputmonitor.data.backup.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.inputmonitor.core.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BackupSettingsRepositoryTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var tempDir: File
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var repository: BackupSettingsRepositoryImpl

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("backup_settings_test").toFile()
        val dummyContext = object : android.content.ContextWrapper(null) {}

        testDataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            produceFile = { File(tempDir, "test_backup.preferences_pb") }
        )

        repository = BackupSettingsRepositoryImpl(
            context = dummyContext,
            dispatchers = testDispatchers,
            customDataStore = testDataStore
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun defaultSettings_areSafeAndDisabled() = runBlocking {
        val settings = repository.getBackupSettings()

        assertFalse("Backup must be OFF by default", settings.enabled)
        assertTrue("Wi-Fi only must be ON by default", settings.wifiOnly)
        assertEquals("https_rest", settings.providerId)
        assertNull(settings.providerEndpoint)
        assertNull(settings.lastAttemptAt)
        assertNull(settings.lastSuccessfulBackupAt)
        assertNull(settings.lastFailureReason)
        assertFalse(settings.isAuthenticationRequired)
    }

    @Test
    fun setBackupEnabled_andWifiOnly_persistsProperly() = runBlocking {
        repository.setBackupEnabled(true)
        repository.setWifiOnly(false)

        val updated = repository.getBackupSettings()
        assertTrue(updated.enabled)
        assertFalse(updated.wifiOnly)

        repository.setBackupEnabled(false)
        assertFalse(repository.getBackupSettings().enabled)
    }

    @Test
    fun setProviderConfig_persistsEndpointAndProviderId() = runBlocking {
        repository.setProviderConfig("nextcloud_webdav", "https://cloud.example.com/remote.php/dav/files")

        val settings = repository.getBackupSettings()
        assertEquals("nextcloud_webdav", settings.providerId)
        assertEquals("https://cloud.example.com/remote.php/dav/files", settings.providerEndpoint)
        assertTrue(settings.isConfigured)
    }

    @Test
    fun recordSuccess_updatesTimestampAndClearsFailure() = runBlocking {
        repository.recordBackupFailure("Network timed out", authRequired = false)
        val failedState = repository.getBackupSettings()
        assertEquals("Network timed out", failedState.lastFailureReason)

        val now = 1700000000000L
        repository.recordBackupSuccess(timestamp = now, remoteBackupId = "backup_file_123.csv")

        val successState = repository.getBackupSettings()
        assertEquals(now, successState.lastSuccessfulBackupAt)
        assertEquals("backup_file_123.csv", successState.lastRemoteBackupId)
        assertNull(successState.lastFailureReason)
        assertFalse(successState.isAuthenticationRequired)
    }

    @Test
    fun recordFailure_withAuthRequired_flagsAuthenticationRequired() = runBlocking {
        repository.recordBackupFailure("401 Unauthorized", authRequired = true)

        val state = repository.getBackupSettings()
        assertEquals("401 Unauthorized", state.lastFailureReason)
        assertTrue(state.isAuthenticationRequired)

        repository.clearAuthenticationRequired()
        assertFalse(repository.getBackupSettings().isAuthenticationRequired)
    }

    @Test
    fun resetBackupSettings_clearsAllStateToDefaults() = runBlocking {
        repository.setBackupEnabled(true)
        repository.setProviderConfig("provider", "https://test.com")
        repository.recordBackupSuccess(12345L, "id")

        repository.resetBackupSettings()

        val reset = repository.getBackupSettings()
        assertFalse(reset.enabled)
        assertNull(reset.providerEndpoint)
        assertNull(reset.lastSuccessfulBackupAt)
    }
}
