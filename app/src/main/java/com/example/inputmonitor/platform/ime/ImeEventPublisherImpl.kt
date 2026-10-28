package com.example.inputmonitor.platform.ime

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.domain.processor.EventProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Production implementation of [ImeEventPublisher] (Architecture.md §33).
 *
 * Dispatches raw interaction event candidates onto an asynchronous background scope
 * to guarantee that typing latency on the main thread is strictly unaffected.
 */
class ImeEventPublisherImpl(
    private val eventProcessor: EventProcessor,
    dispatchers: DispatcherProvider,
    private val scope: CoroutineScope = CoroutineScope(dispatchers.default + SupervisorJob())
) : ImeEventPublisher {

    override fun publish(event: RawInteractionEvent) {
        scope.launch {
            try {
                eventProcessor.process(event)
            } catch (_: Exception) {
                // Failure-tolerance: errors in event processing must never crash IME
            }
        }
    }
}
