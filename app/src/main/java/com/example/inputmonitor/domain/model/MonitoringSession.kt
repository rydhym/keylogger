package com.example.inputmonitor.domain.model

/**
 * Domain model representing a bounded monitoring session (Architecture.md §46, SRS.md §1.1).
 */
data class MonitoringSession(
    val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    val state: SessionState = SessionState.ACTIVE,
    val androidVersion: String = "15",
    val deviceModel: String = "Android Device",
    val appVersion: String = "0.1.0",
    val notes: String? = null
)
