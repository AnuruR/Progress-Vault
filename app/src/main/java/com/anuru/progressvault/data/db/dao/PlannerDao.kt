package com.anuru.progressvault.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.anuru.progressvault.data.db.entity.PlannerTaskEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PlannerDao {
    @Query("SELECT * FROM planner_tasks WHERE weekStart = :weekStart ORDER BY day, sortOrder, id")
    fun observeWeek(weekStart: LocalDate): Flow<List<PlannerTaskEntity>>

    @Query("SELECT * FROM planner_tasks WHERE weekStart = :weekStart AND status = 'TODO'")
    suspend fun getTodo(weekStart: LocalDate): List<PlannerTaskEntity>

    @Query("SELECT COUNT(*) FROM planner_tasks WHERE weekStart = :weekStart AND status = 'TODO'")
    fun observeTodoCount(weekStart: LocalDate): Flow<Int>

    @Query("SELECT COUNT(*) FROM planner_tasks WHERE weekStart = :weekStart AND status = 'TODO'")
    suspend fun todoCount(weekStart: LocalDate): Int

    @Query("SELECT * FROM planner_tasks WHERE id = :id")
    suspend fun getById(id: Long): PlannerTaskEntity?

    /** Moves every unfinished task of [from] into [to], unscheduled, remembering where it came from. */
    @Query("UPDATE planner_tasks SET weekStart = :to, day = NULL, rolledOverFrom = :from WHERE weekStart = :from AND status = 'TODO'")
    suspend fun rollOver(from: LocalDate, to: LocalDate): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM planner_tasks WHERE weekStart = :weekStart")
    suspend fun maxSortOrder(weekStart: LocalDate): Int

    @Query("SELECT COUNT(*) FROM planner_tasks WHERE deadlineId = :deadlineId")
    suspend fun countForDeadline(deadlineId: Long): Int

    @Query("UPDATE planner_tasks SET status = :status, completedAt = :completedAt WHERE deadlineId = :deadlineId")
    suspend fun setStatusForDeadline(deadlineId: Long, status: com.anuru.progressvault.data.db.entity.TaskStatus, completedAt: java.time.Instant?)

    @Query("DELETE FROM planner_tasks WHERE deadlineId = :deadlineId AND status = 'TODO'")
    suspend fun deleteTodoForDeadline(deadlineId: Long)

    @Query("UPDATE planner_tasks SET weekStart = :to, day = NULL, rolledOverFrom = :from WHERE weekStart = :from AND status = 'TODO'")
    suspend fun moveTodo(from: LocalDate, to: LocalDate): Int

    @Insert suspend fun insert(task: PlannerTaskEntity): Long
    @Update suspend fun update(task: PlannerTaskEntity)
    @Update suspend fun updateAll(tasks: List<PlannerTaskEntity>)
    @Delete suspend fun delete(task: PlannerTaskEntity)
}
