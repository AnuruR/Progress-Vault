package com.anuru.progressvault.ui.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.data.repository.SkillsRepository
import com.anuru.progressvault.domain.skills.Skill
import com.anuru.progressvault.domain.skills.SkillReview
import com.anuru.progressvault.domain.skills.SkillSource
import com.anuru.progressvault.domain.time.WeekCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class SkillFilter { ALL, RUSTY, NEW, LEETCODE, COURSES, MANUAL }

data class SkillsUiState(
    val isLoading: Boolean = true,
    val review: SkillReview = SkillReview(emptyList(), emptyList()),
    val filter: SkillFilter = SkillFilter.ALL,
    val today: LocalDate = LocalDate.now(),
) {
    val visible: List<Skill> get() = review.skills.filter {
        when (filter) {
            SkillFilter.ALL -> true
            SkillFilter.RUSTY -> it.isRusty
            SkillFilter.NEW -> it.isNew
            SkillFilter.LEETCODE -> SkillSource.LEETCODE in it.sources
            SkillFilter.COURSES -> SkillSource.COURSE in it.sources
            SkillFilter.MANUAL -> SkillSource.MANUAL in it.sources
        }
    }
}

/** Draft for the add / edit dialog. */
data class SkillDraft(
    val manualId: Long? = null,
    val name: String = "",
    val firstSeen: LocalDate,
    val lastPracticed: LocalDate? = null,
    val notes: String = "",
)

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val repository: SkillsRepository,
    private val weekCalculator: WeekCalculator,
) : ViewModel() {
    private val filter = MutableStateFlow(SkillFilter.ALL)

    val state: StateFlow<SkillsUiState> = combine(repository.observeReview(), filter) { review, f ->
        SkillsUiState(isLoading = false, review = review, filter = f, today = weekCalculator.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkillsUiState())

    fun setFilter(f: SkillFilter) { filter.value = f }

    fun newDraft() = SkillDraft(firstSeen = weekCalculator.today())

    fun save(d: SkillDraft) {
        if (d.name.isBlank()) return
        viewModelScope.launch {
            if (d.manualId == null) repository.addManual(d.name, d.firstSeen, d.lastPracticed, d.notes)
            else repository.updateManual(d.manualId, d.name, d.firstSeen, d.lastPracticed, d.notes)
        }
    }

    fun markPracticed(skill: Skill) {
        viewModelScope.launch { repository.markPracticed(skill.manualId, skill.name, skill.firstSeen) }
    }

    fun deleteManual(id: Long) {
        viewModelScope.launch { repository.deleteManual(id) }
    }
}
