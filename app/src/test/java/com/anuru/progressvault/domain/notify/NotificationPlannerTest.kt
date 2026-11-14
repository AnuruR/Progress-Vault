package com.anuru.progressvault.domain.notify

import com.anuru.progressvault.domain.progress.PaceStatus
import com.anuru.progressvault.domain.progress.SectionProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class NotificationPlannerTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val wednesday = monday.plusDays(2)

    private fun sp(id: Long, target: Double, achieved: Double, inDial: Boolean = true) = SectionProgress(
        id, null, "S$id", "x", 0, "u", target, achieved,
        if (target > 0) (achieved / target).coerceAtMost(1.0) else 0.0, 1.0, inDial, false, false,
    )

    @Test fun focusPicksClosestUnfinishedDialSection() {
        val list = listOf(sp(1, 4.0, 1.0), sp(2, 3.0, 2.0), sp(3, 2.0, 2.0), sp(4, 1.0, 0.9, inDial = false))
        assertEquals(2L, NotificationPlanner.focusSection(list)?.sectionId)
    }

    @Test fun focusNullWhenAllDone() {
        assertNull(NotificationPlanner.focusSection(listOf(sp(1, 2.0, 2.0), sp(2, 0.0, 0.0))))
    }

    @Test fun paceNudgeOnlyWednesdayWhenBehindOncePerWeek() {
        assertTrue(NotificationPlanner.shouldNudgePace(wednesday, monday, PaceStatus.BEHIND, true, null))
        assertFalse(NotificationPlanner.shouldNudgePace(wednesday.plusDays(1), monday, PaceStatus.BEHIND, true, null))
        assertFalse(NotificationPlanner.shouldNudgePace(wednesday, monday, PaceStatus.ON_PACE, true, null))
        assertFalse(NotificationPlanner.shouldNudgePace(wednesday, monday, PaceStatus.BEHIND, false, null))
        assertFalse(NotificationPlanner.shouldNudgePace(wednesday, monday, PaceStatus.BEHIND, true, wednesday))
        assertTrue(NotificationPlanner.shouldNudgePace(wednesday, monday, PaceStatus.BEHIND, true, wednesday.minusWeeks(1)))
    }

    @Test fun summaryDueOncePerWeek() {
        assertEquals(monday.minusWeeks(1), NotificationPlanner.summaryWeekDue(monday, null))
        assertEquals(monday.minusWeeks(1), NotificationPlanner.summaryWeekDue(monday, monday.minusWeeks(3)))
        assertNull(NotificationPlanner.summaryWeekDue(monday, monday.minusWeeks(1)))
    }

    @Test fun recapDueForLastMonthOnce() {
        val today = LocalDate.of(2026, 11, 1)
        assertEquals(YearMonth.of(2026, 10), NotificationPlanner.recapMonthDue(today, true, null, LocalDate.of(2026, 9, 1)))
        assertNull(NotificationPlanner.recapMonthDue(today, true, "2026-10", null))
        assertNull(NotificationPlanner.recapMonthDue(today, false, null, null))
        // Started using the app this month: nothing to recap yet.
        assertNull(NotificationPlanner.recapMonthDue(today, true, null, LocalDate.of(2026, 11, 1)))
    }
}
