package com.anuru.progressvault.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anuru.progressvault.MainActivity
import com.anuru.progressvault.R
import com.anuru.progressvault.domain.history.MonthlyRecap
import com.anuru.progressvault.domain.progress.SectionProgress
import com.anuru.progressvault.domain.time.WeekRange
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.main.DeepLinks
import java.time.format.TextStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            Triple(CHANNEL_WEEKLY, R.string.channel_weekly, R.string.channel_weekly_desc),
            Triple(CHANNEL_REMINDERS, R.string.channel_reminders, R.string.channel_reminders_desc),
            Triple(CHANNEL_RECAP, R.string.channel_recap, R.string.channel_recap_desc),
        ).forEach { (id, name, desc) ->
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(desc)
                },
            )
        }
    }

    private fun openIntent(target: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(DeepLinks.EXTRA_TARGET, target),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(id: Int, channel: String, title: String, text: String, target: String, color: Int = R.color.vault_cyan) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_vault)
            .setColor(ContextCompat.getColor(context, color))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(target, id))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked in canPost()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    private fun focusText(focus: SectionProgress?): String? = focus?.let {
        context.getString(R.string.notif_focus, formatAmount((it.target - it.achieved).coerceAtLeast(0.0)), it.name, it.unit)
    }

    /** "45% this week · LeetCode 1/2 problems". */
    fun showDailyReminder(percent: Int, focus: SectionProgress?) {
        val text = if (percent >= 100) context.getString(R.string.notif_daily_done)
        else listOfNotNull(context.getString(R.string.notif_daily_status, percent), focusText(focus)).joinToString(" · ")
        post(ID_DAILY, CHANNEL_REMINDERS, context.getString(R.string.notif_daily_title), text, DeepLinks.TARGET_DASHBOARD)
    }

    fun showPaceNudge(percent: Int, elapsedPercent: Int, focus: SectionProgress?) {
        val text = listOfNotNull(context.getString(R.string.notif_pace_body, percent, elapsedPercent), focusText(focus)).joinToString(" ")
        post(ID_PACE, CHANNEL_REMINDERS, context.getString(R.string.notif_pace_title), text, DeepLinks.TARGET_DASHBOARD, R.color.vault_amber)
    }

    fun showMonthlyRecap(recap: MonthlyRecap) {
        val month = recap.month.month.getDisplayName(TextStyle.FULL, context.resources.configuration.locales[0])
        val parts = listOfNotNull(
            recap.problems.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.recap_problems, it, it) },
            recap.commits.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.recap_commits, it, it) },
            recap.posts.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.recap_posts, it, it) },
            recap.lessons.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.recap_lessons, it, it) },
            recap.studyHours.takeIf { it > 0 }?.let { context.getString(R.string.recap_hours, formatAmount(it)) },
            recap.coursesFinished.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.recap_courses, it, it) },
            recap.bestWeek?.takeIf { it.completion > 0 }?.let { context.getString(R.string.recap_best_week, (it.completion * 100).toInt()) },
        )
        post(
            ID_RECAP, CHANNEL_RECAP,
            context.getString(R.string.notif_recap_title, month),
            context.getString(R.string.notif_recap_body, month, parts.joinToString(", ")),
            DeepLinks.TARGET_RECAP + recap.month, R.color.vault_amber,
        )
    }

    /** Monday summary; adds Roll over / Leave actions when last week has unfinished tasks. */
    fun showWeeklySummary(week: WeekRange, percent: Int, completedSections: Int, dialSections: Int, unfinishedTasks: Int, includeSummary: Boolean) {
        if (!includeSummary) {
            if (unfinishedTasks > 0) showRolloverPrompt(week, unfinishedTasks, percent)
            return
        }
        if (!canPost()) return
        val summary = context.resources.getQuantityString(R.plurals.notif_summary_body, dialSections, percent, completedSections, dialSections)
        val text = if (unfinishedTasks > 0) summary + " " + context.resources.getQuantityString(R.plurals.notif_summary_tasks, unfinishedTasks, unfinishedTasks) else summary
        val builder = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(R.drawable.ic_stat_vault)
            .setColor(ContextCompat.getColor(context, R.color.vault_cyan))
            .setContentTitle(context.getString(R.string.notif_summary_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(if (unfinishedTasks > 0) DeepLinks.TARGET_PLANNER else DeepLinks.TARGET_DASHBOARD, ID_ROLLOVER))
            .setAutoCancel(true)
        if (unfinishedTasks > 0) {
            builder.addAction(0, context.getString(R.string.action_roll_over), rolloverAction(week, true))
            builder.addAction(0, context.getString(R.string.action_leave_them), rolloverAction(week, false))
        }
        @Suppress("MissingPermission") // checked in canPost()
        NotificationManagerCompat.from(context).notify(ID_ROLLOVER, builder.build())
    }

    private fun rolloverAction(week: WeekRange, roll: Boolean) = PendingIntent.getBroadcast(
        context, if (roll) 1 else 2,
        Intent(context, RolloverActionReceiver::class.java)
            .setAction(if (roll) RolloverActionReceiver.ACTION_ROLL else RolloverActionReceiver.ACTION_DISMISS)
            .putExtra(RolloverActionReceiver.EXTRA_WEEK_START, week.start.toEpochDay()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** "You have N unfinished tasks from last week — roll them over?" with Roll / Leave actions. */
    fun showRolloverPrompt(finishedWeek: WeekRange, unfinished: Int, overallPercent: Int) {
        if (!canPost()) return
        val open = openIntent(DeepLinks.TARGET_PLANNER, ID_ROLLOVER)
        fun action(roll: Boolean) = rolloverAction(finishedWeek, roll)
        val text = context.resources.getQuantityString(R.plurals.notif_rollover_body, unfinished, overallPercent, unfinished)
        val notification = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(R.drawable.ic_stat_vault)
            .setColor(ContextCompat.getColor(context, R.color.vault_cyan))
            .setContentTitle(context.getString(R.string.notif_rollover_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_roll_over), action(true))
            .addAction(0, context.getString(R.string.action_leave_them), action(false))
            .build()
        @Suppress("MissingPermission") // checked in canPost()
        NotificationManagerCompat.from(context).notify(ID_ROLLOVER, notification)
    }

    fun cancelRollover() = NotificationManagerCompat.from(context).cancel(ID_ROLLOVER)

    companion object {
        const val CHANNEL_WEEKLY = "weekly"
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_RECAP = "recap"
        const val ID_ROLLOVER = 1001
        const val ID_DAILY = 1002
        const val ID_PACE = 1003
        const val ID_RECAP = 1004
    }
}
