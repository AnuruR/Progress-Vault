package com.anuru.progressvault.ui.sections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anuru.progressvault.data.db.dao.SyncDao
import com.anuru.progressvault.data.db.entity.ActivityLogEntity
import com.anuru.progressvault.data.db.entity.GitHubContributionDayEntity
import com.anuru.progressvault.data.db.entity.LeetCodeProblemEntity
import com.anuru.progressvault.data.db.entity.LeetCodeStatsSnapshotEntity
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.data.db.entity.SyncStateEntity
import com.anuru.progressvault.data.repository.SyncRepository
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.anuru.progressvault.data.db.entity.WeeklyTargetOverrideEntity
import com.anuru.progressvault.data.repository.LogRepository
import com.anuru.progressvault.data.repository.ProgressRepository
import com.anuru.progressvault.data.repository.SectionRepository
import com.anuru.progressvault.domain.model.Section
import com.anuru.progressvault.domain.progress.SectionProgress
import com.anuru.progressvault.domain.time.WeekCalculator
import com.anuru.progressvault.ui.navigation.SectionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SyncPanelState(
    val source: SyncSource? = null,
    val configured: Boolean = false,
    val sync: SyncStateEntity? = null,
    val running: Boolean = false,
    val problems: List<LeetCodeProblemEntity> = emptyList(),
    val stats: LeetCodeStatsSnapshotEntity? = null,
    val days: List<GitHubContributionDayEntity> = emptyList(),
    val weekDays: List<LocalDate> = emptyList(),
)

data class SectionDetailUiState(
    val isLoading: Boolean = true,
    val section: Section? = null,
    val progress: SectionProgress? = null,
    val override: WeeklyTargetOverrideEntity? = null,
    val weekStart: LocalDate? = null,
    val logs: List<ActivityLogEntity> = emptyList(),
)

@HiltViewModel
class SectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sectionRepository: SectionRepository,
    private val logRepository: LogRepository,
    progressRepository: ProgressRepository,
    private val weekCalculator: WeekCalculator,
    private val syncRepository: SyncRepository,
    syncDao: SyncDao,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val sectionId = savedStateHandle.toRoute<SectionDetailRoute>().sectionId
    private val week = weekCalculator.currentWeek()

    val state: StateFlow<SectionDetailUiState> = combine(
        sectionRepository.observe(sectionId),
        progressRepository.observeWeek(week),
        sectionRepository.observeOverride(sectionId, week.start),
        sectionRepository.observeLogs(sectionId),
    ) { section, progress, override, logs ->
        SectionDetailUiState(
            isLoading = false,
            section = section,
            progress = progress.sections.firstOrNull { it.sectionId == sectionId },
            override = override,
            weekStart = week.start,
            logs = logs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionDetailUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val syncPanel: StateFlow<SyncPanelState> = sectionRepository.observe(sectionId).flatMapLatest { section ->
        val zone = weekCalculator.zone
        when (section?.builtInKey) {
            BuiltInSectionKey.LEETCODE -> combine(
                settingsRepository.settings, syncDao.observeSyncState(SyncSource.LEETCODE), syncRepository.running,
                syncDao.observeProblemsBetween(week.startInstant(zone), week.endExclusiveInstant(zone)), syncDao.observeLatestStats(),
            ) { s, st, running, problems, stats ->
                SyncPanelState(SyncSource.LEETCODE, s.leetCodeUsername.isNotBlank(), st, SyncSource.LEETCODE in running, problems, stats)
            }
            BuiltInSectionKey.GITHUB -> combine(
                settingsRepository.settings, syncDao.observeSyncState(SyncSource.GITHUB), syncRepository.running,
                syncDao.observeContributionsBetween(week.start, week.end),
            ) { s, st, running, days ->
                SyncPanelState(SyncSource.GITHUB, s.gitHubUsername.isNotBlank(), st, SyncSource.GITHUB in running, days = days, weekDays = week.days)
            }
            else -> flowOf(SyncPanelState())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncPanelState())

    fun syncNow() {
        viewModelScope.launch {
            when (syncPanel.value.source) {
                SyncSource.LEETCODE -> syncRepository.syncLeetCode()
                SyncSource.GITHUB -> syncRepository.syncGitHub()
                null -> Unit
            }
        }
    }

    fun log(amount: Double, title: String?, url: String?, note: String?) {
        viewModelScope.launch { logRepository.log(sectionId, amount, title = title, url = url, note = note) }
    }

    fun deleteLog(log: ActivityLogEntity) {
        viewModelScope.launch { sectionRepository.deleteLog(log) }
    }

    fun setOverride(target: Double) {
        viewModelScope.launch { sectionRepository.setOverride(sectionId, week.start, target) }
    }

    fun clearOverride() {
        viewModelScope.launch { sectionRepository.clearOverride(sectionId, week.start) }
    }
}
