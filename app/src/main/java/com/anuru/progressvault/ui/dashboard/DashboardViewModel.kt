package com.anuru.progressvault.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.data.db.entity.SyncStateEntity
import com.anuru.progressvault.data.repository.LogRepository
import com.anuru.progressvault.data.repository.ProgressRepository
import com.anuru.progressvault.data.repository.SyncStatusRepository
import com.anuru.progressvault.domain.progress.ModuleProgress
import com.anuru.progressvault.domain.progress.PaceStatus
import com.anuru.progressvault.domain.progress.ProgressCalculator
import com.anuru.progressvault.domain.progress.SectionProgress
import com.anuru.progressvault.domain.time.WeekCalculator
import com.anuru.progressvault.domain.time.WeekRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val week: WeekRange? = null,
    val isCurrentWeek: Boolean = true,
    val isArchived: Boolean = false,
    val overall: Double = 0.0,
    val daysLeft: Int = 0,
    val elapsed: Double = 0.0,
    val pace: PaceStatus? = null,
    val sections: List<SectionProgress> = emptyList(),
    val modules: List<ModuleProgress> = emptyList(),
    val sync: Map<SyncSource, SyncStateEntity?> = emptyMap(),
) {
    val hasDialSections: Boolean get() = sections.any { it.countsInDial }
}

sealed interface DashboardEvent {
    data class Logged(val logId: Long, val sectionName: String, val amount: Double) : DashboardEvent
    data class SectionCompleted(val sectionName: String) : DashboardEvent
    data object DialCompleted : DashboardEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val logRepository: LogRepository,
    syncStatusRepository: SyncStatusRepository,
    private val weekCalculator: WeekCalculator,
) : ViewModel() {

    private val selectedWeek = MutableStateFlow(weekCalculator.currentWeek())
    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events: Flow<DashboardEvent> = _events.receiveAsFlow()

    /** Completion memory for the current week, used to fire celebrations only on the crossing. */
    private var completedIds: Set<Long>? = null
    private var dialComplete: Boolean? = null
    private var trackedWeek: WeekRange? = null

    val state: StateFlow<DashboardUiState> = selectedWeek
        .flatMapLatest { week ->
            combine(progressRepository.observeWeek(week), syncStatusRepository.observeAll()) { progress, sync ->
                val current = weekCalculator.currentWeek()
                val isCurrent = week == current
                val elapsed = weekCalculator.fractionElapsed(week)
                DashboardUiState(
                    isLoading = false,
                    week = week,
                    isCurrentWeek = isCurrent,
                    isArchived = progress.isArchived,
                    overall = progress.overall,
                    daysLeft = weekCalculator.daysLeft(week),
                    elapsed = elapsed,
                    pace = if (isCurrent) ProgressCalculator.pace(progress.overall, elapsed) else null,
                    sections = progress.sections,
                    modules = progress.modules,
                    sync = sync,
                )
            }
        }
        .onEach(::detectCelebrations)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun detectCelebrations(s: DashboardUiState) {
        if (!s.isCurrentWeek || s.week == null) return
        val done = s.sections.filter { it.isComplete }.map { it.sectionId }.toSet()
        val dialDone = s.hasDialSections && s.overall >= 1.0
        val prev = completedIds
        if (prev != null && trackedWeek == s.week) {
            s.sections.filter { it.sectionId in done && it.sectionId !in prev }
                .forEach { _events.trySend(DashboardEvent.SectionCompleted(it.name)) }
            if (dialDone && dialComplete == false) _events.trySend(DashboardEvent.DialCompleted)
        }
        completedIds = done
        dialComplete = dialDone
        trackedWeek = s.week
    }

    fun previousWeek() = selectedWeek.update { it.previous() }

    fun nextWeek() = selectedWeek.update { if (it.next().start.isAfter(weekCalculator.currentWeek().start)) it else it.next() }

    fun goToCurrentWeek() = selectedWeek.update { weekCalculator.currentWeek() }

    /** Quick "+1". Academics needs a module, so the UI opens the log sheet instead. */
    fun quickLog(section: SectionProgress) = log(section, 1.0)

    fun log(
        section: SectionProgress,
        amount: Double,
        moduleId: Long? = null,
        title: String? = null,
        url: String? = null,
        note: String? = null,
    ) {
        if (amount <= 0.0 || !state.value.isCurrentWeek) return
        viewModelScope.launch {
            val id = logRepository.log(section.sectionId, amount, moduleId = moduleId, title = title, url = url, note = note)
            _events.send(DashboardEvent.Logged(id, section.name, amount))
        }
    }

    fun undo(logId: Long) {
        viewModelScope.launch { logRepository.undo(logId) }
    }
}
