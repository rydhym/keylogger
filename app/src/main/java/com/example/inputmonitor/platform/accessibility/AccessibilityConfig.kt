package com.example.inputmonitor.platform.accessibility

/**
 * Configuration options for accessibility event monitoring (Architecture.md §20, SRS.md §32).
 *
 * Controls event filtering and event storm suppression.
 */
data class AccessibilityConfig(
    val monitorClicks: Boolean = true,
    val monitorFocus: Boolean = true,
    val monitorScrolls: Boolean = true,
    val monitorWindowChanges: Boolean = true,
    val monitorSelections: Boolean = true,
    val ignoreOwnAppPackage: Boolean = true,
    val minScrollIntervalMs: Long = 250L // Rate limit for scroll event storms (TESTING.md §14)
)
