package com.anuru.progressvault.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.anuru.progressvault.data.db.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY status, createdAt DESC")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id")
    fun observe(id: Long): Flow<CourseEntity?>

    @Insert suspend fun insert(course: CourseEntity): Long
    @Update suspend fun update(course: CourseEntity)
    @Delete suspend fun delete(course: CourseEntity)
}
