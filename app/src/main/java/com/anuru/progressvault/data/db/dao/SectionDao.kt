package com.anuru.progressvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.anuru.progressvault.data.db.entity.SectionEntity
import com.anuru.progressvault.data.db.entity.WeeklyTargetOverrideEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE isHidden = 0 ORDER BY sortOrder, id")
    fun observeVisible(): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE id = :id")
    fun observe(id: Long): Flow<SectionEntity?>

    @Query("SELECT * FROM weekly_target_overrides WHERE sectionId = :sectionId AND weekStart = :weekStart")
    fun observeOverride(sectionId: Long, weekStart: LocalDate): Flow<WeeklyTargetOverrideEntity?>

    @Query("SELECT * FROM sections ORDER BY sortOrder, id")
    suspend fun getAll(): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun getById(id: Long): SectionEntity?

    @Query("SELECT * FROM sections WHERE builtInKey = :key")
    suspend fun getByBuiltInKey(key: String): SectionEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM sections")
    suspend fun maxSortOrder(): Int

    /** IGNORE so re-seeding never overwrites a user's edits to a built-in section. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(sections: List<SectionEntity>): List<Long>

    @Insert
    suspend fun insert(section: SectionEntity): Long

    @Update
    suspend fun update(section: SectionEntity)

    @Update
    suspend fun updateAll(sections: List<SectionEntity>)

    @Query("UPDATE sections SET defaultWeeklyTarget = :target, includeInDial = :includeInDial WHERE id = :id")
    suspend fun updateTarget(id: Long, target: Double, includeInDial: Boolean)

    @Query("UPDATE sections SET weight = :weight WHERE id = :id")
    suspend fun setWeight(id: Long, weight: Double)

    @Query("UPDATE sections SET includeInDial = :include WHERE id = :id")
    suspend fun setIncludeInDial(id: Long, include: Boolean)

    @Query("DELETE FROM sections WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustom(id: Long)

    @Upsert
    suspend fun upsertOverride(override: WeeklyTargetOverrideEntity)

    @Query("DELETE FROM weekly_target_overrides WHERE sectionId = :sectionId AND weekStart = :weekStart")
    suspend fun deleteOverride(sectionId: Long, weekStart: LocalDate)

    @Query("SELECT * FROM weekly_target_overrides WHERE weekStart = :weekStart")
    fun observeOverridesForWeek(weekStart: LocalDate): Flow<List<WeeklyTargetOverrideEntity>>

    @Transaction
    suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("UPDATE sections SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)
}
