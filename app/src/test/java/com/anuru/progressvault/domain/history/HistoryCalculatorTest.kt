package com.anuru.progressvault.domain.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HistoryCalculatorTest {
    private val today = LocalDate.of(2026, 11, 14) // Saturday
    private fun d(s: String) = LocalDate.parse(s)
    private fun data(
        items: List<ActivityItem> = emptyList(),
        courses: List<LocalDate?> = emptyList(),
        skills: List<SkillDate> = emptyList(),
        stats: List<DifficultyCounts> = emptyList(),
        weeks: List<WeekResult> = emptyList(),
        semesters: List<SemesterInfo> = emptyList(),
        firstDay: LocalDate? = d("2026-01-01"),
    ) = HistoryData(items, emptyList(), courses, skills, stats, weeks, semesters, firstDay, today)

    private fun p(date: String, n: Double = 1.0) = ActivityItem(d(date), 1, Metric.PROBLEMS, n)

    @Test fun totalsAtIsCumulativeAndInclusive() {
        val h = data(items = listOf(p("2026-10-01"), p("2026-10-05", 2.0), p("2026-11-01")))
        assertEquals(3, HistoryCalculator.totalsAt(h, d("2026-10-05")).problems)
        assertEquals(1, HistoryCalculator.totalsAt(h, d("2026-10-04")).problems)
        assertEquals(4, HistoryCalculator.totalsAt(h, today).problems)
    }

    @Test fun compareDiffIncludesThenDayAndNewSkills() {
        val h = data(
            items = listOf(p("2026-09-01", 5.0), p("2026-10-14", 3.0), ActivityItem(d("2026-11-01"), 2, Metric.POSTS, 2.0)),
            courses = listOf(d("2026-11-02")),
            skills = listOf(SkillDate("array", "Array", d("2026-08-01")), SkillDate("graph", "Graph", d("2026-10-20"))),
            stats = listOf(DifficultyCounts(d("2026-09-01"), 5, 0, 0, 5), DifficultyCounts(d("2026-11-10"), 6, 2, 0, 8)),
        )
        val c = HistoryCalculator.compare(h, d("2026-10-14"))
        assertEquals(5, c.then.problems) // activity ON the then-date counts as gained
        assertEquals(3, c.problems)
        assertEquals(2, c.posts)
        assertEquals(1, c.coursesCompleted)
        assertEquals(listOf("Graph"), c.newSkills)
        assertEquals(5, c.then.leetCode!!.total)
        assertEquals(8, c.now.leetCode!!.total)
    }

    @Test fun customSectionsTotalledSeparately() {
        val h = data(items = listOf(ActivityItem(d("2026-11-01"), 9, null, 3.0)))
        assertEquals(3.0, HistoryCalculator.totalsAt(h, today).custom[9L]!!, 0.0)
        assertEquals(0, HistoryCalculator.totalsAt(h, today).problems)
    }

    @Test fun heatmapFiltersBySectionAndRange() {
        val h = data(items = listOf(p("2026-11-01"), p("2026-11-01"), ActivityItem(d("2026-11-01"), 2, Metric.POSTS, 1.0), p("2025-01-01")))
        val all = HistoryCalculator.heatmap(h, d("2026-01-01"), d("2026-12-31"))
        assertEquals(3.0, all[d("2026-11-01")]!!, 0.0)
        assertEquals(1, all.size)
        assertEquals(2.0, HistoryCalculator.heatmap(h, d("2026-01-01"), d("2026-12-31"), sectionId = 1)[d("2026-11-01")]!!, 0.0)
    }

    @Test fun milestonesCrossThresholdsOnTheRightDay() {
        val h = data(items = listOf(p("2026-10-01"), p("2026-10-02", 8.0), p("2026-10-03")))
        val m = HistoryCalculator.milestones(h).filter { it.kind == MilestoneKind.PROBLEMS }
        assertEquals(listOf(1 to d("2026-10-01"), 10 to d("2026-10-03")), m.map { it.threshold to it.date })
    }

    @Test fun perfectStreakNeedsConsecutiveFinishedWeeks() {
        val weeks = listOf("2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26").map { WeekResult(d(it), 1.0) } +
            WeekResult(d("2026-11-09"), 1.0) // current week: not finished
        val h = data(weeks = weeks)
        val m = HistoryCalculator.milestones(h)
        assertTrue(m.any { it.kind == MilestoneKind.PERFECT_STREAK && it.date == d("2026-11-01") })
        assertEquals(d("2026-10-11"), m.single { it.kind == MilestoneKind.PERFECT_WEEK }.date)
    }

    @Test fun brokenStreakDoesNotCount() {
        val weeks = listOf("2026-10-05", "2026-10-12", "2026-10-26", "2026-11-02").map { WeekResult(d(it), 1.0) }
        val h = data(weeks = weeks)
        assertTrue(HistoryCalculator.milestones(h).none { it.kind == MilestoneKind.PERFECT_STREAK })
        assertEquals(2, HistoryCalculator.records(h).longestPerfectStreak)
    }

    @Test fun recordsDayStreaksAndBestMonth() {
        val h = data(
            items = listOf(p("2026-10-01"), p("2026-10-02"), p("2026-10-03"), p("2026-11-12"), p("2026-11-13", 9.0)),
            weeks = listOf(WeekResult(d("2026-10-26"), 0.4), WeekResult(d("2026-11-02"), 0.92)),
        )
        val r = HistoryCalculator.records(h)
        assertEquals(3, r.longestDayStreak)
        assertEquals(2, r.currentDayStreak) // ends yesterday
        assertEquals(YearMonth.of(2026, 11), r.mostActiveMonth)
        assertEquals(0.92, r.bestWeek!!.completion, 0.0)
    }

    @Test fun monthlyRecap() {
        val h = data(
            items = listOf(p("2026-10-01", 9.0), ActivityItem(d("2026-10-05"), 2, Metric.POSTS, 4.0), p("2026-11-01")),
            courses = listOf(d("2026-10-20"), null),
            weeks = listOf(WeekResult(d("2026-10-05"), 0.5), WeekResult(d("2026-10-12"), 0.92), WeekResult(d("2026-11-02"), 1.0)),
        )
        val r = HistoryCalculator.monthlyRecap(h, YearMonth.of(2026, 10))
        assertEquals(9, r.problems)
        assertEquals(4, r.posts)
        assertEquals(1, r.coursesFinished)
        assertEquals(2, r.activeDays)
        assertEquals(0.92, r.bestWeek!!.completion, 0.0)
    }

    @Test fun presets() {
        val h = data(semesters = listOf(SemesterInfo("S1", d("2026-02-01"), d("2026-06-01"), d("2026-06-01")), SemesterInfo("S2", d("2026-09-01"), d("2027-01-15"), null)))
        assertEquals(d("2026-09-01"), HistoryCalculator.presetDate(h, ComparePreset.SEMESTER_START))
        assertEquals(d("2026-10-14"), HistoryCalculator.presetDate(h, ComparePreset.ONE_MONTH))
        assertEquals(d("2026-01-01"), HistoryCalculator.presetDate(h, ComparePreset.FIRST_DAY))
        assertNull(HistoryCalculator.presetDate(data(firstDay = null), ComparePreset.FIRST_DAY))
    }

    @Test fun semesterMilestoneUsesArchiveDateAndNeverFuture() {
        val h = data(semesters = listOf(
            SemesterInfo("Early", d("2026-09-01"), d("2027-03-06"), d("2026-11-10")),
            SemesterInfo("Running", d("2026-09-01"), d("2027-03-06"), null),
        ))
        val m = HistoryCalculator.milestones(h).filter { it.kind == MilestoneKind.SEMESTER }
        assertEquals(listOf("Early" to d("2026-11-10")), m.map { it.label to it.date })
    }

    @Test fun weeksTrackedCountsFromFirstDay() {
        assertEquals(5, HistoryCalculator.weeksTracked(data(firstDay = d("2026-10-13"))))
    }

    @Test fun weeklySeriesFillsGaps() {
        val h = data(items = listOf(p("2026-11-10", 2.0), p("2026-10-27")))
        val s = HistoryCalculator.weeklySeries(h, null, 4)
        assertEquals(listOf(d("2026-10-19"), d("2026-10-26"), d("2026-11-02"), d("2026-11-09")), s.map { it.start })
        assertEquals(listOf(0.0, 1.0, 0.0, 2.0), s.map { it.value })
    }
}
