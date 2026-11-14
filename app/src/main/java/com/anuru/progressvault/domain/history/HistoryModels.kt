package com.anuru.progressvault.domain.history

import java.time.LocalDate
import java.time.YearMonth

/** What a unit of activity counts as, for lifetime totals. Null = a custom section's own unit. */
enum class Metric { PROBLEMS, COMMITS, POSTS, LESSONS, STUDY_HOURS }

/** One dated piece of activity from any source (manual log, LeetCode solve, GitHub day). */
data class ActivityItem(val date: LocalDate, val sectionId: Long?, val metric: Metric?, val amount: Double)

data class SectionInfo(val id: Long, val name: String, val builtInKey: String?, val unit: String, val accentColor: Long)

data class DifficultyCounts(val date: LocalDate, val easy: Int, val medium: Int, val hard: Int, val total: Int)

data class WeekResult(val weekStart: LocalDate, val completion: Double)

/** [archivedOn] is set when the semester was closed via "Start new semester". */
data class SemesterInfo(val name: String, val start: LocalDate, val end: LocalDate, val archivedOn: LocalDate?)

data class SkillDate(val key: String, val name: String, val firstSeen: LocalDate?)

/** Everything Then-vs-Now needs, loaded once from Room. Purely in-memory so the maths is testable. */
data class HistoryData(
    val items: List<ActivityItem>,
    val sections: List<SectionInfo>,
    /** Finish dates of completed courses (null = completed, date unknown). */
    val courseCompletions: List<LocalDate?>,
    val skills: List<SkillDate>,
    val leetCodeStats: List<DifficultyCounts>,
    /** Archived weeks plus the live current week, oldest first. */
    val weeks: List<WeekResult>,
    val semesters: List<SemesterInfo>,
    val firstDay: LocalDate?,
    val today: LocalDate,
)

data class Totals(
    val date: LocalDate,
    val problems: Int,
    val commits: Int,
    val posts: Int,
    val lessons: Int,
    val studyHours: Double,
    val coursesCompleted: Int,
    val skillKeys: Set<String>,
    /** Custom-section totals by section id. */
    val custom: Map<Long, Double>,
    val leetCode: DifficultyCounts?,
) {
    val skills: Int get() = skillKeys.size
}

data class Comparison(
    val then: Totals,
    val now: Totals,
    /** Names of skills present now but not then. */
    val newSkills: List<String>,
) {
    val problems get() = now.problems - then.problems
    val commits get() = now.commits - then.commits
    val posts get() = now.posts - then.posts
    val lessons get() = now.lessons - then.lessons
    val studyHours get() = now.studyHours - then.studyHours
    val coursesCompleted get() = now.coursesCompleted - then.coursesCompleted
    val skills get() = now.skills - then.skills
}

enum class MilestoneKind { PROBLEMS, COMMITS, POSTS, LESSONS, STUDY_HOURS, COURSES, SKILLS, PERFECT_WEEK, PERFECT_STREAK, SEMESTER }

/** [threshold] is the count reached (or streak length); [label] carries e.g. a semester name. */
data class Milestone(val kind: MilestoneKind, val threshold: Int, val date: LocalDate, val label: String? = null) {
    val key: String get() = "${kind.name.lowercase()}_$threshold" + (label?.let { "_$it" } ?: "")
}

data class NextGoal(val kind: MilestoneKind, val current: Int, val target: Int)

data class Records(
    val bestWeek: WeekResult?,
    /** Consecutive weeks at 100%. */
    val longestPerfectStreak: Int,
    /** Consecutive days with any activity. */
    val longestDayStreak: Int,
    val currentDayStreak: Int,
    val mostActiveMonth: YearMonth?,
    val mostActiveMonthPoints: Double,
)

data class MonthlyRecap(
    val month: YearMonth,
    val problems: Int,
    val commits: Int,
    val posts: Int,
    val lessons: Int,
    val studyHours: Double,
    val coursesFinished: Int,
    val activeDays: Int,
    val bestWeek: WeekResult?,
    val milestones: List<Milestone>,
) {
    val isEmpty: Boolean get() = problems + commits + posts + lessons == 0 && studyHours == 0.0 && coursesFinished == 0
}

/** Per-week (or per-month) output of one section, for trend charts. */
data class SeriesPoint(val start: LocalDate, val value: Double)
