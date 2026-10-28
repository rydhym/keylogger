package com.example.inputmonitor.ui.dashboard

import com.example.inputmonitor.domain.model.EventCounts
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.model.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [DashboardUiState] properties and aggregate count delegations (Architecture.md §55, SRS.md §47).
 */
class DashboardUiStateTest {

    @Test
    fun defaultState_hasZeroCountersAndInactiveSession() {
        val state = DashboardUiState()

        assertEquals(0L, state.totalEvents)
        assertEquals(0L, state.keyboardEvents)
        assertEquals(0L, state.accessibilityEvents)
        assertEquals(0L, state.clickEvents)
        assertEquals(0L, state.focusEvents)
        assertEquals(0L, state.scrollEvents)
        assertEquals(0L, state.windowEvents)
        assertEquals(SessionState.INACTIVE, state.sessionState)
        assertEquals(null, state.activeSession)
        assertEquals(null, state.currentApplicationLabel)
        assertEquals(null, state.currentPackageName)
        assertTrue("Incomplete services on default setupState", state.hasIncompleteServices)
    }

    @Test
    fun populatedState_delegatesCountersCorrectly() {
        val counts = EventCounts(
            total = 1284L,
            keyboard = 642L,
            accessibility = 642L,
            clicks = 213L,
            focus = 184L,
            scrolls = 156L,
            windowChanges = 89L
        )

        val setupComplete = SetupState(
            runtimePermissionsReady = true,
            accessibilityEnabled = true,
            imeInstalled = true,
            imeEnabled = true,
            imeSelected = true,
            databaseReady = true
        )

        val session = MonitoringSession(
            id = "sess-1",
            name = "Test Session",
            startTime = 1000L,
            state = SessionState.ACTIVE,
            androidVersion = "15",
            deviceModel = "Pixel",
            appVersion = "0.1.0"
        )

        val state = DashboardUiState(
            setupState = setupComplete,
            sessionState = SessionState.ACTIVE,
            activeSession = session,
            eventCounts = counts,
            currentApplicationLabel = "Notes App",
            currentPackageName = "com.example.notes"
        )

        assertEquals(1284L, state.totalEvents)
        assertEquals(642L, state.keyboardEvents)
        assertEquals(642L, state.accessibilityEvents)
        assertEquals(213L, state.clickEvents)
        assertEquals(184L, state.focusEvents)
        assertEquals(156L, state.scrollEvents)
        assertEquals(89L, state.windowEvents)
        assertEquals("Notes App", state.currentApplicationLabel)
        assertEquals("com.example.notes", state.currentPackageName)
        assertFalse("All services complete", state.hasIncompleteServices)
    }
}
