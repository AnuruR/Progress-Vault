package com.anuru.progressvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.anuru.progressvault.data.db.entity.GitHubContributionDayEntity
import com.anuru.progressvault.data.db.entity.LeetCodeProblemEntity
import com.anuru.progressvault.data.db.entity.LeetCodeStatsSnapshotEntity
import com.anuru.progressvault.data.db.entity.LeetCodeTagCountEntity
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.data.db.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface SyncDao {
    /** Additive: an already-stored slug keeps its original solve time. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProblems(problems: List<LeetCodeProblemEntity>): List<Long>

    @Query("SELECT * FROM leetcode_problems WHERE solvedAt >= :from AND solvedAt < :to ORDER BY solvedAt DESC")
    fun observeProblemsBetween(from: Instant, to: Instant): Flow<List<LeetCodeProblemEntity>>

    @Query("SELECT * FROM leetcode_problems ORDER BY solvedAt")
    fun observeAllProblems(): Flow<List<LeetCodeProblemEntity>>

    @Insert suspend fun insertStatsSnapshot(snapshot: LeetCodeStatsSnapshotEntity)

    @Upsert suspend fun upsertTagCounts(tags: List<LeetCodeTagCountEntity>)

    @Query("SELECT * FROM leetcode_tag_counts ORDER BY problemsSolved DESC")
    fun observeTagCounts(): Flow<List<LeetCodeTagCountEntity>>

    @Upsert suspend fun upsertContributionDays(days: List<GitHubContributionDayEntity>)

    @Query("SELECT * FROM github_contribution_days WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeContributionsBetween(from: LocalDate, to: LocalDate): Flow<List<GitHubContributionDayEntity>>

    @Query("SELECT * FROM sync_states WHERE source = :source")
    fun observeSyncState(source: SyncSource): Flow<SyncStateEntity?>

    @Upsert suspend fun upsertSyncState(state: SyncStateEntity)

    @Query("SELECT * FROM leetcode_problems WHERE difficulty IS NULL ORDER BY solvedAt DESC LIMIT :limit")
    suspend fun getProblemsMissingDetails(limit: Int): List<LeetCodeProblemEntity>

    @Update suspend fun updateProblems(problems: List<LeetCodeProblemEntity>)

    @Query("SELECT * FROM leetcode_stats_snapshots ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun getLatestStats(): LeetCodeStatsSnapshotEntity?

    @Query("SELECT * FROM leetcode_stats_snapshots ORDER BY fetchedAt DESC LIMIT 1")
    fun observeLatestStats(): Flow<LeetCodeStatsSnapshotEntity?>

    @Query("SELECT * FROM sync_states WHERE source = :source")
    suspend fun getSyncState(source: SyncSource): SyncStateEntity?

    @Query("SELECT COALESCE(SUM(count), 0) FROM github_contribution_days WHERE date BETWEEN :from AND :to")
    fun observeContributionTotal(from: LocalDate, to: LocalDate): Flow<Int>

    @Query("SELECT * FROM github_contribution_days WHERE count > 0 ORDER BY date")
    fun observeAllContributions(): Flow<List<GitHubContributionDayEntity>>

    @Query("SELECT * FROM leetcode_stats_snapshots ORDER BY fetchedAt")
    fun observeAllStats(): Flow<List<LeetCodeStatsSnapshotEntity>>
}
