package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.AcademicsDao
import com.anuru.progressvault.data.db.dao.ActivityLogDao
import com.anuru.progressvault.data.db.dao.PlannerDao
import com.anuru.progressvault.data.db.dao.SectionDao
import com.anuru.progressvault.data.db.entity.PlannerTaskEntity
import com.anuru.progressvault.data.db.entity.TaskPriority
import com.anuru.progressvault.data.db.entity.TaskStatus
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.time.WeekRange
import com.anuru.progressvault.data.db.entity.DeadlineType
import com.anuru.progressvault.data.db.entity.ModuleDeadlineEntity
import com.anuru.progressvault.data.db.entity.ModuleEntity
import com.anuru.progressvault.data.db.entity.SemesterEntity
import com.anuru.progressvault.data.db.toDomain
import com.anuru.progressvault.domain.model.Module
import com.anuru.progressvault.domain.model.ModuleDraft
import com.anuru.progressvault.domain.model.Semester
import com.anuru.progressvault.domain.time.WeekCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AcademicsRepository @Inject constructor(
    private val academicsDao: AcademicsDao,
    private val logDao: ActivityLogDao,
    private val plannerDao: PlannerDao,
    private val sectionDao: SectionDao,
    private val weekCalculator: WeekCalculator,
) {
    fun observeActiveSemester(): Flow<Semester?> = academicsDao.observeActiveSemester().map { it?.toDomain() }

    fun observeSemesters(): Flow<List<Semester>> = academicsDao.observeSemesters().map { l -> l.map { it.toDomain() } }

    fun observeSemester(id: Long): Flow<Semester?> = academicsDao.observeSemester(id).map { it?.toDomain() }

    fun observeModules(semesterId: Long): Flow<List<Module>> =
        academicsDao.observeModules(semesterId).map { l -> l.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveModules(): Flow<List<Module>> = academicsDao.observeActiveSemester().flatMapLatest { semester ->
        if (semester == null) flowOf(emptyList()) else observeModules(semester.id)
    }

    fun observeDeadlines(semesterId: Long): Flow<List<ModuleDeadlineEntity>> =
        academicsDao.observeDeadlinesForSemester(semesterId)

    fun observeUpcomingDeadlines(): Flow<List<ModuleDeadlineEntity>> =
        academicsDao.observeUpcomingDeadlines(weekCalculator.now())

    /** Lifetime hours logged per module id. */
    fun observeModuleHours(): Flow<Map<Long, Double>> =
        logDao.observeModuleTotals().map { rows -> rows.associate { it.courseId to it.total } }

    /** Archives the current active semester (if any) and starts a new one with [modules]. */
    suspend fun startSemester(name: String, startDate: LocalDate, endDate: LocalDate, modules: List<ModuleDraft>): Long {
        require(!endDate.isBefore(startDate)) { "Semester end must not be before its start" }
        val now = weekCalculator.now()
        return academicsDao.startSemester(
            semester = SemesterEntity(name = name.trim(), startDate = startDate, endDate = endDate, isActive = true, createdAt = now),
            modules = modules.map { it.toEntity(semesterId = 0) },
            now = now,
        )
    }

    suspend fun updateSemester(semester: Semester, name: String, start: LocalDate, end: LocalDate) {
        val existing = academicsDao.getSemester(semester.id) ?: return
        academicsDao.updateSemester(existing.copy(name = name.trim(), startDate = start, endDate = end))
    }

    suspend fun addModule(semesterId: Long, draft: ModuleDraft): Long =
        academicsDao.insertModule(draft.toEntity(semesterId).copy(sortOrder = academicsDao.maxModuleSortOrder(semesterId) + 1))

    suspend fun updateModule(module: Module, draft: ModuleDraft) = academicsDao.updateModule(
        draft.toEntity(module.semesterId).copy(id = module.id),
    )

    /** Modules with logged hours are kept for history; returns false if deletion was refused. */
    suspend fun deleteModuleIfUnused(module: Module): Boolean {
        if (logDao.countForModule(module.id) > 0) return false
        academicsDao.deleteModule(draftEntity(module))
        return true
    }

    /** Adds a deadline and automatically puts a high-priority task for it in the planner on its due day. */
    suspend fun addDeadline(moduleId: Long, title: String, type: DeadlineType, dueAt: Instant, notes: String?): Long {
        val deadline = ModuleDeadlineEntity(moduleId = moduleId, title = title.trim(), type = type, dueAt = dueAt, notes = notes?.trim()?.ifEmpty { null })
        val id = academicsDao.insertDeadline(deadline)
        createPlannerTask(deadline.copy(id = id))
        return id
    }

    /** One-time backfill: open deadlines from this week on that have no planner task yet get one. */
    suspend fun backfillDeadlineTasks() {
        val from = weekCalculator.currentWeek().startInstant(weekCalculator.zone)
        academicsDao.getOpenDeadlinesFrom(from).forEach { if (plannerDao.countForDeadline(it.id) == 0) createPlannerTask(it) }
    }

    private suspend fun createPlannerTask(d: ModuleDeadlineEntity) {
        val due = d.dueAt.atZone(weekCalculator.zone).toLocalDate()
        val academics = sectionDao.getByBuiltInKey(BuiltInSectionKey.ACADEMICS.key)
        plannerDao.insert(
            PlannerTaskEntity(
                title = d.title,
                sectionId = academics?.id,
                moduleId = d.moduleId,
                deadlineId = d.id,
                weekStart = WeekRange.containing(due).start,
                day = due.dayOfWeek,
                priority = TaskPriority.HIGH,
                notes = d.notes,
                createdAt = weekCalculator.now(),
            ),
        )
    }

    /** Ticking a deadline also ticks its planner task(s). */
    suspend fun setDeadlineDone(deadline: ModuleDeadlineEntity, done: Boolean) {
        academicsDao.updateDeadline(deadline.copy(isDone = done))
        plannerDao.setStatusForDeadline(deadline.id, if (done) TaskStatus.DONE else TaskStatus.TODO, if (done) weekCalculator.now() else null)
    }

    /** Deleting a deadline removes its unfinished planner task (finished ones stay as history). */
    suspend fun deleteDeadline(deadline: ModuleDeadlineEntity) {
        plannerDao.deleteTodoForDeadline(deadline.id)
        academicsDao.deleteDeadline(deadline)
    }

    private fun ModuleDraft.toEntity(semesterId: Long) = ModuleEntity(
        semesterId = semesterId,
        code = code.trim(),
        name = name.trim().ifEmpty { code.trim() },
        accentColor = accentColor,
        weeklyTargetHours = weeklyTargetHours,
        credits = credits,
    )

    private fun draftEntity(m: Module) = ModuleEntity(
        id = m.id, semesterId = m.semesterId, code = m.code, name = m.name,
        accentColor = m.accentColor, weeklyTargetHours = m.weeklyTargetHours, credits = m.credits,
    )
}
