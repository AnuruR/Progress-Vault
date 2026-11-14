package com.anuru.progressvault.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.anuru.progressvault.data.db.Migrations
import com.anuru.progressvault.data.db.ProgressVaultDatabase
import com.anuru.progressvault.data.db.dao.AcademicsDao
import com.anuru.progressvault.data.db.dao.ActivityLogDao
import com.anuru.progressvault.data.db.dao.CourseDao
import com.anuru.progressvault.data.db.dao.HistoryDao
import com.anuru.progressvault.data.db.dao.PlannerDao
import com.anuru.progressvault.data.db.dao.SectionDao
import com.anuru.progressvault.data.db.dao.SyncDao
import com.anuru.progressvault.domain.time.SystemTimeProvider
import com.anuru.progressvault.domain.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ProgressVaultDatabase =
        Room.databaseBuilder(context, ProgressVaultDatabase::class.java, ProgressVaultDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            // Deliberately NO fallbackToDestructiveMigration(): a missing migration must crash
            // in development rather than silently wipe the user's history.
            .build()

    @Provides fun sectionDao(db: ProgressVaultDatabase): SectionDao = db.sectionDao()
    @Provides fun activityLogDao(db: ProgressVaultDatabase): ActivityLogDao = db.activityLogDao()
    @Provides fun academicsDao(db: ProgressVaultDatabase): AcademicsDao = db.academicsDao()
    @Provides fun courseDao(db: ProgressVaultDatabase): CourseDao = db.courseDao()
    @Provides fun syncDao(db: ProgressVaultDatabase): SyncDao = db.syncDao()
    @Provides fun historyDao(db: ProgressVaultDatabase): HistoryDao = db.historyDao()
    @Provides fun plannerDao(db: ProgressVaultDatabase): PlannerDao = db.plannerDao()
    @Provides fun backupDao(db: ProgressVaultDatabase): com.anuru.progressvault.data.db.dao.BackupDao = db.backupDao()
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
