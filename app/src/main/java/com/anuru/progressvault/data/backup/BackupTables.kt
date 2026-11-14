package com.anuru.progressvault.data.backup

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
import kotlinx.serialization.Serializable

/** Every Room table, as stored. Missing lists in older backups decode as empty. */
@Serializable
data class BackupTables(
    val sections: List<SectionEntity> = emptyList(),
    val weeklyTargetOverrides: List<WeeklyTargetOverrideEntity> = emptyList(),
    val semesters: List<SemesterEntity> = emptyList(),
    val modules: List<ModuleEntity> = emptyList(),
    val moduleDeadlines: List<ModuleDeadlineEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val activityLogs: List<ActivityLogEntity> = emptyList(),
    val leetCodeProblems: List<LeetCodeProblemEntity> = emptyList(),
    val leetCodeStatsSnapshots: List<LeetCodeStatsSnapshotEntity> = emptyList(),
    val leetCodeTagCounts: List<LeetCodeTagCountEntity> = emptyList(),
    val gitHubContributionDays: List<GitHubContributionDayEntity> = emptyList(),
    val syncStates: List<SyncStateEntity> = emptyList(),
    val weeklySnapshots: List<WeeklySnapshotEntity> = emptyList(),
    val weeklySectionSnapshots: List<WeeklySectionSnapshotEntity> = emptyList(),
    val achievements: List<AchievementEntity> = emptyList(),
    val manualSkills: List<ManualSkillEntity> = emptyList(),
    val plannerTasks: List<PlannerTaskEntity> = emptyList(),
)
