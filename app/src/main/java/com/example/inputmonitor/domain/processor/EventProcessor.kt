package com.example.inputmonitor.domain.processor

import com.example.inputmonitor.domain.model.RawInteractionEvent

/**
 * Central event ingestion pipeline interface (Architecture.md §37, §38).
 */
interface EventProcessor {

    /**
     * Processes an incoming raw interaction event through gating, privacy filtering,
     * application context resolution, and persistent storage.
     *
     * @param event The raw interaction event candidate.
     * @return true if the event was permitted and successfully persisted, false if dropped or failed.
     */
    suspend fun process(event: RawInteractionEvent): Boolean
}
