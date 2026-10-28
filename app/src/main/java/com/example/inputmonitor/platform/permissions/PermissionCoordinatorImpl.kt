package com.example.inputmonitor.platform.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Concrete implementation of [PermissionCoordinator] managing runtime permission states (Architecture.md §16).
 */
class PermissionCoordinatorImpl(
    private val context: Context,
    private val requiredPermissions: List<String> = emptyList()
) : PermissionCoordinator {

    private val _isPermissionDenied = MutableStateFlow(false)
    override val isPermissionDenied: StateFlow<Boolean> = _isPermissionDenied.asStateFlow()

    override fun arePermissionsGranted(): Boolean {
        if (requiredPermissions.isEmpty()) return true
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun getRequiredPermissions(): List<String> = requiredPermissions

    override fun onPermissionsResult(permissions: Map<String, Boolean>) {
        val anyDenied = permissions.values.any { !it }
        _isPermissionDenied.value = anyDenied
    }

    override fun dismissPermissionDeniedAlert() {
        _isPermissionDenied.value = false
    }
}
