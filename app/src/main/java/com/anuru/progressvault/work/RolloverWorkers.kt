package com.anuru.progressvault.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anuru.progressvault.data.db.dao.HistoryDao
import com.anuru.progressvault.data.repository.PlannerRepository
import com.anuru.progressvault.data.repository.WeeklyRolloverRepository
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.time.WeekCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Runs daily (so it effectively fires right after Monday 00:00) and on app start:
 * archives any finished week into an immutable WeeklySnapshot.
 */
@HiltWorker
class WeeklyRolloverWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rolloverRepository: WeeklyRolloverRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Archiving only. The Monday summary + rollover prompt is posted by ReminderWorker at
        // the user's reminder time, rather than at midnight.
        rolloverRepository.archiveFinishedWeeks()
        return Result.success()
    }
}

/** Performs (or declines) the task rollover chosen from the notification. */
@HiltWorker
class RollTasksWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val plannerRepository: PlannerRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val week = LocalDate.ofEpochDay(inputData.getLong(KEY_WEEK, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return Result.failure())
        if (inputData.getBoolean(KEY_ROLL, false)) plannerRepository.rollOver(week)
        settingsRepository.setRolloverHandled(week)
        return Result.success()
    }

    companion object {
        const val KEY_WEEK = "week"
        const val KEY_ROLL = "roll"
    }
}

class RolloverActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val week = intent.getLongExtra(EXTRA_WEEK_START, Long.MIN_VALUE)
        if (week == Long.MIN_VALUE) return
        val roll = intent.action == ACTION_ROLL
        WorkManager.getInstance(context).enqueueUniqueWork(
            "roll-tasks-$week",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RollTasksWorker>()
                .setInputData(workDataOf(RollTasksWorker.KEY_WEEK to week, RollTasksWorker.KEY_ROLL to roll))
                .build(),
        )
        androidx.core.app.NotificationManagerCompat.from(context).cancel(Notifier.ID_ROLLOVER)
    }

    companion object {
        const val ACTION_ROLL = "com.anuru.progressvault.action.ROLL_TASKS"
        const val ACTION_DISMISS = "com.anuru.progressvault.action.DISMISS_ROLLOVER"
        const val EXTRA_WEEK_START = "week_start"
    }
}

object WorkScheduler {
    private const val WEEKLY = "weekly-rollover"
    private const val CATCH_UP = "weekly-rollover-catchup"

    /** Daily job aligned to ~00:05 local, plus an immediate idempotent catch-up run. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val now = java.time.ZonedDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(0, 5)).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        wm.enqueueUniquePeriodicWork(
            WEEKLY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeeklyRolloverWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build(),
        )
        wm.enqueueUniqueWork(CATCH_UP, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WeeklyRolloverWorker>().build())
    }
}
