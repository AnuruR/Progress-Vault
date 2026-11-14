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

/** How a section's weekly progress is produced. */
@Serializable
enum class SectionKind {
    /** Progress = sum of manual ActivityLogs (plus synced data for LeetCode/GitHub). */
    STANDARD,

    /** Progress = combined study-hour progress of the active semester's modules. */
    ACADEMICS,

    /** Derived view (Skills). Never a tracking target, never in the dial. */
    DERIVED,
}

/**
 * A trackable area of life. Built-in sections are identified by a stable [builtInKey]
 * (see BuiltInSectionKey) so the app can find them even after the user renames them.
 */
@Entity(
    tableName = "sections",
    indices = [Index(value = ["builtInKey"], unique = true)],
)
@Serializable
data class SectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val builtInKey: String? = null,
    val kind: SectionKind = SectionKind.STANDARD,
    val name: String,
    val iconKey: String,
    /** ARGB colour, e.g. 0xFF22D3EE. */
    val accentColor: Long,
    val unit: String,
    val defaultWeeklyTarget: Double,
    val weight: Double = 1.0,
    val sortOrder: Int,
    val isBuiltIn: Boolean = false,
    val isHidden: Boolean = false,
    val includeInDial: Boolean = true,
    val isAutoSynced: Boolean = false,
    val createdAt: Instant,
)

/** Overrides a section's target for one specific week without touching the default. */
@Entity(
    tableName = "weekly_target_overrides",
    primaryKeys = ["sectionId", "weekStart"],
    foreignKeys = [
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@Serializable
data class WeeklyTargetOverrideEntity(
    val sectionId: Long,
    /** Monday of the week the override applies to. */
    val weekStart: LocalDate,
    val target: Double,
)

@Serializable
enum class LogSource { MANUAL, LEETCODE_SYNC, GITHUB_SYNC, IMPORT }

/**
 * The single source of truth for manually logged progress (LinkedIn posts, lessons,
 * study hours, custom sections, manual "+1" fallbacks for synced sections).
 *
 * Logs are never deleted when a section is hidden; sectionId is intentionally NOT a
 * cascading foreign key so history survives even a custom section deletion
 * ([sectionNameAtLog] keeps it readable).
 */
@Entity(
    tableName = "activity_logs",
    indices = [
        Index("sectionId"),
        Index("localDate"),
        Index("moduleId"),
        Index("courseId"),
    ],
)
@Serializable
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long,
    val sectionNameAtLog: String,
    val amount: Double,
    val timestamp: Instant,
    /** Local date at the moment of logging; used for week bucketing. */
    val localDate: LocalDate,
    val moduleId: Long? = null,
    val courseId: Long? = null,
    /** e.g. LinkedIn post topic or lesson name. */
    val title: String? = null,
    val url: String? = null,
    val note: String? = null,
    val source: LogSource = LogSource.MANUAL,
)
