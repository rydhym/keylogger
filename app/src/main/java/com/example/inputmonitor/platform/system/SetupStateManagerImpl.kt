package com.example.inputmonitor.platform.system

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.model.SetupState
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.session.SessionManager
import com.example.inputmonitor.domain.setup.SetupStateManager
import com.example.inputmonitor.platform.permissions.PermissionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [SetupStateManager] querying system status checker,
 * permissions, session manager, and preferences (Architecture.md §14, §15, SRS.md §18).
 */
class SetupStateManagerImpl(
    private val systemStatusChecker: SystemStatusChecker,
    private val permissionCoordinator: PermissionCoordinator,
    private val sessionManager: SessionManager,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: DispatcherProvider,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.default)
) : SetupStateManager {

    private val _setupState = MutableStateFlow(SetupState())
    private val setupStateFlow: StateFlow<SetupState> = _setupState.asStateFlow()

    init {
        // Observe session state changes to maintain active session flag
        scope.launch(dispatchers.default) {
            sessionManager.sessionState.collect { sessionState ->
                _setupState.value = _setupState.value.copy(
                    monitoringSessionActive = (sessionState == SessionState.ACTIVE)
                )
            }
        }

        // Observe onboarding preferences
        scope.launch(dispatchers.io) {
            preferencesRepository.isOnboardingCompleted.collect { completed ->
                _setupState.value = _setupState.value.copy(
                    isOnboardingCompleted = completed
                )
            }
        }
    }

    override fun observeSetupState(): StateFlow<SetupState> = setupStateFlow

    override suspend fun refresh() {
        withContext(dispatchers.io) {
            val permissionsReady = permissionCoordinator.arePermissionsGranted()
            val accessibilityEnabled = systemStatusChecker.isAccessibilityServiceEnabled()
            val imeInstalled = systemStatusChecker.isImeInstalled()
            val imeEnabled = systemStatusChecker.isImeEnabled()
            val imeSelected = systemStatusChecker.isImeSelected()
            val dbReady = systemStatusChecker.isDatabaseReady()
            val sessionActive = (sessionManager.sessionState.value == SessionState.ACTIVE)
            val onboardingCompleted = try {
                preferencesRepository.isOnboardingCompleted.first()
            } catch (_: Exception) {
                false
            }

            _setupState.value = SetupState(
                runtimePermissionsReady = permissionsReady,
                accessibilityEnabled = accessibilityEnabled,
                imeInstalled = imeInstalled,
                imeEnabled = imeEnabled,
                imeSelected = imeSelected,
                databaseReady = dbReady,
                monitoringSessionActive = sessionActive,
                isOnboardingCompleted = onboardingCompleted
            )
        }
    }

    override fun getSetupState(): SetupState = _setupState.value
}
