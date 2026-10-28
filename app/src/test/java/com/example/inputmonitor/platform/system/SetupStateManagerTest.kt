package com.example.inputmonitor.platform.system

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.session.SessionManager
import com.example.inputmonitor.platform.permissions.PermissionCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SetupStateManagerImpl] verifying state tracking, refresh cycles,
 * and system service observations (Architecture.md §14, §15, SRS.md §18).
 */
class SetupStateManagerTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeSystemStatusChecker(
        var accessibility: Boolean = false,
        var installed: Boolean = true,
        var enabled: Boolean = false,
        var selected: Boolean = false,
        var dbReady: Boolean = true
    ) : SystemStatusChecker {
        override fun isAccessibilityServiceEnabled(): Boolean = accessibility
        override fun isImeInstalled(): Boolean = installed
        override fun isImeEnabled(): Boolean = enabled
        override fun isImeSelected(): Boolean = selected
        override fun isDatabaseReady(): Boolean = dbReady
    }

    private class FakePermissionCoordinator(
        var granted: Boolean = true
    ) : PermissionCoordinator {
        private val _denied = MutableStateFlow(false)
        override val isPermissionDenied: StateFlow<Boolean> = _denied.asStateFlow()
        override fun arePermissionsGranted(): Boolean = granted
        override fun getRequiredPermissions(): List<String> = emptyList()
        override fun onPermissionsResult(permissions: Map<String, Boolean>) {
            _denied.value = permissions.values.any { !it }
        }
        override fun dismissPermissionDeniedAlert() {
            _denied.value = false
        }
    }

    private class FakeSessionManager : SessionManager {
        val _sessionState = MutableStateFlow(SessionState.INACTIVE)
        override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
        override val currentSession: StateFlow<MonitoringSession?> = MutableStateFlow(null)

        override suspend fun startSession(name: String, notes: String?): MonitoringSession {
            _sessionState.value = SessionState.ACTIVE
            return MonitoringSession(
                id = "test-session",
                name = name,
                startTime = System.currentTimeMillis(),
                state = SessionState.ACTIVE,
                androidVersion = "15",
                deviceModel = "Pixel",
                appVersion = "0.1.0",
                notes = notes
            )
        }
        override suspend fun pauseSession(): Boolean {
            _sessionState.value = SessionState.PAUSED
            return true
        }
        override suspend fun resumeSession(): Boolean {
            _sessionState.value = SessionState.ACTIVE
            return true
        }
        override suspend fun stopSession(): Boolean {
            _sessionState.value = SessionState.STOPPED
            return true
        }
        override suspend fun restoreSessionIfActive(): MonitoringSession? = null
    }

    private class FakePreferencesRepository : PreferencesRepository {
        val _onboarding = MutableStateFlow(false)
        override val isOnboardingCompleted: Flow<Boolean> = _onboarding
        override val activeSessionId: Flow<String?> = MutableStateFlow(null)
        override val isKeyboardLoggingEnabled: Flow<Boolean> = MutableStateFlow(true)
        override val isAccessibilityLoggingEnabled: Flow<Boolean> = MutableStateFlow(true)

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            _onboarding.value = completed
        }
        override suspend fun setActiveSessionId(sessionId: String?) {}
        override suspend fun setKeyboardLoggingEnabled(enabled: Boolean) {}
        override suspend fun setAccessibilityLoggingEnabled(enabled: Boolean) {}
        override suspend fun clearAllPreferences() {}
    }

    private lateinit var systemStatusChecker: FakeSystemStatusChecker
    private lateinit var permissionCoordinator: FakePermissionCoordinator
    private lateinit var sessionManager: FakeSessionManager
    private lateinit var preferencesRepository: FakePreferencesRepository
    private lateinit var setupStateManager: SetupStateManagerImpl

    @Before
    fun setUp() {
        systemStatusChecker = FakeSystemStatusChecker()
        permissionCoordinator = FakePermissionCoordinator()
        sessionManager = FakeSessionManager()
        preferencesRepository = FakePreferencesRepository()

        setupStateManager = SetupStateManagerImpl(
            systemStatusChecker = systemStatusChecker,
            permissionCoordinator = permissionCoordinator,
            sessionManager = sessionManager,
            preferencesRepository = preferencesRepository,
            dispatchers = testDispatchers,
            scope = CoroutineScope(testDispatcher)
        )
    }

    @Test
    fun freshInstallation_returnsUnconfiguredState() = runBlocking {
        setupStateManager.refresh()
        val state = setupStateManager.getSetupState()

        assertTrue("Permissions ready", state.runtimePermissionsReady)
        assertFalse("Accessibility disabled initially", state.accessibilityEnabled)
        assertTrue("IME installed", state.imeInstalled)
        assertFalse("IME not enabled initially", state.imeEnabled)
        assertFalse("IME not selected initially", state.imeSelected)
        assertTrue("Database ready", state.databaseReady)
        assertFalse("No active session", state.monitoringSessionActive)
        assertFalse("Onboarding not yet completed", state.isOnboardingCompleted)
        assertFalse("System is not complete", state.isComplete)
    }

    @Test
    fun refresh_reflectsEnabledAccessibilityService() = runBlocking {
        systemStatusChecker.accessibility = true
        setupStateManager.refresh()

        val state = setupStateManager.getSetupState()
        assertTrue("Accessibility service should be enabled", state.accessibilityEnabled)
        assertTrue("Accessibility alone enables operational partial setup", state.isOperational)
        assertFalse("Still incomplete due to IME unconfigured", state.isComplete)
    }

    @Test
    fun refresh_reflectsImeEnabledAndSelected() = runBlocking {
        systemStatusChecker.enabled = true
        systemStatusChecker.selected = true
        setupStateManager.refresh()

        val state = setupStateManager.getSetupState()
        assertTrue("IME enabled", state.imeEnabled)
        assertTrue("IME selected", state.imeSelected)
        assertTrue("IME enabled+selected enables operational partial setup", state.isOperational)
    }

    @Test
    fun fullConfiguration_satisfiesIsComplete() = runBlocking {
        systemStatusChecker.accessibility = true
        systemStatusChecker.enabled = true
        systemStatusChecker.selected = true
        systemStatusChecker.dbReady = true
        permissionCoordinator.granted = true
        preferencesRepository.setOnboardingCompleted(true)

        setupStateManager.refresh()
        val state = setupStateManager.getSetupState()

        assertTrue("All verified -> complete", state.isComplete)
        assertTrue("All verified -> operational", state.isOperational)
        assertTrue("Onboarding completed", state.isOnboardingCompleted)
    }

    @Test
    fun sessionStateChange_updatesMonitoringSessionActive() = runBlocking {
        sessionManager._sessionState.value = SessionState.ACTIVE
        val state = setupStateManager.getSetupState()
        assertTrue("Monitoring session active flag must update", state.monitoringSessionActive)

        sessionManager._sessionState.value = SessionState.PAUSED
        assertFalse("Paused session is not active", setupStateManager.getSetupState().monitoringSessionActive)
    }

    @Test
    fun onboardingPreferenceChange_updatesIsOnboardingCompleted() = runBlocking {
        preferencesRepository.setOnboardingCompleted(true)
        val state = setupStateManager.getSetupState()
        assertTrue("Onboarding completed flag must update", state.isOnboardingCompleted)
    }
}
