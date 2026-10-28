package com.example.inputmonitor.platform.accessibility

import android.view.accessibility.AccessibilityEvent

/**
 * Filter determining whether an accessibility event should be ingested (Architecture.md §20).
 *
 * Enforces monitored category selection and suppresses event storms (TESTING.md §14).
 */
interface AccessibilityEventFilter {

    /**
     * Evaluates whether an accessibility event should proceed to mapping and processing.
     *
     * @param event The accessibility event info.
     * @return true if the event should be processed, false to discard immediately.
     */
    fun shouldProcess(event: AccessibilityEventInfo?): Boolean

    /**
     * Convenience overload for Android [AccessibilityEvent].
     */
    fun shouldProcess(event: AccessibilityEvent?): Boolean = shouldProcess(event?.toEventInfo())
}
