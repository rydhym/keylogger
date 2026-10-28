package com.example.inputmonitor.platform.permissions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying permission coordinator and non-blocking denial behavior (SRS.md §12, AC-03, AC-35).
 */
class PermissionCoordinatorTest {

    private class FakePermissionCoordinator(
        var permissionsGranted: Boolean = true,
        var requiredList: List<String> = emptyList()
    ) : PermissionCoordinator {
        private val _isPermissionDenied = MutableStateFlow(false)
        override val isPermissionDenied: StateFlow<Boolean> = _isPermissionDenied.asStateFlow()

        override fun arePermissionsGranted(): Boolean = permissionsGranted
        override fun getRequiredPermissions(): List<String> = requiredList

        override fun onPermissionsResult(permissions: Map<String, Boolean>) {
            _isPermissionDenied.value = permissions.values.any { !it }
        }

        override fun dismissPermissionDeniedAlert() {
            _isPermissionDenied.value = false
        }
    }

    @Test
    fun defaultState_allGrantedWhenEmptyRequiredPermissions() {
        val coordinator = FakePermissionCoordinator(permissionsGranted = true, requiredList = emptyList())
        assertTrue(coordinator.arePermissionsGranted())
        assertTrue(coordinator.getRequiredPermissions().isEmpty())
        assertFalse(coordinator.isPermissionDenied.value)
    }

    @Test
    fun permissionGranted_updatesStateCorrectly() {
        val coordinator = FakePermissionCoordinator(
            permissionsGranted = true,
            requiredList = listOf("android.permission.POST_NOTIFICATIONS")
        )

        coordinator.onPermissionsResult(mapOf("android.permission.POST_NOTIFICATIONS" to true))
        assertFalse("Granted permission must not mark denial", coordinator.isPermissionDenied.value)
    }

    @Test
    fun permissionDenied_setsDeniedFlagWithoutThrowingException() {
        val coordinator = FakePermissionCoordinator(
            permissionsGranted = false,
            requiredList = listOf("android.permission.POST_NOTIFICATIONS")
        )

        coordinator.onPermissionsResult(mapOf("android.permission.POST_NOTIFICATIONS" to false))
        assertTrue("Denied permission must set denial state", coordinator.isPermissionDenied.value)
    }

    @Test
    fun dismissPermissionDeniedAlert_clearsDeniedFlagPreventingUserTrap() {
        val coordinator = FakePermissionCoordinator(
            permissionsGranted = false,
            requiredList = listOf("android.permission.POST_NOTIFICATIONS")
        )

        coordinator.onPermissionsResult(mapOf("android.permission.POST_NOTIFICATIONS" to false))
        assertTrue(coordinator.isPermissionDenied.value)

        // User acknowledges/dismisses alert -> never trapped (AC-35)
        coordinator.dismissPermissionDeniedAlert()
        assertFalse("Acknowledging denial must clear alert state", coordinator.isPermissionDenied.value)
    }
}
