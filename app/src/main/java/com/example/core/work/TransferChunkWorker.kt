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

class TransferChunkWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? InternetStorerApplication ?: return Result.success()
            val container = app.container

            // 1. Recover any transfers interrupted by app termination
            container.transferEngine.recoverInterruptedTransfers()

            // 2. Perform local storage integrity check
            container.integrityEngine.runIntegrityCheck()

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "internet_storer_transfer_recovery"

        fun schedule(context: Context) {
            try {
                // Strictly offline-compatible constraints - NO NetworkType required!
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<TransferChunkWorker>(
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
