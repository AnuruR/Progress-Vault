package com.anuru.progressvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.anuru.progressvault.data.db.entity.AchievementEntity
import com.anuru.progressvault.data.db.entity.ManualSkillEntity
import com.anuru.progressvault.data.db.entity.WeeklySectionSnapshotEntity
import com.anuru.progressvault.data.db.entity.WeeklySnapshotEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HistoryDao {
    @Query("SELECT * FROM weekly_snapshots ORDER BY weekStart")
    fun observeSnapshots(): Flow<List<WeeklySnapshotEntity>>

    @Query("SELECT * FROM weekly_snapshots WHERE weekStart = :weekStart")
    fun observeSnapshot(weekStart: LocalDate): Flow<WeeklySnapshotEntity?>

    @Query("SELECT * FROM weekly_snapshots WHERE weekStart = :weekStart")
    suspend fun getSnapshot(weekStart: LocalDate): WeeklySnapshotEntity?

    @Query("SELECT weekStart FROM weekly_snapshots")
    suspend fun getSnapshotWeeks(): List<LocalDate>

    @Query("SELECT * FROM weekly_section_snapshots WHERE weekStart = :weekStart")
    fun observeSectionSnapshots(weekStart: LocalDate): Flow<List<WeeklySectionSnapshotEntity>>

    /** Snapshots are immutable: a second archive of the same week is ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSnapshot(snapshot: WeeklySnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSectionSnapshots(rows: List<WeeklySectionSnapshotEntity>)

    @Transaction
    suspend fun archiveWeek(snapshot: WeeklySnapshotEntity, rows: List<WeeklySectionSnapshotEntity>): Boolean {
        val inserted = insertSnapshot(snapshot) != -1L
        if (inserted) insertSectionSnapshots(rows)
        return inserted
    }

    @Query("SELECT * FROM achievements ORDER BY earnedAt")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(achievement: AchievementEntity): Long

    @Query("SELECT * FROM manual_skills ORDER BY name")
    fun observeManualSkills(): Flow<List<ManualSkillEntity>>

    @Insert suspend fun insertManualSkill(skill: ManualSkillEntity): Long

    @androidx.room.Update suspend fun updateManualSkill(skill: ManualSkillEntity)

    @Query("DELETE FROM manual_skills WHERE id = :id")
    suspend fun deleteManualSkill(id: Long)

    @Query("SELECT * FROM manual_skills WHERE id = :id")
    suspend fun getManualSkill(id: Long): ManualSkillEntity?
}
