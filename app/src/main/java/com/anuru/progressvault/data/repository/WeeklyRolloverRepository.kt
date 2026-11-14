package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.HistoryDao
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.rollover.WeeklyArchiver
import com.anuru.progressvault.domain.time.WeekCalculator
import com.anuru.progressvault.domain.time.WeekRange
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyRolloverRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
    private val weekCalculator: WeekCalculator,
) {
    /**
     * Archives every finished, not-yet-archived week as an immutable WeeklySnapshot computed
     * from the stored logs. Idempotent: safe to run any number of times.
     */
    suspend fun archiveFinishedWeeks(): List<WeekRange> {
        val firstUse = settingsRepository.current().firstLaunchDate ?: weekCalculator.today()
        val weeks = WeeklyArchiver.weeksToArchive(firstUse, weekCalculator.currentWeek(), historyDao.getSnapshotWeeks().toSet())
        return weeks.filter { week ->
            val inputs = progressRepository.observeInputs(week).first()
            val (snapshot, rows) = WeeklyArchiver.snapshot(ProgressRepository.computeLive(week, inputs), weekCalculator.now())
            historyDao.archiveWeek(snapshot, rows)
        }
    }
}
