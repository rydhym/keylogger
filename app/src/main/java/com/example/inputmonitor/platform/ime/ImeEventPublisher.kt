package com.example.inputmonitor.platform.ime

import com.example.inputmonitor.domain.model.RawInteractionEvent

/**
 * Asynchronously publishes interaction event candidates from the keyboard service
 * to the central event pipeline (Architecture.md §33).
 */
interface ImeEventPublisher {

    /**
     * Emits a raw event candidate. Must return immediately without blocking UI typing.
     */
    fun publish(event: RawInteractionEvent)
}
