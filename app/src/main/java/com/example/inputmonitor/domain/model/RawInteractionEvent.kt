package com.example.inputmonitor.domain.model

/**
 * Domain model representing an ephemeral raw event emitted by platform services
 * (Keyboard or Accessibility) before session gating, privacy filtering, and resolution
 * (Architecture.md §36).
 *
 * Raw events are never persisted directly into storage.
 */
data class RawInteractionEvent(
    val timestamp: Long,
    val source: EventSource,
    val eventType: EventType,
    val packageName: String?,
    val candidateData: String?,
    val inputClassification: InputClassification = InputClassification.UNSPECIFIED,
    val metadata: Map<String, String> = emptyMap()
)
