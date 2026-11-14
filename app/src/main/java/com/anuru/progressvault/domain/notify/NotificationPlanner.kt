package com.anuru.progressvault.domain.notify

import com.anuru.progressvault.domain.progress.PaceStatus
import com.anuru.progressvault.domain.progress.SectionProgress
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Pure "should we notify?" rules, so they can be unit-tested without Android. */
object NotificationPlanner {

    /** The unfinished dial section closest to done, i.e. the most motivating "to go" hint. */
    fun focusSection(sections: List<SectionProgress>): SectionProgress? =
        sections.filter { it.countsInDial && it.target > 0 && !it.isComplete }.maxByOrNull { it.fraction }

    /** Wednesday nudge, at most once per week, only when behind pace. */
    fun shouldNudgePace(today: LocalDate, weekStart: LocalDate, pace: PaceStatus, enabled: Boolean, lastNudged: LocalDate?): Boolean =
        enabled && today.dayOfWeek == DayOfWeek.WEDNESDAY && pace == PaceStatus.BEHIND &&
            (lastNudged == null || lastNudged.isBefore(weekStart))

    /** The previous week, if its Monday summary hasn't been shown yet. */
    fun summaryWeekDue(currentWeekStart: LocalDate, lastSummaryWeek: LocalDate?): LocalDate? {
        val previous = currentWeekStart.minusWeeks(1)
        return if (lastSummaryWeek == null || lastSummaryWeek.isBefore(previous)) previous else null
    }

    /** Last month, if its recap hasn't been shown yet. Fires on the first reminder of a new month. */
    fun recapMonthDue(today: LocalDate, enabled: Boolean, lastNotified: String?, firstDay: LocalDate?): YearMonth? {
        if (!enabled) return null
        val previous = YearMonth.from(today).minusMonths(1)
        // Nothing to recap for months before the app was used.
        if (firstDay != null && YearMonth.from(firstDay).isAfter(previous)) return null
        return previous.takeIf { lastNotified != it.toString() }
    }
}
