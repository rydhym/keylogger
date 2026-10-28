package com.example.inputmonitor.platform.accessibility

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.domain.processor.EventProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Production implementation of [AccessibilityEventPublisher] (Architecture.md §19).
 *
 * Dispatches raw accessibility events onto a background coroutine scope with supervisor isolation,
 * ensuring zero blocking on the Android accessibility IPC callback thread.
 */
class AccessibilityEventPublisherImpl(
    private val eventProcessor: EventProcessor,
    dispatchers: DispatcherProvider,
    private val scope: CoroutineScope = CoroutineScope(dispatchers.default + SupervisorJob())
) : AccessibilityEventPublisher {

    override fun publish(event: RawInteractionEvent) {
        scope.launch {
            try {
                eventProcessor.process(event)
            } catch (_: Exception) {
                // Fail-safe: background event errors must never crash or affect AccessibilityService
            }
        }
    }
}
