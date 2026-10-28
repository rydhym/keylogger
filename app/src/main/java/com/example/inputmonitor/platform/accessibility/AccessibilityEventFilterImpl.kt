package com.example.inputmonitor.platform.accessibility

import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicLong

/**
 * Production implementation of [AccessibilityEventFilter] (Architecture.md §20, TESTING.md §14).
 *
 * Provides category filtering, self-monitoring suppression, and scroll storm rate-limiting.
 */
class AccessibilityEventFilterImpl(
    private val ownPackageName: String,
    private val config: AccessibilityConfig = AccessibilityConfig()
) : AccessibilityEventFilter {

    private val lastScrollTimeMs = AtomicLong(0L)

    override fun shouldProcess(event: AccessibilityEventInfo?): Boolean {
        if (event == null) return false

        val eventPackage = event.packageName?.toString()

        // Suppress self-monitoring feedback loops
        if (config.ignoreOwnAppPackage && eventPackage == ownPackageName) {
            return false
        }

        return when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> config.monitorClicks
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> config.monitorFocus
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!config.monitorScrolls) return false
                val now = System.currentTimeMillis()
                val prev = lastScrollTimeMs.get()
                if (now - prev < config.minScrollIntervalMs) {
                    false
                } else {
                    lastScrollTimeMs.set(now)
                    true
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> config.monitorWindowChanges
            AccessibilityEvent.TYPE_VIEW_SELECTED -> config.monitorSelections
            else -> false
        }
    }
}
