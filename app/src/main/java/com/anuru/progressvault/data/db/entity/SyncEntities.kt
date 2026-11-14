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
import java.time.Instant
import java.time.LocalDate

/** A solved LeetCode problem, de-duplicated by slug. */
@Entity(tableName = "leetcode_problems", indices = [Index("solvedAt")])
@Serializable
data class LeetCodeProblemEntity(
    @PrimaryKey val slug: String,
    val title: String,
    /** EASY / MEDIUM / HARD, or null if not yet known. */
    val difficulty: String?,
    val topicTags: List<String>,
    val solvedAt: Instant,
)

/** Point-in-time totals, so "Then vs Now" can show difficulty breakdown at any past date. */
@Entity(tableName = "leetcode_stats_snapshots", indices = [Index("fetchedAt")])
@Serializable
data class LeetCodeStatsSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fetchedAt: Instant,
    val easySolved: Int,
    val mediumSolved: Int,
    val hardSolved: Int,
    val totalSolved: Int,
)

/** LeetCode topic-tag skill stats (tagProblemCounts). */
@Entity(tableName = "leetcode_tag_counts")
@Serializable
data class LeetCodeTagCountEntity(
    @PrimaryKey val tagSlug: String,
    val tagName: String,
    /** fundamental / intermediate / advanced */
    val level: String,
    val problemsSolved: Int,
    val updatedAt: Instant,
)

@Entity(tableName = "github_contribution_days")
@Serializable
data class GitHubContributionDayEntity(
    @PrimaryKey val date: LocalDate,
    val count: Int,
    val updatedAt: Instant,
)

@Serializable
enum class SyncSource { LEETCODE, GITHUB }

@Entity(tableName = "sync_states")
@Serializable
data class SyncStateEntity(
    @PrimaryKey val source: SyncSource,
    val lastAttemptAt: Instant? = null,
    val lastSuccessAt: Instant? = null,
    val lastError: String? = null,
)
