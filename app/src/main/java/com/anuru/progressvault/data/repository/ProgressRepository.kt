package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.AcademicsDao
import com.anuru.progressvault.data.db.dao.ActivityLogDao
import com.anuru.progressvault.data.db.dao.HistoryDao
import com.anuru.progressvault.data.db.dao.SectionDao
import com.anuru.progressvault.data.db.dao.SyncDao
import com.anuru.progressvault.data.db.entity.ActivityLogEntity
import com.anuru.progressvault.data.db.entity.GitHubContributionDayEntity
import com.anuru.progressvault.data.db.entity.LeetCodeProblemEntity
import com.anuru.progressvault.data.db.entity.ModuleEntity
import com.anuru.progressvault.data.db.entity.SectionEntity
import com.anuru.progressvault.data.db.entity.SectionKind
import com.anuru.progressvault.data.db.entity.WeeklySectionSnapshotEntity
import com.anuru.progressvault.data.db.entity.WeeklyTargetOverrideEntity
import com.anuru.progressvault.data.db.toDomain
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.progress.ModuleProgress
import com.anuru.progressvault.domain.progress.ProgressCalculator
import com.anuru.progressvault.domain.progress.SectionProgress
import com.anuru.progressvault.domain.progress.WeekProgress
import com.anuru.progressvault.domain.time.WeekCalculator
import com.anuru.progressvault.domain.time.WeekRange
import com.anuru.progressvault.ui.components.SectionIconKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Raw inputs for one live week, gathered from the DB. */
data class WeekInputs(
    val sections: List<SectionEntity>,
    val overrides: List<WeeklyTargetOverrideEntity>,
    val logs: List<ActivityLogEntity>,
    val problems: List<LeetCodeProblemEntity>,
    val contributions: List<GitHubContributionDayEntity>,
    val modules: List<ModuleEntity>,
)

@Singleton
class ProgressRepository @Inject constructor(
    private val sectionDao: SectionDao,
    private val logDao: ActivityLogDao,
    private val syncDao: SyncDao,
    private val historyDao: HistoryDao,
    private val academicsDao: AcademicsDao,
    private val weekCalculator: WeekCalculator,
) {
    /** Archived weeks come from their immutable snapshot; others are computed from logs. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWeek(week: WeekRange): Flow<WeekProgress> =
        historyDao.observeSnapshot(week.start).flatMapLatest { snapshot ->
            if (snapshot != null) {
                combine(historyDao.observeSectionSnapshots(week.start), sectionDao.observeAll()) { rows, sections ->
                    archived(week, snapshot.overallCompletion, rows, sections)
                }
            } else {
                observeInputs(week).map { computeLive(week, it) }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeInputs(week: WeekRange): Flow<WeekInputs> {
        val zone = weekCalculator.zone
        val modulesFlow = academicsDao.observeActiveSemester().flatMapLatest { s ->
            if (s == null) flowOf(emptyList()) else academicsDao.observeModules(s.id)
        }
        val base = combine(
            sectionDao.observeAll(),
            sectionDao.observeOverridesForWeek(week.start),
            logDao.observeBetween(week.start, week.end),
        ) { sections, overrides, logs -> Triple(sections, overrides, logs) }
        return combine(
            base,
            syncDao.observeProblemsBetween(week.startInstant(zone), week.endExclusiveInstant(zone)),
            syncDao.observeContributionsBetween(week.start, week.end),
            modulesFlow,
        ) { (sections, overrides, logs), problems, contributions, modules ->
            WeekInputs(sections, overrides, logs, problems, contributions, modules)
        }
    }

    companion object {
        /** Pure computation of a live week; shared with the weekly rollover job. */
        fun computeLive(week: WeekRange, inputs: WeekInputs): WeekProgress {
            val overrides = inputs.overrides.associate { it.sectionId to it.target }
            val logsBySection = inputs.logs.groupBy { it.sectionId }
            val modules = inputs.modules.map { m ->
                ModuleProgress(
                    moduleId = m.id,
                    code = m.code,
                    name = m.name,
                    accentColor = m.accentColor,
                    targetHours = m.weeklyTargetHours,
                    hours = inputs.logs.filter { it.moduleId == m.id }.sumOf { it.amount },
                )
            }
            val sections = inputs.sections
                .filter { !it.isHidden && it.kind != SectionKind.DERIVED }
                .map { entity ->
                    val section = entity.toDomain()
                    val manual = logsBySection[entity.id].orEmpty().sumOf { it.amount }
                    val (target, achieved, fraction) = when {
                        section.isAcademics -> {
                            val t = modules.sumOf { it.targetHours }
                            Triple(t, modules.sumOf { it.hours }, ProgressCalculator.academicsFraction(modules))
                        }
                        else -> {
                            val t = overrides[entity.id] ?: entity.defaultWeeklyTarget
                            val synced = when (section.builtInKey) {
                                BuiltInSectionKey.LEETCODE -> inputs.problems.size.toDouble()
                                BuiltInSectionKey.GITHUB -> inputs.contributions.sumOf { it.count }.toDouble()
                                else -> 0.0
                            }
                            val a = manual + synced
                            Triple(t, a, ProgressCalculator.fraction(a, t))
                        }
                    }
                    SectionProgress(
                        sectionId = entity.id,
                        builtInKey = section.builtInKey,
                        name = entity.name,
                        iconKey = entity.iconKey,
                        accentColor = entity.accentColor,
                        unit = entity.unit,
                        target = target,
                        achieved = achieved,
                        fraction = fraction,
                        weight = entity.weight,
                        countsInDial = section.countsInDial(target),
                        isAutoSynced = entity.isAutoSynced,
                        isAcademics = section.isAcademics,
                    )
                }
            return WeekProgress(week, sections, modules, ProgressCalculator.overall(sections), isArchived = false)
        }

        fun archived(
            week: WeekRange,
            overall: Double,
            rows: List<WeeklySectionSnapshotEntity>,
            sections: List<SectionEntity>,
        ): WeekProgress {
            val byId = sections.associateBy { it.id }
            val progress = rows.map { row ->
                val current = byId[row.sectionId]
                SectionProgress(
                    sectionId = row.sectionId,
                    builtInKey = BuiltInSectionKey.fromKey(row.builtInKey),
                    name = row.sectionName,
                    iconKey = current?.iconKey ?: SectionIconKey.STAR,
                    accentColor = row.accentColor,
                    unit = row.unit,
                    target = row.target,
                    achieved = row.achieved,
                    fraction = ProgressCalculator.fraction(row.achieved, row.target),
                    weight = row.weight,
                    countsInDial = row.includedInDial,
                    isAutoSynced = current?.isAutoSynced ?: false,
                    isAcademics = current?.kind == SectionKind.ACADEMICS,
                )
            }.sortedBy { byId[it.sectionId]?.sortOrder ?: Int.MAX_VALUE }
            return WeekProgress(week, progress, emptyList(), overall, isArchived = true)
        }
    }
}
