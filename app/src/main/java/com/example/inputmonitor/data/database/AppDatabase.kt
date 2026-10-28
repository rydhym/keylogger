package com.example.inputmonitor.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.inputmonitor.data.dao.InteractionEventDao
import com.example.inputmonitor.data.dao.MonitoringSessionDao
import com.example.inputmonitor.data.entity.InteractionEventEntity
import com.example.inputmonitor.data.entity.MonitoringSessionEntity

/**
 * Main application Room database (Architecture.md §45).
 * Encrypted transparently at rest via SQLCipher (DEC-005).
 */
@Database(
    entities = [
        MonitoringSessionEntity::class,
        InteractionEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun monitoringSessionDao(): MonitoringSessionDao

    abstract fun interactionEventDao(): InteractionEventDao

    companion object {
        const val DATABASE_NAME = "input_monitor_encrypted.db"
    }
}
