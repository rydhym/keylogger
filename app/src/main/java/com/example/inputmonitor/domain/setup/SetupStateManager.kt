package com.example.inputmonitor.domain.setup

import com.example.inputmonitor.domain.model.SetupState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface responsible for determining system readiness, permission status,
 * service enablement, and active configuration (Architecture.md §14, §15, SRS.md §18).
 */
interface SetupStateManager {
    /**
     * Observes continuous updates of the application [SetupState].
     */
    fun observeSetupState(): StateFlow<SetupState>

    /**
     * Inspects system services, permissions, and database to refresh the current [SetupState].
     * Invoked when the application returns to foreground or user interacts with setup steps.
     */
    suspend fun refresh()

    /**
     * Returns the current immediate snapshot of [SetupState].
     */
    fun getSetupState(): SetupState
}
