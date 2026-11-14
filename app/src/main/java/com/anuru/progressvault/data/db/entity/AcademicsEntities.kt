@file:UseSerializers(
    com.anuru.progressvault.data.backup.InstantSerializer::class,
    com.anuru.progressvault.data.backup.LocalDateSerializer::class,
    com.anuru.progressvault.data.backup.DayOfWeekSerializer::class,
)

package com.anuru.progressvault.data.db.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "semesters")
@Serializable
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isActive: Boolean,
    /** Set when the semester is archived via "Start new semester". Kept forever for history. */
    val archivedAt: Instant? = null,
    val createdAt: Instant,
)

@Entity(
    tableName = "modules",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("semesterId")],
)
@Serializable
data class ModuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val semesterId: Long,
    val code: String,
    val name: String,
    val accentColor: Long,
    val weeklyTargetHours: Double,
    val credits: Int? = null,
    val sortOrder: Int = 0,
)

@Serializable
enum class DeadlineType { ASSIGNMENT, EXAM, QUIZ, PROJECT, OTHER }

@Entity(
    tableName = "module_deadlines",
    foreignKeys = [
        ForeignKey(
            entity = ModuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["moduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("moduleId"), Index("dueAt")],
)
@Serializable
data class ModuleDeadlineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moduleId: Long,
    val title: String,
    val type: DeadlineType,
    val dueAt: Instant,
    val isDone: Boolean = false,
    val notes: String? = null,
)
