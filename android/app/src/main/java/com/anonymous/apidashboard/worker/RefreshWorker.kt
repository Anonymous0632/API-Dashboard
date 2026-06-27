package com.anonymous.apidashboard.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anonymous.apidashboard.network.QuotaRepository
import com.anonymous.apidashboard.widget.ApiQuotaWidget
import java.util.concurrent.TimeUnit

class RefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            QuotaRepository(applicationContext).refresh()
            ApiQuotaWidget().updateAll(applicationContext)
            Result.success()
        }.getOrElse {
            ApiQuotaWidget().updateAll(applicationContext)
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "aiquota.periodic.refresh"
        private const val ONCE_NAME = "aiquota.once.refresh"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun refreshOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
