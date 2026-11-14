package com.anuru.progressvault.ui.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.data.db.entity.ModuleDeadlineEntity
import com.anuru.progressvault.data.repository.AcademicsRepository
import com.anuru.progressvault.data.repository.LogRepository
import com.anuru.progressvault.data.repository.ProgressRepository
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.model.Module
import com.anuru.progressvault.domain.model.ModuleDraft
import com.anuru.progressvault.domain.model.Semester
import com.anuru.progressvault.domain.progress.ModuleProgress
import com.anuru.progressvault.domain.progress.SectionProgress
import com.anuru.progressvault.domain.time.WeekCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class AcademicsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val semester: Semester? = null,
    val modules: List<Module> = emptyList(),
    val weekModules: List<ModuleProgress> = emptyList(),
    val section: SectionProgress? = null,
    val lifetimeHours: Map<Long, Double> = emptyMap(),
    val deadlines: List<ModuleDeadlineEntity> = emptyList(),
    val archived: List<Semester> = emptyList(),
) {
    /** 1-based teaching week within the semester, or null outside it. */
    val semesterWeek: Pair<Int, Int>? get() {
        val s = semester ?: return null
        if (today.isBefore(s.startDate) || today.isAfter(s.endDate)) return null
        val total = (ChronoUnit.DAYS.between(s.startDate, s.endDate) / 7 + 1).toInt()
        return (ChronoUnit.DAYS.between(s.startDate, today) / 7 + 1).toInt() to total
    }
}

sealed interface AcademicsEvent { data object ModuleInUse : AcademicsEvent }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AcademicsViewModel @Inject constructor(
    private val repository: AcademicsRepository,
    private val logRepository: LogRepository,
    progressRepository: ProgressRepository,
    private val weekCalculator: WeekCalculator,
) : ViewModel() {
    private val _events = Channel<AcademicsEvent>(Channel.BUFFERED)
    val events: Flow<AcademicsEvent> = _events.receiveAsFlow()

    private val activeSemester = repository.observeActiveSemester()

    val state: StateFlow<AcademicsUiState> = combine(
        activeSemester.flatMapLatest { s ->
            if (s == null) flowOf(Triple<Semester?, List<Module>, List<ModuleDeadlineEntity>>(null, emptyList(), emptyList()))
            else combine(repository.observeModules(s.id), repository.observeDeadlines(s.id)) { m, d -> Triple(s, m, d) }
        },
        progressRepository.observeWeek(weekCalculator.currentWeek()),
        repository.observeModuleHours(),
        repository.observeSemesters(),
    ) { (semester, modules, deadlines), week, hours, all ->
        AcademicsUiState(
            isLoading = false,
            today = weekCalculator.today(),
            semester = semester,
            modules = modules,
            weekModules = week.modules,
            section = week.sections.firstOrNull { it.builtInKey == BuiltInSectionKey.ACADEMICS },
            lifetimeHours = hours,
            deadlines = deadlines,
            archived = all.filterNot { it.isActive },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcademicsUiState())

    fun addModule(draft: ModuleDraft) {
        val s = state.value.semester ?: return
        viewModelScope.launch { repository.addModule(s.id, draft) }
    }

    fun updateModule(module: Module, draft: ModuleDraft) { viewModelScope.launch { repository.updateModule(module, draft) } }

    fun deleteModule(module: Module) {
        viewModelScope.launch { if (!repository.deleteModuleIfUnused(module)) _events.send(AcademicsEvent.ModuleInUse) }
    }

    fun logHours(sectionId: Long, moduleId: Long, hours: Double, note: String?) {
        viewModelScope.launch { logRepository.log(sectionId, hours, moduleId = moduleId, note = note) }
    }

    fun addDeadline(d: DeadlineDraft) {
        viewModelScope.launch {
            // Due at the end of the chosen local day.
            val dueAt = d.date.atTime(LocalTime.of(23, 59)).atZone(weekCalculator.zone).toInstant()
            repository.addDeadline(d.moduleId, d.title, d.type, dueAt, d.notes)
        }
    }

    fun toggleDeadline(d: ModuleDeadlineEntity) { viewModelScope.launch { repository.setDeadlineDone(d, !d.isDone) } }
    fun deleteDeadline(d: ModuleDeadlineEntity) { viewModelScope.launch { repository.deleteDeadline(d) } }
}
