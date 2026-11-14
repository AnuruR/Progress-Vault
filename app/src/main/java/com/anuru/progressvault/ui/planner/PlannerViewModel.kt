package com.anuru.progressvault.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.data.db.entity.ModuleDeadlineEntity
import com.anuru.progressvault.data.db.entity.PlannerTaskEntity
import com.anuru.progressvault.data.db.entity.TaskStatus
import com.anuru.progressvault.data.repository.AcademicsRepository
import com.anuru.progressvault.data.repository.PlannerRepository
import com.anuru.progressvault.data.repository.SectionRepository
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.model.Module
import com.anuru.progressvault.domain.model.Section
import com.anuru.progressvault.domain.planner.AutoBalancer
import com.anuru.progressvault.domain.planner.BalanceTask
import com.anuru.progressvault.domain.planner.TaskMove
import com.anuru.progressvault.domain.time.WeekCalculator
import com.anuru.progressvault.domain.time.WeekRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One group in the planner list: a weekday, or unscheduled when [day] is null. */
data class DayGroup(val day: DayOfWeek?, val date: LocalDate?, val tasks: List<PlannerTaskEntity>)

data class MovePreview(val move: TaskMove, val title: String)

data class PlannerUiState(
    val isLoading: Boolean = true,
    val week: WeekRange? = null,
    val today: LocalDate = LocalDate.now(),
    val groups: List<DayGroup> = emptyList(),
    val sections: List<Section> = emptyList(),
    val modules: List<Module> = emptyList(),
    val deadlines: List<ModuleDeadlineEntity> = emptyList(),
    /** Unfinished tasks left in last week that the user hasn't decided about yet. */
    val pendingRollover: Int = 0,
) {
    val isCurrentWeek: Boolean get() = week != null && today in week
    val isPastWeek: Boolean get() = week != null && today.isAfter(week.end)
    val totalTasks: Int get() = groups.sumOf { it.tasks.size }
    val doneTasks: Int get() = groups.sumOf { g -> g.tasks.count { it.status == TaskStatus.DONE } }
    val todoTasks: Int get() = groups.sumOf { g -> g.tasks.count { it.status == TaskStatus.TODO } }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val repository: PlannerRepository,
    sectionRepository: SectionRepository,
    academicsRepository: AcademicsRepository,
    private val settingsRepository: SettingsRepository,
    private val weekCalculator: WeekCalculator,
) : ViewModel() {

    private val selectedWeek = MutableStateFlow(weekCalculator.currentWeek())
    private val _preview = MutableStateFlow<List<MovePreview>?>(null)
    val balancePreview: StateFlow<List<MovePreview>?> = _preview.asStateFlow()

    private val deadlinesFlow = academicsRepository.observeActiveSemester().flatMapLatest { s ->
        if (s == null) flowOf(emptyList()) else academicsRepository.observeDeadlines(s.id)
    }

    private val rolloverFlow = combine(
        repository.observeTodoCount(weekCalculator.currentWeek().previous().start),
        settingsRepository.rolloverHandledWeek,
    ) { count, handled -> if (handled == weekCalculator.currentWeek().previous().start) 0 else count }

    val state: StateFlow<PlannerUiState> = combine(
        selectedWeek.flatMapLatest { w -> repository.observeWeek(w.start).combine(flowOf(w)) { t, wk -> wk to t } },
        sectionRepository.observeVisible(),
        academicsRepository.observeActiveModules(),
        deadlinesFlow,
        rolloverFlow,
    ) { (week, tasks), sections, modules, deadlines, rollover ->
        val byDay = tasks.groupBy { it.day }
        val sorter = compareBy<PlannerTaskEntity>({ it.status != TaskStatus.TODO }, { -it.priority.ordinal }, { it.sortOrder })
        val groups = buildList {
            byDay[null]?.let { add(DayGroup(null, null, it.sortedWith(sorter))) }
            DayOfWeek.entries.forEach { d ->
                add(DayGroup(d, week.start.plusDays((d.value - 1).toLong()), byDay[d].orEmpty().sortedWith(sorter)))
            }
        }
        PlannerUiState(
            isLoading = false,
            week = week,
            today = weekCalculator.today(),
            groups = groups,
            sections = sections.filterNot { it.isDerived },
            modules = modules,
            deadlines = deadlines,
            pendingRollover = rollover,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerUiState())

    fun previousWeek() = selectedWeek.update { it.previous() }
    fun nextWeek() = selectedWeek.update { it.next() }
    fun goToCurrentWeek() = selectedWeek.update { weekCalculator.currentWeek() }

    fun save(task: PlannerTaskEntity) { viewModelScope.launch { repository.save(task) } }
    fun delete(task: PlannerTaskEntity) { viewModelScope.launch { repository.delete(task) } }

    fun toggleDone(task: PlannerTaskEntity) {
        viewModelScope.launch { repository.setStatus(task, if (task.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE) }
    }

    fun moveToDay(taskId: Long, day: DayOfWeek?) { viewModelScope.launch { repository.moveToDay(taskId, day) } }
    fun moveToNextWeek(taskId: Long) { viewModelScope.launch { repository.moveToNextWeek(taskId) } }

    /** Builds the Auto-balance preview; nothing changes until [applyBalance]. */
    fun previewBalance() {
        val s = state.value
        val week = s.week ?: return
        val zone: ZoneId = weekCalculator.zone
        val deadlineDates = s.deadlines.associate { it.id to it.dueAt.atZone(zone).toLocalDate() }
        val todo = s.groups.flatMap { it.tasks }.filter { it.status == TaskStatus.TODO }
        val moves = AutoBalancer.plan(
            todo.map { BalanceTask(it.id, it.day, it.priority, it.deadlineId?.let(deadlineDates::get)) },
            week,
            s.today,
        )
        val titles = todo.associate { it.id to it.title }
        _preview.value = moves.map { MovePreview(it, titles[it.taskId].orEmpty()) }
    }

    fun applyBalance() {
        val moves = _preview.value ?: return
        _preview.value = null
        viewModelScope.launch { repository.applyMoves(moves.map { it.move }) }
    }

    fun dismissBalance() { _preview.value = null }

    fun rollOverPrevious(roll: Boolean) {
        val prev = weekCalculator.currentWeek().previous().start
        viewModelScope.launch {
            if (roll) repository.rollOver(prev)
            settingsRepository.setRolloverHandled(prev)
        }
    }

    /** From a past week: move its unfinished tasks into the current week. */
    fun moveUnfinishedToThisWeek() {
        val from = state.value.week?.start ?: return
        viewModelScope.launch { repository.moveUnfinished(from, weekCalculator.currentWeek().start) }
    }

    /** Defaults for a brand-new task in the selected week. */
    fun newTaskTemplate(day: DayOfWeek?): PlannerTaskEntity {
        val s = state.value
        val week = s.week ?: weekCalculator.currentWeek()
        val defaultDay = day ?: if (s.isCurrentWeek) s.today.dayOfWeek else null
        return PlannerTaskEntity(title = "", weekStart = week.start, day = defaultDay, createdAt = weekCalculator.now())
    }
}
