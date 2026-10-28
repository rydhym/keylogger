package com.example.inputmonitor.domain.model

/**
 * Aggregated event counts for dashboard and analytics display (Architecture.md §55, SRS.md §47).
 *
 * Exposes counts without loading unbounded event entities into memory (IMPLEMENTATION_PLAN.md §50).
 */
data class EventCounts(
    val total: Long = 0,
    val keyboard: Long = 0,
    val accessibility: Long = 0,
    val clicks: Long = 0,
    val focus: Long = 0,
    val scrolls: Long = 0,
    val windowChanges: Long = 0
)
