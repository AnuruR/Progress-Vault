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

/** Archived result of a finished week. Immutable once written. */
@Entity(tableName = "weekly_snapshots")
@Serializable
data class WeeklySnapshotEntity(
    /** Monday of the archived week. */
    @PrimaryKey val weekStart: LocalDate,
    /** 0.0 – 1.0 overall weighted dial value. */
    val overallCompletion: Double,
    val createdAt: Instant,
)

/**
 * Per-section result inside a [WeeklySnapshotEntity]. Name/unit/colour are copied so the
 * snapshot remains meaningful after sections are renamed, hidden or deleted.
 */
@Entity(tableName = "weekly_section_snapshots", primaryKeys = ["weekStart", "sectionId"])
@Serializable
data class WeeklySectionSnapshotEntity(
    val weekStart: LocalDate,
    val sectionId: Long,
    val sectionName: String,
    val builtInKey: String?,
    val unit: String,
    val accentColor: Long,
    val target: Double,
    val achieved: Double,
    val weight: Double,
    val includedInDial: Boolean,
)

@Entity(tableName = "achievements")
@Serializable
data class AchievementEntity(
    /** Stable achievement key, e.g. "leetcode_100". */
    @PrimaryKey val key: String,
    val earnedAt: Instant,
    /** Optional value that triggered it (e.g. the count). */
    val value: Double? = null,
)

/** A skill entered by hand (the rest of the skill list is derived). */
@Entity(tableName = "manual_skills")
@Serializable
data class ManualSkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val firstSeen: LocalDate,
    val lastPracticed: LocalDate? = null,
    val notes: String? = null,
)
