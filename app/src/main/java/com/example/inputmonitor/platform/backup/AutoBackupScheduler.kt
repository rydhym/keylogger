package com.example.inputmonitor.platform.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Interface defining operations for scheduling and triggering background cloud backups.
 */
interface AutoBackupScheduler {
    fun schedulePeriodicBackup(wifiOnly: Boolean)
    fun triggerImmediateBackup(wifiOnly: Boolean)
    fun cancelPeriodicBackup()
}

/**
 * WorkManager implementation guaranteeing unique periodic scheduling (every 24 hours)
 * and single-instance manual backups without worker duplication.
 */
class WorkManagerAutoBackupScheduler(
    private val context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context)
) : AutoBackupScheduler {

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "inputmonitor_auto_backup_periodic"
        const val UNIQUE_ONE_TIME_WORK_NAME = "inputmonitor_auto_backup_immediate"
        const val BACKUP_INTERVAL_HOURS = 24L
    }

    override fun schedulePeriodicBackup(wifiOnly: Boolean) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            BACKUP_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        // ExistingPeriodicWorkPolicy.UPDATE ensures toggling settings safely replaces the job without duplicates
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    override fun triggerImmediateBackup(wifiOnly: Boolean) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        // ExistingWorkPolicy.KEEP prevents duplicate concurrent manual backup jobs
        workManager.enqueueUniqueWork(
            UNIQUE_ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            oneTimeRequest
        )
    }

    override fun cancelPeriodicBackup() {
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
    }
}
