package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.PlannerDao
import com.anuru.progressvault.data.db.entity.PlannerTaskEntity
import com.anuru.progressvault.data.db.entity.TaskStatus
import com.anuru.progressvault.domain.planner.TaskMove
import com.anuru.progressvault.domain.time.WeekCalculator
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlannerRepository @Inject constructor(
    private val dao: PlannerDao,
    private val academicsDao: com.anuru.progressvault.data.db.dao.AcademicsDao,
    private val weekCalculator: WeekCalculator,
) {
    fun observeWeek(weekStart: LocalDate): Flow<List<PlannerTaskEntity>> = dao.observeWeek(weekStart)

    fun observeTodoCount(weekStart: LocalDate): Flow<Int> = dao.observeTodoCount(weekStart)

    suspend fun save(task: PlannerTaskEntity): Long {
        val clean = task.copy(title = task.title.trim(), notes = task.notes?.trim()?.ifEmpty { null })
        return if (task.id == 0L) {
            dao.insert(clean.copy(createdAt = weekCalculator.now(), sortOrder = dao.maxSortOrder(task.weekStart) + 1))
        } else {
            dao.update(clean)
            task.id
        }
    }

    suspend fun delete(task: PlannerTaskEntity) = dao.delete(task)

    /** Updates status; a task linked to a deadline keeps that deadline's done-state in sync. */
    suspend fun setStatus(task: PlannerTaskEntity, status: TaskStatus) {
        dao.update(task.copy(status = status, completedAt = if (status == TaskStatus.DONE) weekCalculator.now() else null))
        task.deadlineId?.let { id -> academicsDao.getDeadline(id)?.let { academicsDao.updateDeadline(it.copy(isDone = status == TaskStatus.DONE)) } }
    }

    /** Moves every unfinished task of week [from] into week [to], unscheduled. */
    suspend fun moveUnfinished(from: LocalDate, to: LocalDate): Int = if (from == to) 0 else dao.moveTodo(from, to)

    /** Moves a task to [day] within its week. */
    suspend fun moveToDay(taskId: Long, day: DayOfWeek?) {
        val t = dao.getById(taskId) ?: return
        if (t.day != day) dao.update(t.copy(day = day))
    }

    /** Moves a task into the following week, unscheduled. */
    suspend fun moveToNextWeek(taskId: Long) {
        val t = dao.getById(taskId) ?: return
        dao.update(t.copy(weekStart = t.weekStart.plusWeeks(1), day = null))
    }

    suspend fun applyMoves(moves: List<TaskMove>) {
        moves.forEach { moveToDay(it.taskId, it.to) }
    }

    suspend fun rollOver(from: LocalDate): Int = dao.rollOver(from, from.plusWeeks(1))

    suspend fun todoCount(weekStart: LocalDate): Int = dao.todoCount(weekStart)
}
