package com.anuru.progressvault.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anuru.progressvault.data.repository.SyncRepository
import com.anuru.progressvault.domain.sync.SyncError
import com.anuru.progressvault.domain.sync.SyncOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Syncs LeetCode + GitHub. Transient failures (offline, 5xx, rate limits) are retried with backoff. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcomes = syncRepository.syncAll().values
        val transient = outcomes.any {
            it is SyncOutcome.Failed && it.error in setOf(SyncError.NETWORK, SyncError.SERVER, SyncError.RATE_LIMITED)
        }
        return if (transient && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val PERIODIC = "sync-periodic"
        private const val ON_DEMAND = "sync-now"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** (Re)schedules the periodic sync; UPDATE keeps timing but applies a changed interval. */
        fun schedulePeriodic(context: Context, intervalHours: Int) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SyncWorker>(intervalHours.toLong().coerceAtLeast(1), TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build(),
            )
        }

        /** One-off sync that waits for connectivity (used at app start). */
        fun syncSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ON_DEMAND,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }
}
