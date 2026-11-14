package com.anuru.progressvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.anuru.progressvault.data.backup.BackupTables
import com.anuru.progressvault.data.db.entity.AchievementEntity
import com.anuru.progressvault.data.db.entity.ActivityLogEntity
import com.anuru.progressvault.data.db.entity.CourseEntity
import com.anuru.progressvault.data.db.entity.GitHubContributionDayEntity
import com.anuru.progressvault.data.db.entity.LeetCodeProblemEntity
import com.anuru.progressvault.data.db.entity.LeetCodeStatsSnapshotEntity
import com.anuru.progressvault.data.db.entity.LeetCodeTagCountEntity
import com.anuru.progressvault.data.db.entity.ManualSkillEntity
import com.anuru.progressvault.data.db.entity.ModuleDeadlineEntity
import com.anuru.progressvault.data.db.entity.ModuleEntity
import com.anuru.progressvault.data.db.entity.PlannerTaskEntity
import com.anuru.progressvault.data.db.entity.SectionEntity
import com.anuru.progressvault.data.db.entity.SemesterEntity
import com.anuru.progressvault.data.db.entity.SyncStateEntity
import com.anuru.progressvault.data.db.entity.WeeklySectionSnapshotEntity
import com.anuru.progressvault.data.db.entity.WeeklySnapshotEntity
import com.anuru.progressvault.data.db.entity.WeeklyTargetOverrideEntity

/** Whole-database read and replace, used only by JSON backup / restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM sections") suspend fun allSection(): List<SectionEntity>
    @Query("SELECT * FROM weekly_target_overrides") suspend fun allWeeklyTargetOverride(): List<WeeklyTargetOverrideEntity>
    @Query("SELECT * FROM semesters") suspend fun allSemester(): List<SemesterEntity>
    @Query("SELECT * FROM modules") suspend fun allModule(): List<ModuleEntity>
    @Query("SELECT * FROM module_deadlines") suspend fun allModuleDeadline(): List<ModuleDeadlineEntity>
    @Query("SELECT * FROM courses") suspend fun allCourse(): List<CourseEntity>
    @Query("SELECT * FROM activity_logs") suspend fun allActivityLog(): List<ActivityLogEntity>
    @Query("SELECT * FROM leetcode_problems") suspend fun allLeetCodeProblem(): List<LeetCodeProblemEntity>
    @Query("SELECT * FROM leetcode_stats_snapshots") suspend fun allLeetCodeStatsSnapshot(): List<LeetCodeStatsSnapshotEntity>
    @Query("SELECT * FROM leetcode_tag_counts") suspend fun allLeetCodeTagCount(): List<LeetCodeTagCountEntity>
    @Query("SELECT * FROM github_contribution_days") suspend fun allGitHubContributionDay(): List<GitHubContributionDayEntity>
    @Query("SELECT * FROM sync_states") suspend fun allSyncState(): List<SyncStateEntity>
    @Query("SELECT * FROM weekly_snapshots") suspend fun allWeeklySnapshot(): List<WeeklySnapshotEntity>
    @Query("SELECT * FROM weekly_section_snapshots") suspend fun allWeeklySectionSnapshot(): List<WeeklySectionSnapshotEntity>
    @Query("SELECT * FROM achievements") suspend fun allAchievement(): List<AchievementEntity>
    @Query("SELECT * FROM manual_skills") suspend fun allManualSkill(): List<ManualSkillEntity>
    @Query("SELECT * FROM planner_tasks") suspend fun allPlannerTask(): List<PlannerTaskEntity>

    @Insert suspend fun insertSection(rows: List<SectionEntity>)
    @Insert suspend fun insertWeeklyTargetOverride(rows: List<WeeklyTargetOverrideEntity>)
    @Insert suspend fun insertSemester(rows: List<SemesterEntity>)
    @Insert suspend fun insertModule(rows: List<ModuleEntity>)
    @Insert suspend fun insertModuleDeadline(rows: List<ModuleDeadlineEntity>)
    @Insert suspend fun insertCourse(rows: List<CourseEntity>)
    @Insert suspend fun insertActivityLog(rows: List<ActivityLogEntity>)
    @Insert suspend fun insertLeetCodeProblem(rows: List<LeetCodeProblemEntity>)
    @Insert suspend fun insertLeetCodeStatsSnapshot(rows: List<LeetCodeStatsSnapshotEntity>)
    @Insert suspend fun insertLeetCodeTagCount(rows: List<LeetCodeTagCountEntity>)
    @Insert suspend fun insertGitHubContributionDay(rows: List<GitHubContributionDayEntity>)
    @Insert suspend fun insertSyncState(rows: List<SyncStateEntity>)
    @Insert suspend fun insertWeeklySnapshot(rows: List<WeeklySnapshotEntity>)
    @Insert suspend fun insertWeeklySectionSnapshot(rows: List<WeeklySectionSnapshotEntity>)
    @Insert suspend fun insertAchievement(rows: List<AchievementEntity>)
    @Insert suspend fun insertManualSkill(rows: List<ManualSkillEntity>)
    @Insert suspend fun insertPlannerTask(rows: List<PlannerTaskEntity>)

    @Query("DELETE FROM planner_tasks") suspend fun clearPlannerTask()
    @Query("DELETE FROM manual_skills") suspend fun clearManualSkill()
    @Query("DELETE FROM achievements") suspend fun clearAchievement()
    @Query("DELETE FROM weekly_section_snapshots") suspend fun clearWeeklySectionSnapshot()
    @Query("DELETE FROM weekly_snapshots") suspend fun clearWeeklySnapshot()
    @Query("DELETE FROM sync_states") suspend fun clearSyncState()
    @Query("DELETE FROM github_contribution_days") suspend fun clearGitHubContributionDay()
    @Query("DELETE FROM leetcode_tag_counts") suspend fun clearLeetCodeTagCount()
    @Query("DELETE FROM leetcode_stats_snapshots") suspend fun clearLeetCodeStatsSnapshot()
    @Query("DELETE FROM leetcode_problems") suspend fun clearLeetCodeProblem()
    @Query("DELETE FROM activity_logs") suspend fun clearActivityLog()
    @Query("DELETE FROM courses") suspend fun clearCourse()
    @Query("DELETE FROM module_deadlines") suspend fun clearModuleDeadline()
    @Query("DELETE FROM modules") suspend fun clearModule()
    @Query("DELETE FROM semesters") suspend fun clearSemester()
    @Query("DELETE FROM weekly_target_overrides") suspend fun clearWeeklyTargetOverride()
    @Query("DELETE FROM sections") suspend fun clearSection()

    suspend fun readAll(): BackupTables = BackupTables(
        sections = allSection(),
        weeklyTargetOverrides = allWeeklyTargetOverride(),
        semesters = allSemester(),
        modules = allModule(),
        moduleDeadlines = allModuleDeadline(),
        courses = allCourse(),
        activityLogs = allActivityLog(),
        leetCodeProblems = allLeetCodeProblem(),
        leetCodeStatsSnapshots = allLeetCodeStatsSnapshot(),
        leetCodeTagCounts = allLeetCodeTagCount(),
        gitHubContributionDays = allGitHubContributionDay(),
        syncStates = allSyncState(),
        weeklySnapshots = allWeeklySnapshot(),
        weeklySectionSnapshots = allWeeklySectionSnapshot(),
        achievements = allAchievement(),
        manualSkills = allManualSkill(),
        plannerTasks = allPlannerTask(),
    )

    /** Atomic: if any insert fails, nothing is changed. */
    @Transaction
    suspend fun replaceAll(t: BackupTables) {
        clearPlannerTask()
        clearManualSkill()
        clearAchievement()
        clearWeeklySectionSnapshot()
        clearWeeklySnapshot()
        clearSyncState()
        clearGitHubContributionDay()
        clearLeetCodeTagCount()
        clearLeetCodeStatsSnapshot()
        clearLeetCodeProblem()
        clearActivityLog()
        clearCourse()
        clearModuleDeadline()
        clearModule()
        clearSemester()
        clearWeeklyTargetOverride()
        clearSection()
        insertSection(t.sections)
        insertWeeklyTargetOverride(t.weeklyTargetOverrides)
        insertSemester(t.semesters)
        insertModule(t.modules)
        insertModuleDeadline(t.moduleDeadlines)
        insertCourse(t.courses)
        insertActivityLog(t.activityLogs)
        insertLeetCodeProblem(t.leetCodeProblems)
        insertLeetCodeStatsSnapshot(t.leetCodeStatsSnapshots)
        insertLeetCodeTagCount(t.leetCodeTagCounts)
        insertGitHubContributionDay(t.gitHubContributionDays)
        insertSyncState(t.syncStates)
        insertWeeklySnapshot(t.weeklySnapshots)
        insertWeeklySectionSnapshot(t.weeklySectionSnapshots)
        insertAchievement(t.achievements)
        insertManualSkill(t.manualSkills)
        insertPlannerTask(t.plannerTasks)
    }
}
