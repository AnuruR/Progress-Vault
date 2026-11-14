package com.anuru.progressvault.domain.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Pure Then-vs-Now maths over [HistoryData]. Everything is derived from logs, synced rows and
 * WeeklySnapshots, so history survives target or section changes.
 */
object HistoryCalculator {

    val PROBLEM_STEPS = listOf(1, 10, 50, 100, 250, 500, 1000)
    val COMMIT_STEPS = listOf(10, 100, 500, 1000)
    val POST_STEPS = listOf(1, 10, 25, 50, 100)
    val LESSON_STEPS = listOf(10, 50, 100, 250)
    val HOUR_STEPS = listOf(10, 50, 100, 250, 500)
    val COURSE_STEPS = listOf(1, 5, 10)
    val SKILL_STEPS = listOf(5, 10, 25)
    const val PERFECT_STREAK = 4
    const val PERFECT = 0.999

    /** Cumulative totals at the end of [date] (inclusive). */
    fun totalsAt(data: HistoryData, date: LocalDate): Totals {
        val upTo = data.items.filter { !it.date.isAfter(date) }
        fun sum(m: Metric) = upTo.filter { it.metric == m }.sumOf { it.amount }
        val custom = upTo.filter { it.metric == null && it.sectionId != null }
            .groupBy { it.sectionId!! }.mapValues { (_, v) -> v.sumOf { it.amount } }
        return Totals(
            date = date,
            problems = sum(Metric.PROBLEMS).roundToInt(),
            commits = sum(Metric.COMMITS).roundToInt(),
            posts = sum(Metric.POSTS).roundToInt(),
            lessons = sum(Metric.LESSONS).roundToInt(),
            studyHours = sum(Metric.STUDY_HOURS),
            // Completions with an unknown date only count for "today".
            coursesCompleted = data.courseCompletions.count { d -> if (d == null) date >= data.today else !d.isAfter(date) },
            skillKeys = data.skills.filter { s -> s.firstSeen?.let { !it.isAfter(date) } ?: (date >= data.today) }.map { it.key }.toSet(),
            custom = custom,
            leetCode = data.leetCodeStats.filter { !it.date.isAfter(date) }.maxByOrNull { it.date },
        )
    }

    /** "Then" = the start of [then] (end of the previous day), so activity on that day counts as gained since. */
    fun compare(data: HistoryData, then: LocalDate, now: LocalDate = data.today): Comparison {
        val a = totalsAt(data, then.minusDays(1))
        val b = totalsAt(data, now)
        val names = data.skills.associate { it.key to it.name }
        return Comparison(a, b, (b.skillKeys - a.skillKeys).mapNotNull { names[it] }.sorted())
    }

    /** Calendar weeks from the first day of activity (or first launch) to this week, inclusive. */
    fun weeksTracked(data: HistoryData): Int {
        val first = data.firstDay ?: return data.weeks.size
        val a = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val b = data.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (java.time.temporal.ChronoUnit.WEEKS.between(a, b) + 1).toInt().coerceAtLeast(data.weeks.size)
    }

    /** Activity points per day, optionally for one section. */
    fun heatmap(data: HistoryData, from: LocalDate, to: LocalDate, sectionId: Long? = null): Map<LocalDate, Double> =
        data.items.asSequence()
            .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .filter { sectionId == null || it.sectionId == sectionId }
            .groupBy { it.date }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .filterValues { it > 0 }

    /** Output of one section per Monday-week, filling empty weeks with 0. */
    fun weeklySeries(data: HistoryData, sectionId: Long?, weeks: Int): List<SeriesPoint> {
        val lastMonday = data.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val starts = (weeks - 1 downTo 0).map { lastMonday.minusWeeks(it.toLong()) }
        val byWeek = data.items.filter { sectionId == null || it.sectionId == sectionId }
            .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        return starts.map { s -> SeriesPoint(s, byWeek[s].orEmpty().sumOf { it.amount }) }
    }

    fun monthlySeries(data: HistoryData, sectionId: Long?, months: Int): List<SeriesPoint> {
        val current = YearMonth.from(data.today)
        val byMonth = data.items.filter { sectionId == null || it.sectionId == sectionId }.groupBy { YearMonth.from(it.date) }
        return (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }
            .map { m -> SeriesPoint(m.atDay(1), byMonth[m].orEmpty().sumOf { it.amount }) }
    }

    /** All milestones earned so far, oldest first. */
    fun milestones(data: HistoryData): List<Milestone> = buildList {
        fun thresholds(metric: Metric, steps: List<Int>, kind: MilestoneKind) {
            var total = 0.0
            var next = 0
            data.items.filter { it.metric == metric }.sortedBy { it.date }.forEach { item ->
                total += item.amount
                while (next < steps.size && total + 1e-9 >= steps[next]) {
                    add(Milestone(kind, steps[next], item.date)); next++
                }
            }
        }
        thresholds(Metric.PROBLEMS, PROBLEM_STEPS, MilestoneKind.PROBLEMS)
        thresholds(Metric.COMMITS, COMMIT_STEPS, MilestoneKind.COMMITS)
        thresholds(Metric.POSTS, POST_STEPS, MilestoneKind.POSTS)
        thresholds(Metric.LESSONS, LESSON_STEPS, MilestoneKind.LESSONS)
        thresholds(Metric.STUDY_HOURS, HOUR_STEPS, MilestoneKind.STUDY_HOURS)

        data.courseCompletions.map { it ?: data.today }.sorted().forEachIndexed { i, d ->
            if ((i + 1) in COURSE_STEPS) add(Milestone(MilestoneKind.COURSES, i + 1, d))
        }
        data.skills.mapNotNull { it.firstSeen }.sorted().forEachIndexed { i, d ->
            if ((i + 1) in SKILL_STEPS) add(Milestone(MilestoneKind.SKILLS, i + 1, d))
        }
        // Weekly milestones only count finished weeks (a week is "earned" on its Sunday).
        val finished = data.weeks.filter { it.weekStart.plusDays(6).isBefore(data.today) }
        finished.firstOrNull { it.completion >= PERFECT }?.let { add(Milestone(MilestoneKind.PERFECT_WEEK, 1, it.weekStart.plusDays(6))) }
        var run = 0
        var prev: LocalDate? = null
        var streakAwarded = false
        finished.forEach { w ->
            run = if (w.completion >= PERFECT && prev?.plusWeeks(1) == w.weekStart) run + 1 else if (w.completion >= PERFECT) 1 else 0
            prev = w.weekStart
            if (!streakAwarded && run >= PERFECT_STREAK) {
                add(Milestone(MilestoneKind.PERFECT_STREAK, PERFECT_STREAK, w.weekStart.plusDays(6))); streakAwarded = true
            }
        }
        data.semesters.forEach { sem ->
            // Completed on its end date, or earlier if it was archived before ending.
            val done = listOfNotNull(sem.archivedOn, sem.end.takeIf { it.isBefore(data.today) }).minOrNull()
            if (done != null) add(Milestone(MilestoneKind.SEMESTER, 1, done, sem.name))
        }
    }.filter { !it.date.isAfter(data.today) }.sortedWith(compareBy({ it.date }, { it.kind.ordinal }, { it.threshold }))

    /** The next un-earned threshold for each counted kind, closest first. */
    fun nextGoals(data: HistoryData): List<NextGoal> {
        val t = totalsAt(data, data.today)
        return listOf(
            Triple(MilestoneKind.PROBLEMS, t.problems, PROBLEM_STEPS),
            Triple(MilestoneKind.COMMITS, t.commits, COMMIT_STEPS),
            Triple(MilestoneKind.POSTS, t.posts, POST_STEPS),
            Triple(MilestoneKind.LESSONS, t.lessons, LESSON_STEPS),
            Triple(MilestoneKind.STUDY_HOURS, t.studyHours.toInt(), HOUR_STEPS),
            Triple(MilestoneKind.COURSES, t.coursesCompleted, COURSE_STEPS),
            Triple(MilestoneKind.SKILLS, t.skills, SKILL_STEPS),
        ).mapNotNull { (kind, cur, steps) -> steps.firstOrNull { it > cur }?.let { NextGoal(kind, cur, it) } }
            .sortedBy { it.current.toDouble() / it.target }.reversed()
    }

    fun records(data: HistoryData): Records {
        val finished = data.weeks.filter { it.weekStart.plusDays(6).isBefore(data.today) || it.completion >= PERFECT }
        var best = 0; var run = 0; var prev: LocalDate? = null
        finished.forEach { w ->
            run = when {
                w.completion < PERFECT -> 0
                prev?.plusWeeks(1) == w.weekStart -> run + 1
                else -> 1
            }
            prev = w.weekStart
            best = maxOf(best, run)
        }
        val days = data.items.filter { it.amount > 0 }.map { it.date }.toSortedSet()
        var longest = 0; var cur = 0; var last: LocalDate? = null
        days.forEach { d -> cur = if (last?.plusDays(1) == d) cur + 1 else 1; last = d; longest = maxOf(longest, cur) }
        // Current streak may end yesterday (today not logged yet).
        var current = 0
        var probe = if (data.today in days) data.today else data.today.minusDays(1)
        while (probe in days) { current++; probe = probe.minusDays(1) }
        val months = data.items.groupBy { YearMonth.from(it.date) }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val top = months.maxByOrNull { it.value }
        return Records(
            bestWeek = data.weeks.maxByOrNull { it.completion }?.takeIf { it.completion > 0 },
            longestPerfectStreak = best,
            longestDayStreak = longest,
            currentDayStreak = current,
            mostActiveMonth = top?.key,
            mostActiveMonthPoints = top?.value ?: 0.0,
        )
    }

    fun monthlyRecap(data: HistoryData, month: YearMonth): MonthlyRecap {
        val inMonth = data.items.filter { YearMonth.from(it.date) == month }
        fun sum(m: Metric) = inMonth.filter { it.metric == m }.sumOf { it.amount }
        return MonthlyRecap(
            month = month,
            problems = sum(Metric.PROBLEMS).roundToInt(),
            commits = sum(Metric.COMMITS).roundToInt(),
            posts = sum(Metric.POSTS).roundToInt(),
            lessons = sum(Metric.LESSONS).roundToInt(),
            studyHours = sum(Metric.STUDY_HOURS),
            coursesFinished = data.courseCompletions.count { it != null && YearMonth.from(it) == month },
            activeDays = inMonth.filter { it.amount > 0 }.map { it.date }.distinct().size,
            bestWeek = data.weeks.filter { YearMonth.from(it.weekStart) == month }.maxByOrNull { it.completion },
            milestones = milestones(data).filter { YearMonth.from(it.date) == month },
        )
    }

    /** Quick presets for the comparison picker. Presets before the first day are clamped to it. */
    fun presetDate(data: HistoryData, preset: ComparePreset): LocalDate? {
        val d = when (preset) {
            ComparePreset.ONE_MONTH -> data.today.minusMonths(1)
            ComparePreset.THREE_MONTHS -> data.today.minusMonths(3)
            ComparePreset.SIX_MONTHS -> data.today.minusMonths(6)
            ComparePreset.SEMESTER_START -> data.semesters.filter { !it.start.isAfter(data.today) }.maxByOrNull { it.start }?.start ?: return null
            ComparePreset.FIRST_DAY -> data.firstDay ?: return null
        }
        return d
    }
}

enum class ComparePreset { ONE_MONTH, THREE_MONTHS, SIX_MONTHS, SEMESTER_START, FIRST_DAY }
