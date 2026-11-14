@file:UseSerializers(
    com.anuru.progressvault.data.backup.InstantSerializer::class,
    com.anuru.progressvault.data.backup.LocalDateSerializer::class,
    com.anuru.progressvault.data.backup.DayOfWeekSerializer::class,
)

package com.anuru.progressvault.data.db.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Serializable
enum class CoursePlatform { COURSERA, UDEMY, YOUTUBE, OTHER }

@Serializable
enum class CourseStatus { IN_PROGRESS, COMPLETED, PAUSED }

/** Lessons completed are stored as ActivityLogs with [ActivityLogEntity.courseId] set. */
@Entity(tableName = "courses")
@Serializable
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val platform: CoursePlatform,
    val url: String? = null,
    val totalLessons: Int? = null,
    val status: CourseStatus = CourseStatus.IN_PROGRESS,
    val skillTags: List<String> = emptyList(),
    val startDate: LocalDate? = null,
    val finishDate: LocalDate? = null,
    val certificateUrl: String? = null,
    val createdAt: Instant,
)
