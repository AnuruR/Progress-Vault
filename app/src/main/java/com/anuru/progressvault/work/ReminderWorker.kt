package com.anuru.progressvault.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anuru.progressvault.data.db.dao.HistoryDao
import com.anuru.progressvault.data.repository.HistoryRepository
import com.anuru.progressvault.data.repository.PlannerRepository
import com.anuru.progressvault.data.repository.ProgressRepository
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.history.HistoryCalculator
import com.anuru.progressvault.domain.notify.NotificationPlanner
import com.anuru.progressvault.domain.progress.ProgressCalculator
import com.anuru.progressvault.domain.time.WeekCalculator
import com.anuru.progressvault.domain.time.WeekRange
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs once a day at the user's reminder time and posts whichever notifications are due:
 * daily status, Wednesday pace nudge, Monday summary (+ rollover prompt) and monthly recap.
 * Each is gated by its Settings toggle and "already shown" bookkeeping, so re-runs are harmless.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val progressRepository: ProgressRepository,
    private val plannerRepository: PlannerRepository,
    private val historyRepository: HistoryRepository,
    private val historyDao: HistoryDao,
    private val weekCalculator: WeekCalculator,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!notifier.canPost()) return Result.success()
        val s = settingsRepository.current()
        if (!s.onboardingCompleted) return Result.success()
        val today = weekCalculator.today()
        val week = weekCalculator.currentWeek()

        // Monday summary of last week, combined with the rollover prompt.
        NotificationPlanner.summaryWeekDue(week.start, settingsRepository.summaryNotifiedWeek())?.let { prevStart ->
            val prev = WeekRange(prevStart)
            val snapshot = historyDao.getSnapshot(prevStart)
            val handled = settingsRepository.rolloverHandledWeek.first() == prevStart
            val unfinished = if (handled) 0 else plannerRepository.todoCount(prevStart)
            if (snapshot != null && (s.weeklySummaryEnabled || unfinished > 0)) {
                val prevProgress = progressRepository.observeWeek(prev).first()
                notifier.showWeeklySummary(
                    week = prev,
                    percent = (snapshot.overallCompletion * 100).toInt(),
                    completedSections = prevProgress.sections.count { it.countsInDial && it.isComplete },
                    dialSections = prevProgress.sections.count { it.countsInDial },
                    unfinishedTasks = unfinished,
                    includeSummary = s.weeklySummaryEnabled,
                )
                settingsRepository.setRolloverPrompted(prevStart)
            }
            settingsRepository.setSummaryNotified(prevStart)
        }

        val progress = progressRepository.observeWeek(week).first()
        val elapsed = weekCalculator.fractionElapsed(week)
        val pace = ProgressCalculator.pace(progress.overall, elapsed)
        val percent = (progress.overall * 100).toInt()

        if (NotificationPlanner.shouldNudgePace(today, week.start, pace, s.paceNudgeEnabled, settingsRepository.paceNudgedOn())) {
            notifier.showPaceNudge(percent, (elapsed * 100).toInt(), NotificationPlanner.focusSection(progress.sections))
            settingsRepository.setPaceNudged(today)
        } else if (s.dailyReminderEnabled) {
            notifier.showDailyReminder(percent, NotificationPlanner.focusSection(progress.sections))
        }

        NotificationPlanner.recapMonthDue(today, s.monthlyRecapEnabled, settingsRepository.recapNotifiedMonth(), s.firstLaunchDate)?.let { month ->
            val recap = HistoryCalculator.monthlyRecap(historyRepository.observe().first(), month)
            if (!recap.isEmpty) notifier.showMonthlyRecap(recap)
            settingsRepository.setRecapNotified(month.toString())
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-reminder"

        /** (Re)aligns the daily job to [time]; REPLACE so a changed time takes effect immediately. */
        fun schedule(context: Context, time: LocalTime) {
            val now = ZonedDateTime.now()
            var next = now.toLocalDate().atTime(time).atZone(now.zone)
            if (!next.isAfter(now)) next = next.plusDays(1)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}
