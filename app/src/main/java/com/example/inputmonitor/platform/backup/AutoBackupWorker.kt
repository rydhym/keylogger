package com.example.inputmonitor.platform.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.inputmonitor.MonitoringApplication
import com.example.inputmonitor.data.backup.file.BackupFileManager
import com.example.inputmonitor.data.backup.file.BackupFileResult
import com.example.inputmonitor.data.backup.security.BackupTokenStorage
import com.example.inputmonitor.domain.backup.model.BackupMetadata
import com.example.inputmonitor.domain.backup.model.BackupResult
import com.example.inputmonitor.domain.backup.repository.BackupRepository
import com.example.inputmonitor.domain.backup.repository.BackupSettingsRepository

/**
 * WorkManager CoroutineWorker executing the automatic background backup pipeline.
 *
 * Execution Steps:
 * 1. Checks if backup is enabled (returns success if disabled).
 * 2. Validates destination configuration.
 * 3. Deletes any stale or abandoned temporary backups.
 * 4. Records the attempt timestamp.
 * 5. Generates the temporary sanitized CSV backup file via existing ExportSessionUseCase.
 * 6. Performs streaming upload over authenticated TLS/HTTPS via BackupRepository.
 * 7. Records status/timestamps in BackupSettingsRepository.
 * 8. Deletes temporary backup file in all completion paths.
 */
class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // Internal dependency holder allowing unit tests to supply fakes directly
    var dependenciesOverride: Dependencies? = null

    data class Dependencies(
        val backupFileManager: BackupFileManager,
        val backupRepository: BackupRepository,
        val backupSettingsRepository: BackupSettingsRepository,
        val tokenStorage: BackupTokenStorage
    )

    private fun resolveDependencies(): Dependencies {
        dependenciesOverride?.let { return it }
        val app = applicationContext as? MonitoringApplication
            ?: throw IllegalStateException("ApplicationContext must be MonitoringApplication")
        val container = app.container
        return Dependencies(
            backupFileManager = container.backupFileManager,
            backupRepository = container.backupRepository,
            backupSettingsRepository = container.backupSettingsRepository,
            tokenStorage = container.backupTokenStorage
        )
    }

    override suspend fun doWork(): Result {
        val deps = try {
            resolveDependencies()
        } catch (_: Throwable) {
            return Result.failure()
        }

        val settings = deps.backupSettingsRepository.getBackupSettings()

        // 1. If backup is not enabled, complete without uploading
        if (!settings.enabled) {
            return Result.success()
        }

        // 2. If endpoint is not configured, record failure and terminate without retrying
        if (!settings.isConfigured) {
            deps.backupSettingsRepository.recordBackupFailure(
                reason = "Backup endpoint is not configured",
                authRequired = false
            )
            return Result.failure()
        }

        // 3. Clean up stale or orphaned backup files
        deps.backupFileManager.deleteStaleBackups()

        // 4. Record backup attempt
        val attemptTimestamp = System.currentTimeMillis()
        deps.backupSettingsRepository.recordBackupAttempt(attemptTimestamp)

        // 5. Generate CSV backup file
        val fileResult = deps.backupFileManager.createBackupFile()
        if (fileResult !is BackupFileResult.Success) {
            val errorMsg = (fileResult as? BackupFileResult.Error)?.message
                ?: "Failed to generate CSV backup file"
            deps.backupSettingsRepository.recordBackupFailure(errorMsg)
            return Result.retry()
        }

        val backupFile = fileResult.file
        try {
            val metadata = BackupMetadata(
                fileName = backupFile.name,
                fileSize = backupFile.length(),
                eventCount = fileResult.eventCount,
                timestamp = attemptTimestamp
            )

            // 6. Upload over authenticated HTTPS
            val uploadResult = deps.backupRepository.uploadBackup(backupFile, metadata)

            // 7. Process upload result
            return when (uploadResult) {
                is BackupResult.Success -> {
                    deps.backupSettingsRepository.recordBackupSuccess(
                        timestamp = uploadResult.timestamp,
                        remoteBackupId = uploadResult.remoteBackupId
                    )
                    Result.success()
                }
                is BackupResult.AuthenticationRequired -> {
                    deps.backupSettingsRepository.recordBackupFailure(
                        reason = uploadResult.message,
                        authRequired = true
                    )
                    // Return failure so WorkManager does NOT hammer the server with retries
                    Result.failure()
                }
                is BackupResult.RetryableFailure -> {
                    deps.backupSettingsRepository.recordBackupFailure(uploadResult.message)
                    Result.retry()
                }
                is BackupResult.PermanentFailure -> {
                    deps.backupSettingsRepository.recordBackupFailure(uploadResult.message)
                    Result.failure()
                }
            }
        } finally {
            // 8. Always delete temporary backup file
            deps.backupFileManager.deleteBackupFile(backupFile)
        }
    }
}
