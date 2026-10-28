package com.example.inputmonitor.ui.setup

import com.example.inputmonitor.domain.model.SetupState
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.setup.SetupStateManager
import com.example.inputmonitor.platform.permissions.PermissionCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SetupViewModel] verifying UI state management and setup completion (SRS.md §16–§18).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeSetupStateManager : SetupStateManager {
        val state = MutableStateFlow(SetupState())
        var refreshCount = 0

        override fun observeSetupState(): StateFlow<SetupState> = state.asStateFlow()
        override suspend fun refresh() {
            refreshCount++
        }
        override fun getSetupState(): SetupState = state.value
    }

    private class FakePermissionCoordinator : PermissionCoordinator {
        val denied = MutableStateFlow(false)
        override val isPermissionDenied: StateFlow<Boolean> = denied.asStateFlow()
        override fun arePermissionsGranted(): Boolean = true
        override fun getRequiredPermissions(): List<String> = emptyList()
        override fun onPermissionsResult(permissions: Map<String, Boolean>) {
            denied.value = permissions.values.any { !it }
        }
        override fun dismissPermissionDeniedAlert() {
            denied.value = false
        }
    }

    private class FakePreferencesRepository : PreferencesRepository {
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
        override suspend fun clearAllPreferences() {}
    }

    private lateinit var setupStateManager: FakeSetupStateManager
    private lateinit var permissionCoordinator: FakePermissionCoordinator
    private lateinit var preferencesRepository: FakePreferencesRepository
    private lateinit var viewModel: SetupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        setupStateManager = FakeSetupStateManager()
        permissionCoordinator = FakePermissionCoordinator()
        preferencesRepository = FakePreferencesRepository()

        viewModel = SetupViewModel(
            setupStateManager = setupStateManager,
            permissionCoordinator = permissionCoordinator,
            preferencesRepository = preferencesRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_exposesSetupStateManagerState() {
        assertEquals(setupStateManager.getSetupState(), viewModel.setupState.value)
        assertFalse(viewModel.isPermissionDenied.value)
    }

    @Test
    fun refresh_invokesSetupStateManagerRefresh() {
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, setupStateManager.refreshCount)
    }

    @Test
    fun completeSetup_persistsOnboardingPreferenceAndInvokesCallback() {
        var callbackInvoked = false

        viewModel.completeSetup {
            callbackInvoked = true
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Preferences should record onboarding complete", preferencesRepository.onboarding.value)
        assertTrue("Callback must be invoked after completion", callbackInvoked)
        assertEquals("Setup state should be refreshed on completion", 1, setupStateManager.refreshCount)
    }

    @Test
    fun permissionResults_delegatesToPermissionCoordinator() {
        viewModel.onPermissionsResult(mapOf("test.permission" to false))
        assertTrue(viewModel.isPermissionDenied.value)

        viewModel.dismissPermissionDeniedAlert()
        assertFalse(viewModel.isPermissionDenied.value)
    }
}
