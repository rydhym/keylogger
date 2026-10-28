package com.example.inputmonitor.data.backup.file

import android.content.Context
import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.export.ExportResult
import com.example.inputmonitor.domain.usecase.ExportSessionUseCase
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of creating a temporary backup file.
 */
sealed class BackupFileResult {
    data class Success(
        val file: File,
        val eventCount: Long,
        val bytesWritten: Long
    ) : BackupFileResult()

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : BackupFileResult()
}

/**
 * Manages atomic creation, streaming, and cleanup of temporary backup files in [cacheDir/backups].
 * Reuses [ExportSessionUseCase] without duplicating CSV formatting or privacy sanitization logic.
 */
class BackupFileManager(
    private val context: Context,
    private val exportSessionUseCase: ExportSessionUseCase,
    private val dispatchers: DispatcherProvider
) {
    companion object {
        const val BACKUP_DIR_NAME = "backups"
        private const val STALE_THRESHOLD_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val backupDir: File
        get() = File(context.cacheDir, BACKUP_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }

    /**
     * Generates a temporary CSV backup file using the existing [ExportSessionUseCase].
     * File I/O is performed strictly on [dispatchers.io].
     *
     * Incomplete files are deleted on failure.
     */
    suspend fun createBackupFile(sessionId: String? = null): BackupFileResult = withContext(dispatchers.io) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanScope = (sessionId ?: "all_sessions").replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val finalFileName = "backup_${cleanScope}_$timestamp.csv"
        val tempFileName = "$finalFileName.tmp"

        val tempFile = File(backupDir, tempFileName)
        val finalFile = File(backupDir, finalFileName)

        try {
            var exportResult: ExportResult? = null
            FileOutputStream(tempFile).use { fos ->
                fos.buffered().use { bos ->
                    exportResult = exportSessionUseCase(
                        outputStream = bos,
                        sessionId = sessionId,
                        onProgress = null
                    )
                    bos.flush()
                }
            }

            when (val res = exportResult) {
                is ExportResult.Success -> {
                    // Atomic rename from .tmp to final .csv
                    if (tempFile.renameTo(finalFile)) {
                        BackupFileResult.Success(
                            file = finalFile,
                            eventCount = res.eventCount,
                            bytesWritten = res.bytesWritten
                        )
                    } else {
                        // Fallback copy & delete
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                        BackupFileResult.Success(
                            file = finalFile,
                            eventCount = res.eventCount,
                            bytesWritten = res.bytesWritten
                        )
                    }
                }
                is ExportResult.Error -> {
                    deleteFileQuietly(tempFile)
                    deleteFileQuietly(finalFile)
                    BackupFileResult.Error(res.message, res.throwable)
                }
                null -> {
                    deleteFileQuietly(tempFile)
                    deleteFileQuietly(finalFile)
                    BackupFileResult.Error("Export resulted in null state")
                }
            }
        } catch (t: Throwable) {
            deleteFileQuietly(tempFile)
            deleteFileQuietly(finalFile)
            BackupFileResult.Error(t.message ?: "Failed to generate backup file", t)
        }
    }

    /**
     * Deletes a specific backup file safely.
     */
    fun deleteBackupFile(file: File): Boolean {
        return deleteFileQuietly(file)
    }

    /**
     * Cleans up abandoned or stale temporary files in the backup directory older than [maxAgeMillis].
     */
    suspend fun deleteStaleBackups(maxAgeMillis: Long = STALE_THRESHOLD_MILLIS): Int = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        var deletedCount = 0
        val files = backupDir.listFiles() ?: return@withContext 0

        for (file in files) {
            val age = now - file.lastModified()
            // Delete if older than threshold or if it's an abandoned .tmp file older than 1 hour
            val isAbandonedTmp = file.name.endsWith(".tmp") && age > (60 * 60 * 1000L)
            if (age > maxAgeMillis || isAbandonedTmp) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        deletedCount
    }

    private fun deleteFileQuietly(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else false
        } catch (_: Throwable) {
            false
        }
    }
}
