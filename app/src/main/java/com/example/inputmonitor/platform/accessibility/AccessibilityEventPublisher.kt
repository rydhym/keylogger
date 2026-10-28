package com.example.inputmonitor.platform.accessibility

import com.example.inputmonitor.domain.model.RawInteractionEvent

/**
 * Asynchronously publishes accessibility interaction events to the central pipeline (Architecture.md §19).
 *
 * Ensures [android.accessibilityservice.AccessibilityService.onAccessibilityEvent] returns immediately.
 */
interface AccessibilityEventPublisher {

    /**
     * Emits a raw accessibility event candidate asynchronously without blocking the service callback.
     */
    fun publish(event: RawInteractionEvent)
}
