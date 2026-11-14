package com.anuru.progressvault.data.backup

import android.content.Context
import android.net.Uri
import com.anuru.progressvault.data.db.ProgressVaultDatabase
import com.anuru.progressvault.data.db.dao.BackupDao
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupOutcome {
    data class Exported(val rows: Int) : BackupOutcome
    data class Imported(val rows: Int) : BackupOutcome
    data class Failed(val reason: Reason) : BackupOutcome
    enum class Reason { NOT_A_BACKUP, TOO_NEW, CORRUPT, IO }
}

/** Export / import of all data as JSON through Storage Access Framework URIs. */
@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) {
    suspend fun export(uri: Uri): BackupOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val tables = backupDao.readAll()
            val file = BackupFile(
                schemaVersion = ProgressVaultDatabase.VERSION,
                exportedAt = time.clock().instant().toString(),
                settings = settings.exportBackup(),
                tables = tables,
            )
            context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(BackupCodec.encode(file)) }
            BackupOutcome.Exported(tables.rowCount())
        }.getOrElse { BackupOutcome.Failed(BackupOutcome.Reason.IO) }
    }

    /** Replaces ALL data with the backup. Validates fully before touching the database. */
    suspend fun import(uri: Uri): BackupOutcome = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        }.getOrElse { return@withContext BackupOutcome.Failed(BackupOutcome.Reason.IO) }
        when (val decoded = BackupCodec.decode(text)) {
            DecodeResult.NotABackup -> BackupOutcome.Failed(BackupOutcome.Reason.NOT_A_BACKUP)
            DecodeResult.TooNew -> BackupOutcome.Failed(BackupOutcome.Reason.TOO_NEW)
            DecodeResult.Corrupt -> BackupOutcome.Failed(BackupOutcome.Reason.CORRUPT)
            is DecodeResult.Ok -> runCatching {
                backupDao.replaceAll(decoded.file.tables)
                settings.restoreBackup(decoded.file.settings)
                BackupOutcome.Imported(decoded.file.tables.rowCount())
            }.getOrElse { BackupOutcome.Failed(BackupOutcome.Reason.CORRUPT) }
        }
    }
}

fun BackupTables.rowCount(): Int = sections.size + weeklyTargetOverrides.size + activityLogs.size + semesters.size + modules.size +
    moduleDeadlines.size + courses.size + leetCodeProblems.size + leetCodeStatsSnapshots.size + leetCodeTagCounts.size +
    gitHubContributionDays.size + syncStates.size + weeklySnapshots.size + weeklySectionSnapshots.size + achievements.size +
    manualSkills.size + plannerTasks.size
