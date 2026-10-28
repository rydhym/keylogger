package com.example.inputmonitor.domain.backup.model

/**
 * Metadata accompanying a backup transmission.
 */
data class BackupMetadata(
    val fileName: String,
    val fileSize: Long,
    val eventCount: Long,
    val timestamp: Long = System.currentTimeMillis()
)
