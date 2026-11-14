@file:UseSerializers(
    com.anuru.progressvault.data.backup.InstantSerializer::class,
    com.anuru.progressvault.data.backup.LocalDateSerializer::class,
    com.anuru.progressvault.data.backup.DayOfWeekSerializer::class,
)

package com.anuru.progressvault.data.db.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@Serializable
enum class TaskPriority { LOW, MEDIUM, HIGH }

@Serializable
enum class TaskStatus { TODO, DONE, SKIPPED }

@Entity(tableName = "planner_tasks", indices = [Index("weekStart"), Index("sectionId")])
@Serializable
data class PlannerTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sectionId: Long? = null,
    val moduleId: Long? = null,
    val deadlineId: Long? = null,
    /** Monday of the week the task belongs to. */
    val weekStart: LocalDate,
    /** null = unscheduled within the week. */
    val day: DayOfWeek? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val notes: String? = null,
    val sortOrder: Int = 0,
    /** Set when the task was rolled over from a previous week. */
    val rolledOverFrom: LocalDate? = null,
    val createdAt: Instant,
    val completedAt: Instant? = null,
)
