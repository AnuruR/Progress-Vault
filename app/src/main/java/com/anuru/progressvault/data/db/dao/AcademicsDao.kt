package com.anuru.progressvault.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.anuru.progressvault.data.db.entity.ModuleDeadlineEntity
import com.anuru.progressvault.data.db.entity.ModuleEntity
import com.anuru.progressvault.data.db.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface AcademicsDao {
    @Query("SELECT * FROM semesters WHERE isActive = 1 LIMIT 1")
    fun observeActiveSemester(): Flow<SemesterEntity?>

    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    fun observeSemesters(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM modules WHERE semesterId = :semesterId ORDER BY sortOrder, id")
    fun observeModules(semesterId: Long): Flow<List<ModuleEntity>>

    @Query("SELECT * FROM module_deadlines WHERE dueAt >= :from AND isDone = 0 ORDER BY dueAt")
    fun observeUpcomingDeadlines(from: Instant): Flow<List<ModuleDeadlineEntity>>

    @Query("SELECT * FROM semesters WHERE id = :id")
    fun observeSemester(id: Long): Flow<SemesterEntity?>

    @Query("SELECT * FROM module_deadlines WHERE moduleId IN (SELECT id FROM modules WHERE semesterId = :semesterId) ORDER BY isDone, dueAt")
    fun observeDeadlinesForSemester(semesterId: Long): Flow<List<ModuleDeadlineEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM modules WHERE semesterId = :semesterId")
    suspend fun maxModuleSortOrder(semesterId: Long): Int

    @Query("SELECT * FROM semesters WHERE id = :id")
    suspend fun getSemester(id: Long): SemesterEntity?

    @Query("SELECT * FROM module_deadlines WHERE id = :id")
    suspend fun getDeadline(id: Long): ModuleDeadlineEntity?

    @Query("SELECT * FROM module_deadlines WHERE isDone = 0 AND dueAt >= :from")
    suspend fun getOpenDeadlinesFrom(from: Instant): List<ModuleDeadlineEntity>

    @Delete suspend fun deleteModule(module: ModuleEntity)

    @Insert suspend fun insertSemester(semester: SemesterEntity): Long
    @Update suspend fun updateSemester(semester: SemesterEntity)
    @Insert suspend fun insertModule(module: ModuleEntity): Long
    @Insert suspend fun insertModules(modules: List<ModuleEntity>): List<Long>
    @Update suspend fun updateModule(module: ModuleEntity)
    @Insert suspend fun insertDeadline(deadline: ModuleDeadlineEntity): Long
    @Update suspend fun updateDeadline(deadline: ModuleDeadlineEntity)
    @Delete suspend fun deleteDeadline(deadline: ModuleDeadlineEntity)

    @Query("UPDATE semesters SET isActive = 0, archivedAt = :at WHERE isActive = 1")
    suspend fun archiveActiveSemesters(at: Instant)

    /** Archives any active semester and makes [semester] the active one, with its modules. */
    @Transaction
    suspend fun startSemester(semester: SemesterEntity, modules: List<ModuleEntity>, now: Instant): Long {
        archiveActiveSemesters(now)
        val id = insertSemester(semester.copy(isActive = true))
        if (modules.isNotEmpty()) {
            insertModules(modules.mapIndexed { i, m -> m.copy(semesterId = id, sortOrder = i) })
        }
        return id
    }
}
