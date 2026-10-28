package com.example.inputmonitor.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an interaction event record (Architecture.md §47).
 *
 * Foreign key relationship:
 * Every event is associated with a session. Deleting a session cascades to delete its events.
 */
@Entity(
    tableName = "interaction_events",
    foreignKeys = [
        ForeignKey(
            entity = MonitoringSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("timestamp"),
        Index("packageName"),
        Index("eventType"),
        Index("applicationName"),
        Index(value = ["sessionId", "timestamp"])
    ]
)
data class InteractionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val source: String,
    val eventType: String,
    val packageName: String?,
    val applicationName: String?,
    val eventData: String?,
    val inputClassification: String,
    val metadata: String?
)
