package com.example.core.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.InternetStorerApplication
import java.util.concurrent.TimeUnit

class OfflineOperationRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? InternetStorerApplication ?: return Result.success()
            val container = app.container

            // 1. Recover any interrupted/stale operations
            container.offlineOperationEngine.recoverStaleOperations()

            // 2. Clean stale staging files in .tmp
            container.storageManager.cleanStaleTempFiles()

            // 3. Process any pending local operations
            container.offlineOperationEngine.processPendingOperations(maxBatch = 3)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "internet_storer_offline_maintenance"

        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<OfflineOperationRecoveryWorker>(
                    repeatInterval = 12,
                    repeatIntervalTimeUnit = TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (_: Exception) {
                // Safely handle environments or test runners where WorkManager is uninitialized
            }
        }
    }
}
