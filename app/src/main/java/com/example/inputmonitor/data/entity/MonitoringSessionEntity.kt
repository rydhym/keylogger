package com.example.inputmonitor.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a monitoring session record (Architecture.md §46).
 */
@Entity(tableName = "monitoring_sessions")
data class MonitoringSessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long?,
    val state: String,
    val androidVersion: String,
    val deviceModel: String,
    val appVersion: String,
    val notes: String?
)
