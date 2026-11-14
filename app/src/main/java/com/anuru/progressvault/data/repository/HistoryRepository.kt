package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.AcademicsDao
import com.anuru.progressvault.data.db.dao.ActivityLogDao
import com.anuru.progressvault.data.db.dao.CourseDao
import com.anuru.progressvault.data.db.dao.HistoryDao
import com.anuru.progressvault.data.db.dao.SectionDao
import com.anuru.progressvault.data.db.dao.SyncDao
import com.anuru.progressvault.data.db.entity.CourseStatus
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.history.ActivityItem
import com.anuru.progressvault.domain.history.DifficultyCounts
import com.anuru.progressvault.domain.history.HistoryData
import com.anuru.progressvault.domain.history.Metric
import com.anuru.progressvault.domain.history.SectionInfo
import com.anuru.progressvault.domain.history.SemesterInfo
import com.anuru.progressvault.domain.history.SkillDate
import com.anuru.progressvault.domain.history.WeekResult
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.time.WeekCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Loads every historical source into one [HistoryData] for Then-vs-Now. */
@Singleton
class HistoryRepository @Inject constructor(
    private val sectionDao: SectionDao,
    private val logDao: ActivityLogDao,
    private val syncDao: SyncDao,
    private val courseDao: CourseDao,
    private val historyDao: HistoryDao,
    private val academicsDao: AcademicsDao,
    private val skillsRepository: SkillsRepository,
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
    private val weekCalculator: WeekCalculator,
) {
    private data class Sources(
        val sections: List<SectionInfo>,
        val items: List<ActivityItem>,
    )

    fun observe(): Flow<HistoryData> {
        val zone = weekCalculator.zone
        val sources = combine(
            sectionDao.observeAll(),
            logDao.observeAll(),
            syncDao.observeAllProblems(),
            syncDao.observeAllContributions(),
        ) { sections, logs, problems, days ->
            val info = sections.map { SectionInfo(it.id, it.name, it.builtInKey, it.unit, it.accentColor) }
            val keyById = sections.associate { it.id to BuiltInSectionKey.fromKey(it.builtInKey) }
            val idByKey = sections.mapNotNull { s -> BuiltInSectionKey.fromKey(s.builtInKey)?.let { it to s.id } }.toMap()
            val items = buildList {
                logs.forEach { log ->
                    val metric = when (keyById[log.sectionId]) {
                        BuiltInSectionKey.LEETCODE -> Metric.PROBLEMS
                        BuiltInSectionKey.GITHUB -> Metric.COMMITS
                        BuiltInSectionKey.LINKEDIN -> Metric.POSTS
                        BuiltInSectionKey.COURSES -> Metric.LESSONS
                        BuiltInSectionKey.ACADEMICS -> Metric.STUDY_HOURS
                        else -> null
                    }
                    add(ActivityItem(log.localDate, log.sectionId, metric, log.amount))
                }
                problems.forEach { add(ActivityItem(it.solvedAt.atZone(zone).toLocalDate(), idByKey[BuiltInSectionKey.LEETCODE], Metric.PROBLEMS, 1.0)) }
                days.forEach { add(ActivityItem(it.date, idByKey[BuiltInSectionKey.GITHUB], Metric.COMMITS, it.count.toDouble())) }
            }
            Sources(info, items)
        }
        val extras = combine(
            courseDao.observeAll(),
            syncDao.observeAllStats(),
            academicsDao.observeSemesters(),
            skillsRepository.observeReview(),
        ) { courses, stats, semesters, review ->
            Extras(
                completions = courses.filter { it.status == CourseStatus.COMPLETED }.map { it.finishDate },
                stats = stats.map { DifficultyCounts(it.fetchedAt.atZone(zone).toLocalDate(), it.easySolved, it.mediumSolved, it.hardSolved, it.totalSolved) },
                semesters = semesters.map { SemesterInfo(it.name, it.startDate, it.endDate, it.archivedAt?.atZone(zone)?.toLocalDate()) },
                skills = review.skills.map { SkillDate(it.key, it.name, it.firstSeen) },
            )
        }
        val current = weekCalculator.currentWeek()
        val weeks = combine(historyDao.observeSnapshots(), progressRepository.observeWeek(current)) { snaps, live ->
            snaps.filter { it.weekStart != current.start }.map { WeekResult(it.weekStart, it.overallCompletion) } +
                WeekResult(current.start, live.overall)
        }
        return combine(sources, extras, weeks, settingsRepository.settings) { src, ex, wk, settings ->
            HistoryData(
                items = src.items,
                sections = src.sections,
                courseCompletions = ex.completions,
                skills = ex.skills,
                leetCodeStats = ex.stats,
                weeks = wk.sortedBy { it.weekStart },
                semesters = ex.semesters,
                firstDay = listOfNotNull(settings.firstLaunchDate, src.items.minOfOrNull { it.date }).minOrNull(),
                today = weekCalculator.today(),
            )
        }
    }

    private data class Extras(
        val completions: List<java.time.LocalDate?>,
        val stats: List<DifficultyCounts>,
        val semesters: List<SemesterInfo>,
        val skills: List<SkillDate>,
    )
}
