package com.example.inputmonitor.ui.setup

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.session.SessionManager
import com.example.inputmonitor.platform.permissions.PermissionCoordinator
import com.example.inputmonitor.platform.system.SetupStateManagerImpl
import com.example.inputmonitor.platform.system.SystemStatusChecker
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
 * Phase 10 Setup Flow Validation Integration Test Suite (IMPLEMENTATION_PLAN.md §49).
 *
 * Verifies all 11 required setup lifecycle scenarios:
 * 1. Fresh installation
 * 2. Permission accepted
 * 3. Permission denied
 * 4. Accessibility enabled
 * 5. Accessibility skipped
 * 6. Keyboard enabled
 * 7. Keyboard not enabled
 * 8. Custom Keyboard selected
 * 9. Different keyboard selected
 * 10. Return from Settings (refresh on resume)
 * 11. App restart (preserving onboarding completion)
 */
class SetupFlowIntegrationTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class SimulatedSystemStatus(
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

    private class SimulatedPermissionCoordinator(
        var permissionsGranted: Boolean = true,
        var requiredList: List<String> = emptyList()
    ) : PermissionCoordinator {
        private val _denied = MutableStateFlow(false)
        override val isPermissionDenied: StateFlow<Boolean> = _denied.asStateFlow()

        override fun arePermissionsGranted(): Boolean = permissionsGranted
        override fun getRequiredPermissions(): List<String> = requiredList

        override fun onPermissionsResult(permissions: Map<String, Boolean>) {
            val anyDenied = permissions.values.any { !it }
            _denied.value = anyDenied
            if (anyDenied) {
                permissionsGranted = false
            }
        }

        override fun dismissPermissionDeniedAlert() {
            _denied.value = false
        }
    }

    private class SimulatedPreferencesRepository : PreferencesRepository {
        val onboarding = MutableStateFlow(false)
        override val isOnboardingCompleted: Flow<Boolean> = onboarding
        override val activeSessionId: Flow<String?> = MutableStateFlow(null)
        override val isKeyboardLoggingEnabled: Flow<Boolean> = MutableStateFlow(true)
        override val isAccessibilityLoggingEnabled: Flow<Boolean> = MutableStateFlow(true)

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            onboarding.value = completed
        }
        override suspend fun setActiveSessionId(sessionId: String?) {}
        override suspend fun setKeyboardLoggingEnabled(enabled: Boolean) {}
        override suspend fun setAccessibilityLoggingEnabled(enabled: Boolean) {}
        override suspend fun clearAllPreferences() {
            onboarding.value = false
        }
    }

    private class SimulatedSessionManager : SessionManager {
        val _sessionState = MutableStateFlow(SessionState.INACTIVE)
        override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
        override val currentSession: StateFlow<MonitoringSession?> = MutableStateFlow(null)

        override suspend fun startSession(name: String, notes: String?): MonitoringSession {
            _sessionState.value = SessionState.ACTIVE
            return MonitoringSession(
                id = "sim-session",
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

    private lateinit var systemStatus: SimulatedSystemStatus
    private lateinit var permissionCoordinator: SimulatedPermissionCoordinator
    private lateinit var preferencesRepository: SimulatedPreferencesRepository
    private lateinit var sessionManager: SimulatedSessionManager
    private lateinit var setupStateManager: SetupStateManagerImpl

    @Before
    fun setUp() {
        systemStatus = SimulatedSystemStatus()
        permissionCoordinator = SimulatedPermissionCoordinator()
        preferencesRepository = SimulatedPreferencesRepository()
        sessionManager = SimulatedSessionManager()

        setupStateManager = SetupStateManagerImpl(
            systemStatusChecker = systemStatus,
            permissionCoordinator = permissionCoordinator,
            sessionManager = sessionManager,
            preferencesRepository = preferencesRepository,
            dispatchers = testDispatchers,
            scope = CoroutineScope(testDispatcher)
        )
    }

    // Scenario 1: Fresh Installation
    @Test
    fun scenario1_freshInstallation() = runBlocking {
        setupStateManager.refresh()
        val state = setupStateManager.getSetupState()

        assertFalse("Onboarding incomplete on fresh install", state.isOnboardingCompleted)
        assertFalse("Accessibility disabled on fresh install", state.accessibilityEnabled)
        assertFalse("Keyboard not enabled on fresh install", state.imeEnabled)
        assertFalse("Keyboard not selected on fresh install", state.imeSelected)
        assertFalse("System configuration incomplete", state.isComplete)
        assertFalse("System not operational without enabled sources", state.isOperational)
    }

    // Scenario 2: Permission Accepted
    @Test
    fun scenario2_permissionAccepted() = runBlocking {
        permissionCoordinator.requiredList = listOf("android.permission.POST_NOTIFICATIONS")
        permissionCoordinator.onPermissionsResult(mapOf("android.permission.POST_NOTIFICATIONS" to true))
        permissionCoordinator.permissionsGranted = true

        setupStateManager.refresh()
        val state = setupStateManager.getSetupState()

        assertTrue("Runtime permissions ready after acceptance", state.runtimePermissionsReady)
        assertFalse("Denial alert must be false", permissionCoordinator.isPermissionDenied.value)
    }

    // Scenario 3: Permission Denied (never traps user, AC-35)
    @Test
    fun scenario3_permissionDenied() = runBlocking {
        permissionCoordinator.requiredList = listOf("android.permission.POST_NOTIFICATIONS")
        permissionCoordinator.onPermissionsResult(mapOf("android.permission.POST_NOTIFICATIONS" to false))

        setupStateManager.refresh()
        val state = setupStateManager.getSetupState()

        assertFalse("Permissions not ready when denied", state.runtimePermissionsReady)
        assertTrue("Denial flag active", permissionCoordinator.isPermissionDenied.value)

        // Dismiss alert allows proceeding with setup
        permissionCoordinator.dismissPermissionDeniedAlert()
        assertFalse("Alert dismissed", permissionCoordinator.isPermissionDenied.value)
    }

    // Scenario 4: Accessibility Enabled
    @Test
    fun scenario4_accessibilityEnabled() = runBlocking {
        systemStatus.accessibility = true
        setupStateManager.refresh()

        val state = setupStateManager.getSetupState()
        assertTrue("Accessibility enabled", state.accessibilityEnabled)
        assertTrue("Operational for partial setup", state.isOperational)
    }

    // Scenario 5: Accessibility Skipped (partial setup flow)
    @Test
    fun scenario5_accessibilitySkipped() = runBlocking {
        systemStatus.accessibility = false
        // User configures keyboard instead
        systemStatus.enabled = true
        systemStatus.selected = true

        setupStateManager.refresh()
        val state = setupStateManager.getSetupState()

        assertFalse("Accessibility not enabled", state.accessibilityEnabled)
        assertFalse("Setup is not complete", state.isComplete)
        assertTrue("Operational via keyboard without accessibility", state.isOperational)
    }

    // Scenario 6: Keyboard Enabled
    @Test
    fun scenario6_keyboardEnabled() = runBlocking {
        systemStatus.enabled = true
        setupStateManager.refresh()

        val state = setupStateManager.getSetupState()
        assertTrue("IME enabled", state.imeEnabled)
        assertFalse("IME not yet selected", state.imeSelected)
    }

    // Scenario 7: Keyboard Not Enabled
    @Test
    fun scenario7_keyboardNotEnabled() = runBlocking {
        systemStatus.enabled = false
        systemStatus.selected = false
        setupStateManager.refresh()

        val state = setupStateManager.getSetupState()
        assertFalse("IME not enabled", state.imeEnabled)
        assertFalse("IME not selected", state.imeSelected)
    }

    // Scenario 8: Custom Keyboard Selected
    @Test
    fun scenario8_customKeyboardSelected() = runBlocking {
        systemStatus.enabled = true
        systemStatus.selected = true
        setupStateManager.refresh()

        val state = setupStateManager.getSetupState()
        assertTrue("Custom keyboard enabled", state.imeEnabled)
        assertTrue("Custom keyboard selected", state.imeSelected)
    }

    // Scenario 9: Different Keyboard Selected
    @Test
    fun scenario9_differentKeyboardSelected() = runBlocking {
        systemStatus.enabled = true
        systemStatus.selected = false // user chose Gboard in IME picker

        setupStateManager.refresh()
        val state = setupStateManager.getSetupState()

        assertTrue("Custom keyboard still enabled in system", state.imeEnabled)
        assertFalse("Custom keyboard is not active selection", state.imeSelected)
    }

    // Scenario 10: Return from Settings (simulated lifecycle resume)
    @Test
    fun scenario10_returnFromSettingsLifecycleResume() = runBlocking {
        // User starts at fresh install
        setupStateManager.refresh()
        assertFalse(setupStateManager.getSetupState().accessibilityEnabled)

        // User opens Android Settings, enables Accessibility, and returns to app
        systemStatus.accessibility = true

        // Lifecycle ON_RESUME triggers refresh
        setupStateManager.refresh()
        val state = setupStateManager.getSetupState()

        assertTrue("On resume, state immediately reflects system settings change", state.accessibilityEnabled)
    }

    // Scenario 11: App Restart (persisting onboarding completion)
    @Test
    fun scenario11_appRestartPreservesOnboardingCompletion() = runBlocking {
        // User completes setup
        preferencesRepository.setOnboardingCompleted(true)
        systemStatus.accessibility = true
        systemStatus.enabled = true
        systemStatus.selected = true

        setupStateManager.refresh()
        assertTrue(setupStateManager.getSetupState().isOnboardingCompleted)

        // Simulate application restart by creating a new SetupStateManager instance with existing persistent preferences
        val restartedSetupStateManager = SetupStateManagerImpl(
            systemStatusChecker = systemStatus,
            permissionCoordinator = permissionCoordinator,
            sessionManager = sessionManager,
            preferencesRepository = preferencesRepository,
            dispatchers = testDispatchers,
            scope = CoroutineScope(testDispatcher)
        )
        restartedSetupStateManager.refresh()
        val restoredState = restartedSetupStateManager.getSetupState()

        assertTrue("Restart preserves completed onboarding preference", restoredState.isOnboardingCompleted)
        assertTrue("Restart preserves complete system state", restoredState.isComplete)
    }
}
