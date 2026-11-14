package com.anuru.progressvault.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.anuru.progressvault.data.db.entity.ActivityLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insert(log: ActivityLogEntity): Long

    @Delete
    suspend fun delete(log: ActivityLogEntity)

    @Query("DELETE FROM activity_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** [from] inclusive, [to] inclusive (local dates). */
    @Query("SELECT * FROM activity_logs WHERE localDate BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE sectionId = :sectionId ORDER BY timestamp DESC")
    fun observeForSection(sectionId: Long): Flow<List<ActivityLogEntity>>

    @Query("SELECT courseId, SUM(amount) AS total FROM activity_logs WHERE courseId IS NOT NULL GROUP BY courseId")
    fun observeCourseTotals(): Flow<List<IdTotal>>

    @Query("SELECT courseId, MAX(localDate) AS date FROM activity_logs WHERE courseId IS NOT NULL GROUP BY courseId")
    fun observeCourseLastDates(): Flow<List<IdDate>>

    @Query("SELECT moduleId AS courseId, SUM(amount) AS total FROM activity_logs WHERE moduleId IS NOT NULL GROUP BY moduleId")
    fun observeModuleTotals(): Flow<List<IdTotal>>

    @Query("SELECT COUNT(*) FROM activity_logs WHERE moduleId = :moduleId")
    suspend fun countForModule(moduleId: Long): Int

    @Query("SELECT * FROM activity_logs ORDER BY timestamp")
    suspend fun getAll(): List<ActivityLogEntity>

    @Query("SELECT * FROM activity_logs ORDER BY localDate")
    fun observeAll(): Flow<List<ActivityLogEntity>>
}

/** Aggregate row: an id (course or module) with its summed amount. */
data class IdTotal(val courseId: Long, val total: Double)

data class IdDate(val courseId: Long, val date: java.time.LocalDate)
