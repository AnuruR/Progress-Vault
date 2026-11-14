package com.anuru.progressvault.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anuru.progressvault.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Non-secret settings, backed by Preferences DataStore. Tokens are stored elsewhere (encrypted). */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<UserSettings> = dataStore.data.map { p ->
        UserSettings(
            onboardingCompleted = p[Keys.ONBOARDING_COMPLETED] ?: false,
            leetCodeUsername = p[Keys.LEETCODE_USERNAME].orEmpty(),
            gitHubUsername = p[Keys.GITHUB_USERNAME].orEmpty(),
            firstLaunchDate = p[Keys.FIRST_LAUNCH_EPOCH_DAY]?.let(LocalDate::ofEpochDay),
            syncIntervalHours = p[Keys.SYNC_INTERVAL_HOURS] ?: UserSettings.DEFAULT_SYNC_INTERVAL_HOURS,
            reminderTime = p[Keys.REMINDER_MINUTE_OF_DAY]?.let { LocalTime.of(it / 60, it % 60) }
                ?: UserSettings.DEFAULT_REMINDER_TIME,
            dailyReminderEnabled = p[Keys.DAILY_REMINDER] ?: true,
            paceNudgeEnabled = p[Keys.PACE_NUDGE] ?: true,
            weeklySummaryEnabled = p[Keys.WEEKLY_SUMMARY] ?: true,
            monthlyRecapEnabled = p[Keys.MONTHLY_RECAP] ?: true,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    /** Records the first day of use exactly once. */
    suspend fun ensureFirstLaunchDate(today: LocalDate) {
        dataStore.edit { p ->
            if (p[Keys.FIRST_LAUNCH_EPOCH_DAY] == null) p[Keys.FIRST_LAUNCH_EPOCH_DAY] = today.toEpochDay()
        }
    }

    suspend fun setUsernames(leetCode: String, gitHub: String) {
        dataStore.edit { p ->
            p[Keys.LEETCODE_USERNAME] = leetCode.trim()
            p[Keys.GITHUB_USERNAME] = gitHub.trim()
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    /** Week (Monday) whose unfinished-task rollover the user already answered (rolled or dismissed). */
    val rolloverHandledWeek: Flow<LocalDate?> = dataStore.data.map { p -> p[Keys.ROLLOVER_HANDLED]?.let(LocalDate::ofEpochDay) }

    suspend fun setRolloverHandled(weekStart: LocalDate) {
        dataStore.edit { it[Keys.ROLLOVER_HANDLED] = weekStart.toEpochDay() }
    }

    /** Week whose rollover notification was already posted (so it's posted once). */
    suspend fun rolloverPromptedWeek(): LocalDate? = dataStore.data.first()[Keys.ROLLOVER_PROMPTED]?.let(LocalDate::ofEpochDay)

    suspend fun setRolloverPrompted(weekStart: LocalDate) {
        dataStore.edit { it[Keys.ROLLOVER_PROMPTED] = weekStart.toEpochDay() }
    }

    /** Returns true exactly once (first call), used for one-time data backfills. */
    suspend fun consumeOnce(flag: String): Boolean {
        var first = false
        dataStore.edit { p ->
            val key = booleanPreferencesKey("once_$flag")
            if (p[key] != true) { p[key] = true; first = true }
        }
        return first
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        dataStore.edit { it[Keys.SYNC_INTERVAL_HOURS] = hours }
    }

    suspend fun setReminderTime(time: LocalTime) {
        dataStore.edit { it[Keys.REMINDER_MINUTE_OF_DAY] = time.hour * 60 + time.minute }
    }

    suspend fun setReminderToggles(daily: Boolean? = null, pace: Boolean? = null, weekly: Boolean? = null, monthly: Boolean? = null) {
        dataStore.edit { p ->
            daily?.let { p[Keys.DAILY_REMINDER] = it }
            pace?.let { p[Keys.PACE_NUDGE] = it }
            weekly?.let { p[Keys.WEEKLY_SUMMARY] = it }
            monthly?.let { p[Keys.MONTHLY_RECAP] = it }
        }
    }

    /** Month (yyyy-MM) whose recap notification was already posted. */
    suspend fun recapNotifiedMonth(): String? = dataStore.data.first()[Keys.RECAP_NOTIFIED]
    suspend fun setRecapNotified(month: String) { dataStore.edit { it[Keys.RECAP_NOTIFIED] = month } }

    /** Date the pace nudge was last posted (once per week at most). */
    suspend fun paceNudgedOn(): LocalDate? = dataStore.data.first()[Keys.PACE_NUDGED]?.let(LocalDate::ofEpochDay)
    suspend fun setPaceNudged(date: LocalDate) { dataStore.edit { it[Keys.PACE_NUDGED] = date.toEpochDay() } }

    /** Week whose Monday summary was already posted. */
    suspend fun summaryNotifiedWeek(): LocalDate? = dataStore.data.first()[Keys.SUMMARY_NOTIFIED]?.let(LocalDate::ofEpochDay)
    suspend fun setSummaryNotified(week: LocalDate) { dataStore.edit { it[Keys.SUMMARY_NOTIFIED] = week.toEpochDay() } }

    val notificationPermissionAsked: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIF_ASKED] ?: false }
    suspend fun setNotificationPermissionAsked() { dataStore.edit { it[Keys.NOTIF_ASKED] = true } }

    suspend fun exportBackup(): com.anuru.progressvault.data.backup.BackupSettings {
        val p = dataStore.data.first()
        val s = current()
        return com.anuru.progressvault.data.backup.BackupSettings(
            leetCodeUsername = s.leetCodeUsername,
            gitHubUsername = s.gitHubUsername,
            firstLaunchEpochDay = p[Keys.FIRST_LAUNCH_EPOCH_DAY],
            syncIntervalHours = s.syncIntervalHours,
            reminderMinuteOfDay = s.reminderTime.hour * 60 + s.reminderTime.minute,
            dailyReminder = s.dailyReminderEnabled,
            paceNudge = s.paceNudgeEnabled,
            weeklySummary = s.weeklySummaryEnabled,
            monthlyRecap = s.monthlyRecapEnabled,
        )
    }

    /** Restores backed-up settings and marks onboarding complete (the data is already set up). */
    suspend fun restoreBackup(b: com.anuru.progressvault.data.backup.BackupSettings) {
        dataStore.edit { p ->
            p[Keys.LEETCODE_USERNAME] = b.leetCodeUsername
            p[Keys.GITHUB_USERNAME] = b.gitHubUsername
            b.firstLaunchEpochDay?.let { p[Keys.FIRST_LAUNCH_EPOCH_DAY] = it }
            p[Keys.SYNC_INTERVAL_HOURS] = b.syncIntervalHours.coerceIn(1, 24)
            p[Keys.REMINDER_MINUTE_OF_DAY] = b.reminderMinuteOfDay.coerceIn(0, 24 * 60 - 1)
            p[Keys.DAILY_REMINDER] = b.dailyReminder
            p[Keys.PACE_NUDGE] = b.paceNudge
            p[Keys.WEEKLY_SUMMARY] = b.weeklySummary
            p[Keys.MONTHLY_RECAP] = b.monthlyRecap
            p[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LEETCODE_USERNAME = stringPreferencesKey("leetcode_username")
        val GITHUB_USERNAME = stringPreferencesKey("github_username")
        val FIRST_LAUNCH_EPOCH_DAY = longPreferencesKey("first_launch_epoch_day")
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        val REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
        val DAILY_REMINDER = booleanPreferencesKey("notif_daily_reminder")
        val PACE_NUDGE = booleanPreferencesKey("notif_pace_nudge")
        val WEEKLY_SUMMARY = booleanPreferencesKey("notif_weekly_summary")
        val MONTHLY_RECAP = booleanPreferencesKey("notif_monthly_recap")
        val ROLLOVER_HANDLED = longPreferencesKey("rollover_handled_week")
        val ROLLOVER_PROMPTED = longPreferencesKey("rollover_prompted_week")
        val RECAP_NOTIFIED = stringPreferencesKey("recap_notified_month")
        val PACE_NUDGED = longPreferencesKey("pace_nudged_on")
        val SUMMARY_NOTIFIED = longPreferencesKey("summary_notified_week")
        val NOTIF_ASKED = booleanPreferencesKey("notif_permission_asked")
    }
}
