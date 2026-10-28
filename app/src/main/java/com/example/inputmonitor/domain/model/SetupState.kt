package com.example.inputmonitor.domain.model

/**
 * Domain representation of application setup and system readiness state (Architecture.md §14, SRS.md §16–§18).
 *
 * Tracks Android runtime permissions, AccessibilityService status, IME installation,
 * IME enablement, IME selection, database readiness, active monitoring sessions,
 * and onboarding completion.
 */
data class SetupState(
    val runtimePermissionsReady: Boolean = true,
    val accessibilityEnabled: Boolean = false,
    val imeInstalled: Boolean = true,
    val imeEnabled: Boolean = false,
    val imeSelected: Boolean = false,
    val databaseReady: Boolean = true,
    val monitoringSessionActive: Boolean = false,
    val isOnboardingCompleted: Boolean = false
) {
    /**
     * True when all monitoring capabilities and services are fully enabled and verified (SRS.md §16).
     */
    val isComplete: Boolean
        get() = runtimePermissionsReady &&
                accessibilityEnabled &&
                imeEnabled &&
                imeSelected &&
                databaseReady

    /**
     * True when at least one monitoring pipeline (Accessibility or Custom Keyboard) is operational,
     * allowing partial setup execution without trapping the user (SRS.md §17, AC-35).
     */
    val isOperational: Boolean
        get() = databaseReady && (accessibilityEnabled || (imeEnabled && imeSelected))
}
