package com.example.inputmonitor.domain.backup.repository

import com.example.inputmonitor.domain.backup.model.BackupMetadata
import com.example.inputmonitor.domain.backup.model.BackupResult
import java.io.File

/**
 * Provider-agnostic abstraction for uploading backups to an authenticated cloud destination.
 *
 * Decouples background workers from specific cloud implementations (Google Drive, WebDAV, REST API, etc.).
 */
interface BackupRepository {
    /**
     * Uploads [file] with accompanying [metadata] to the configured cloud destination.
     */
    suspend fun uploadBackup(file: File, metadata: BackupMetadata): BackupResult

    /**
     * Verifies connectivity and authentication credentials with [endpoint] without uploading backup data.
     */
    suspend fun testConnection(endpoint: String, token: String?): BackupResult
}
