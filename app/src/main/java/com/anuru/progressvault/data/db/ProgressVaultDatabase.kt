package com.anuru.progressvault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.anuru.progressvault.data.db.dao.AcademicsDao
import com.anuru.progressvault.data.db.dao.BackupDao
import com.anuru.progressvault.data.db.dao.ActivityLogDao
import com.anuru.progressvault.data.db.dao.CourseDao
import com.anuru.progressvault.data.db.dao.HistoryDao
import com.anuru.progressvault.data.db.dao.PlannerDao
import com.anuru.progressvault.data.db.dao.SectionDao
import com.anuru.progressvault.data.db.dao.SyncDao
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

@Database(
    version = 1,
    exportSchema = true,
    entities = [
        SectionEntity::class,
        WeeklyTargetOverrideEntity::class,
        ActivityLogEntity::class,
        SemesterEntity::class,
        ModuleEntity::class,
        ModuleDeadlineEntity::class,
        CourseEntity::class,
        LeetCodeProblemEntity::class,
        LeetCodeStatsSnapshotEntity::class,
        LeetCodeTagCountEntity::class,
        GitHubContributionDayEntity::class,
        SyncStateEntity::class,
        WeeklySnapshotEntity::class,
        WeeklySectionSnapshotEntity::class,
        AchievementEntity::class,
        ManualSkillEntity::class,
        PlannerTaskEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class ProgressVaultDatabase : RoomDatabase() {
    abstract fun sectionDao(): SectionDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun academicsDao(): AcademicsDao
    abstract fun courseDao(): CourseDao
    abstract fun syncDao(): SyncDao
    abstract fun historyDao(): HistoryDao
    abstract fun plannerDao(): PlannerDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "progress_vault.db"
        const val VERSION = 1
    }
}
