package com.example.inputmonitor.ui.setup

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.inputmonitor.domain.model.SetupState
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.setup.SetupStateManager
import com.example.inputmonitor.platform.permissions.PermissionCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing first-run onboarding, permissions, and setup status (Architecture.md §14, §16, SRS.md §10–§18).
 */
class SetupViewModel(
    private val setupStateManager: SetupStateManager,
    private val permissionCoordinator: PermissionCoordinator,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val setupState: StateFlow<SetupState> = setupStateManager.observeSetupState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = setupStateManager.getSetupState()
        )

    val isPermissionDenied: StateFlow<Boolean> = permissionCoordinator.isPermissionDenied

    fun refresh() {
        viewModelScope.launch {
            setupStateManager.refresh()
        }
    }

    fun completeSetup(onComplete: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
            setupStateManager.refresh()
            onComplete()
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Graceful fallback to general settings if vendor OS restricts accessibility action
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun openKeyboardSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun showKeyboardPicker(context: Context) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        } catch (_: Exception) {
            // Fail safely
        }
    }

    fun onPermissionsResult(permissions: Map<String, Boolean>) {
        permissionCoordinator.onPermissionsResult(permissions)
        refresh()
    }

    fun dismissPermissionDeniedAlert() {
        permissionCoordinator.dismissPermissionDeniedAlert()
    }

    companion object {
        fun provideFactory(
            setupStateManager: SetupStateManager,
            permissionCoordinator: PermissionCoordinator,
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SetupViewModel(
                    setupStateManager = setupStateManager,
                    permissionCoordinator = permissionCoordinator,
                    preferencesRepository = preferencesRepository
                ) as T
            }
        }
    }
}
