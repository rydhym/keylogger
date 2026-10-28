package com.example.inputmonitor.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [SetupState] invariants, completeness, and operational criteria (SRS.md §16, §17).
 */
class SetupStateTest {

    @Test
    fun defaultState_isNotCompleteAndNotOperational() {
        val state = SetupState(
            runtimePermissionsReady = true,
            accessibilityEnabled = false,
            imeInstalled = true,
            imeEnabled = false,
            imeSelected = false,
            databaseReady = true,
            monitoringSessionActive = false,
            isOnboardingCompleted = false
        )

        assertFalse("Fresh install cannot be complete", state.isComplete)
        assertFalse("Fresh install with no active sources is not operational", state.isOperational)
    }

    @Test
    fun allCapabilitiesReady_isCompleteAndOperational() {
        val state = SetupState(
            runtimePermissionsReady = true,
            accessibilityEnabled = true,
            imeInstalled = true,
            imeEnabled = true,
            imeSelected = true,
            databaseReady = true,
            monitoringSessionActive = false,
            isOnboardingCompleted = true
        )

        assertTrue("All ready must be complete", state.isComplete)
        assertTrue("All ready must be operational", state.isOperational)
    }

    @Test
    fun accessibilityEnabledOnly_isOperationalButNotComplete() {
        val state = SetupState(
            runtimePermissionsReady = true,
            accessibilityEnabled = true,
            imeInstalled = true,
            imeEnabled = false,
            imeSelected = false,
            databaseReady = true
        )

        assertFalse("Missing IME setup is not complete", state.isComplete)
        assertTrue("Accessibility alone makes system operational for partial setup", state.isOperational)
    }

    @Test
    fun imeEnabledAndSelectedOnly_isOperationalButNotComplete() {
        val state = SetupState(
            runtimePermissionsReady = true,
            accessibilityEnabled = false,
            imeInstalled = true,
            imeEnabled = true,
            imeSelected = true,
            databaseReady = true
        )

        assertFalse("Missing Accessibility is not complete", state.isComplete)
        assertTrue("IME enabled and selected makes system operational for partial setup", state.isOperational)
    }

    @Test
    fun imeEnabledButNotSelected_isNotOperational() {
        val state = SetupState(
            runtimePermissionsReady = true,
            accessibilityEnabled = false,
            imeInstalled = true,
            imeEnabled = true,
            imeSelected = false,
            databaseReady = true
        )

        assertFalse("IME not selected cannot capture input", state.isOperational)
        assertFalse(state.isComplete)
    }

    @Test
    fun databaseNotReady_neverOperationalOrComplete() {
        val state = SetupState(
            runtimePermissionsReady = true,
            accessibilityEnabled = true,
            imeInstalled = true,
            imeEnabled = true,
            imeSelected = true,
            databaseReady = false
        )

        assertFalse("Cannot be complete without ready database", state.isComplete)
        assertFalse("Cannot be operational without ready database", state.isOperational)
    }

    @Test
    fun missingPermissions_notComplete() {
        val state = SetupState(
            runtimePermissionsReady = false,
            accessibilityEnabled = true,
            imeInstalled = true,
            imeEnabled = true,
            imeSelected = true,
            databaseReady = true
        )

        assertFalse("Missing permissions must prevent complete status", state.isComplete)
    }
}
