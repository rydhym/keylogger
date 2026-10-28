package com.example.inputmonitor.platform.permissions

import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates Android runtime permission checks, requests, and state representations
 * without trapping the user (Architecture.md §16, SRS.md §12, AC-03, AC-35).
 */
interface PermissionCoordinator {
    /**
     * Checks if all applicable runtime permissions are currently granted.
     */
    fun arePermissionsGranted(): Boolean

    /**
     * List of runtime permissions required by implemented features.
     * Empty if no broad or dangerous runtime permissions are requested.
     */
    fun getRequiredPermissions(): List<String>

    /**
     * Flow tracking whether a permission was explicitly denied by the user.
     */
    val isPermissionDenied: StateFlow<Boolean>

    /**
     * Callback when permission request results arrive from the system.
     */
    fun onPermissionsResult(permissions: Map<String, Boolean>)

    /**
     * Acknowledges permission denial so user can proceed without repeated popups (AC-35).
     */
    fun dismissPermissionDeniedAlert()
}
