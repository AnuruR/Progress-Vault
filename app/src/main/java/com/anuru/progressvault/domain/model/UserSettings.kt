package com.anuru.progressvault.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class UserSettings(
    val onboardingCompleted: Boolean = false,
    val leetCodeUsername: String = "",
    val gitHubUsername: String = "",
    /** First day the app was used — powers the "since I started" preset. */
    val firstLaunchDate: LocalDate? = null,
    val syncIntervalHours: Int = DEFAULT_SYNC_INTERVAL_HOURS,
    val reminderTime: LocalTime = DEFAULT_REMINDER_TIME,
    val dailyReminderEnabled: Boolean = true,
    val paceNudgeEnabled: Boolean = true,
    val weeklySummaryEnabled: Boolean = true,
    val monthlyRecapEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_SYNC_INTERVAL_HOURS = 6
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(20, 0)
    }
}
