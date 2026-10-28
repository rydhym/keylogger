package com.example.inputmonitor.platform.accessibility

import android.view.accessibility.AccessibilityEvent
import com.example.inputmonitor.domain.model.RawInteractionEvent

/**
 * Transforms accessibility event representations into domain [RawInteractionEvent] models (Architecture.md §21).
 *
 * Implements Content Minimization and Sensitive Input Protection (Architecture.md §22, SECURITY.md §4).
 */
interface AccessibilityEventMapper {

    /**
     * Maps an [AccessibilityEventInfo] into a [RawInteractionEvent].
     */
    fun map(event: AccessibilityEventInfo): RawInteractionEvent

    /**
     * Convenience overload for Android [AccessibilityEvent].
     */
    fun map(event: AccessibilityEvent): RawInteractionEvent = map(event.toEventInfo())
}
