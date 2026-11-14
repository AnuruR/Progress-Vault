package com.anuru.progressvault.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.data.db.entity.SectionKind
import com.anuru.progressvault.data.repository.SectionRepository
import com.anuru.progressvault.domain.model.AccentPalette
import com.anuru.progressvault.domain.model.ModuleDraft
import com.anuru.progressvault.domain.time.WeekCalculator
import com.anuru.progressvault.domain.usecase.CompleteOnboardingUseCase
import com.anuru.progressvault.domain.usecase.SemesterSetup
import com.anuru.progressvault.domain.usecase.TargetSetup
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.parseAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class OnboardingStep { WELCOME, ACCOUNTS, SEMESTER, MODULES, TARGETS }

/** Inline validation errors, resolved to strings in the UI. */
enum class FieldError { REQUIRED, INVALID_USERNAME, INVALID_NUMBER, END_BEFORE_START }

data class ModuleInput(
    val key: Long,
    val code: String = "",
    val name: String = "",
    val hoursText: String = "",
    val creditsText: String = "",
    val accentColor: Long = AccentPalette.CYAN,
) {
    val hours: Double? get() = parseAmount(hoursText)?.takeIf { it > 0 }
    val credits: Int? get() = creditsText.trim().toIntOrNull()
    val codeError: FieldError? get() = if (code.isBlank() && name.isBlank()) FieldError.REQUIRED else null
    val hoursError: FieldError? get() = if (hours == null) FieldError.INVALID_NUMBER else null
    val creditsError: FieldError? get() =
        if (creditsText.isNotBlank() && (credits == null || credits!! < 0)) FieldError.INVALID_NUMBER else null
    val isValid: Boolean get() = codeError == null && hoursError == null && creditsError == null
}

data class TargetInput(
    val sectionId: Long,
    val name: String,
    val unit: String,
    val iconKey: String,
    val accentColor: Long,
    val isAcademics: Boolean,
    val targetText: String,
    val includeInDial: Boolean,
) {
    val target: Double? get() = parseAmount(targetText)?.takeIf { it >= 0 }
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val leetCodeUsername: String = "",
    val gitHubUsername: String = "",
    val setUpSemester: Boolean = true,
    val semesterName: String = "",
    val semesterStart: LocalDate = LocalDate.now(),
    val semesterEnd: LocalDate = LocalDate.now().plusWeeks(16),
    val modules: List<ModuleInput> = emptyList(),
    val editingModule: ModuleInput? = null,
    val targets: List<TargetInput> = emptyList(),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
) {
    val leetCodeError: FieldError? get() = usernameError(leetCodeUsername, LEETCODE_USERNAME)
    val gitHubError: FieldError? get() = usernameError(gitHubUsername, GITHUB_USERNAME)
    val semesterNameError: FieldError? get() = if (setUpSemester && semesterName.isBlank()) FieldError.REQUIRED else null
    val semesterDateError: FieldError? get() =
        if (setUpSemester && semesterEnd.isBefore(semesterStart)) FieldError.END_BEFORE_START else null

    /** Academics' weekly target = sum of its modules' study hours. */
    val academicsTargetHours: Double get() = if (setUpSemester) modules.sumOf { it.hours ?: 0.0 } else 0.0

    fun canAdvance(): Boolean = when (step) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.ACCOUNTS -> leetCodeError == null && gitHubError == null
        OnboardingStep.SEMESTER -> semesterNameError == null && semesterDateError == null
        OnboardingStep.MODULES -> modules.all { it.isValid }
        OnboardingStep.TARGETS -> targets.filterNot { it.isAcademics }.all { it.target != null }
    }

    private companion object {
        val LEETCODE_USERNAME = com.anuru.progressvault.domain.sync.Usernames.LEETCODE
        val GITHUB_USERNAME = com.anuru.progressvault.domain.sync.Usernames.GITHUB

        fun usernameError(value: String, pattern: Regex): FieldError? =
            if (value.isNotBlank() && !pattern.matches(value.trim())) FieldError.INVALID_USERNAME else null
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sectionRepository: SectionRepository,
    private val completeOnboarding: CompleteOnboardingUseCase,
    weekCalculator: WeekCalculator,
) : ViewModel() {

    private val today = weekCalculator.today()
    private val _state = MutableStateFlow(
        OnboardingUiState(semesterStart = today, semesterEnd = today.plusWeeks(DEFAULT_SEMESTER_WEEKS)),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()
    private var nextModuleKey = 1L

    init {
        viewModelScope.launch {
            // Wait for the seeded sections, then load them once so user edits aren't overwritten.
            val sections = sectionRepository.observeAll().first { it.isNotEmpty() }
            _state.update { s ->
                s.copy(
                    targets = sections.filter { it.kind != SectionKind.DERIVED }.map {
                        TargetInput(
                            sectionId = it.id,
                            name = it.name,
                            unit = it.unit,
                            iconKey = it.iconKey,
                            accentColor = it.accentColor,
                            isAcademics = it.isAcademics,
                            targetText = formatAmount(it.defaultWeeklyTarget),
                            includeInDial = it.includeInDial,
                        )
                    },
                )
            }
        }
    }

    fun next() {
        val s = _state.value
        if (!s.canAdvance()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val nextStep = when (s.step) {
            OnboardingStep.WELCOME -> OnboardingStep.ACCOUNTS
            OnboardingStep.ACCOUNTS -> OnboardingStep.SEMESTER
            OnboardingStep.SEMESTER -> if (s.setUpSemester) OnboardingStep.MODULES else OnboardingStep.TARGETS
            OnboardingStep.MODULES -> OnboardingStep.TARGETS
            OnboardingStep.TARGETS -> null
        }
        if (nextStep == null) finish() else _state.update { it.copy(step = nextStep, showErrors = false) }
    }

    /** @return false when already on the first step (let the system handle back). */
    fun back(): Boolean {
        val s = _state.value
        val prev = when (s.step) {
            OnboardingStep.WELCOME -> return false
            OnboardingStep.ACCOUNTS -> OnboardingStep.WELCOME
            OnboardingStep.SEMESTER -> OnboardingStep.ACCOUNTS
            OnboardingStep.MODULES -> OnboardingStep.SEMESTER
            OnboardingStep.TARGETS -> if (s.setUpSemester) OnboardingStep.MODULES else OnboardingStep.SEMESTER
        }
        _state.update { it.copy(step = prev, showErrors = false) }
        return true
    }

    fun setLeetCodeUsername(v: String) = _state.update { it.copy(leetCodeUsername = v) }
    fun setGitHubUsername(v: String) = _state.update { it.copy(gitHubUsername = v) }
    fun setSetUpSemester(v: Boolean) = _state.update { it.copy(setUpSemester = v) }
    fun setSemesterName(v: String) = _state.update { it.copy(semesterName = v) }
    fun setSemesterStart(v: LocalDate) = _state.update {
        it.copy(semesterStart = v, semesterEnd = if (it.semesterEnd.isBefore(v)) v.plusWeeks(DEFAULT_SEMESTER_WEEKS) else it.semesterEnd)
    }
    fun setSemesterEnd(v: LocalDate) = _state.update { it.copy(semesterEnd = v) }

    fun startAddModule() = _state.update {
        it.copy(editingModule = ModuleInput(key = 0, accentColor = AccentPalette.forIndex(it.modules.size)))
    }
    fun startEditModule(key: Long) = _state.update { s -> s.copy(editingModule = s.modules.firstOrNull { it.key == key }) }
    fun updateEditingModule(transform: (ModuleInput) -> ModuleInput) =
        _state.update { s -> s.copy(editingModule = s.editingModule?.let(transform)) }
    fun cancelModuleEdit() = _state.update { it.copy(editingModule = null) }

    fun saveEditingModule() {
        val editing = _state.value.editingModule ?: return
        if (!editing.isValid) return
        _state.update { s ->
            val modules = if (editing.key == 0L) {
                s.modules + editing.copy(key = nextModuleKey++)
            } else {
                s.modules.map { if (it.key == editing.key) editing else it }
            }
            s.copy(modules = modules, editingModule = null)
        }
    }

    fun removeModule(key: Long) = _state.update { s -> s.copy(modules = s.modules.filterNot { it.key == key }) }

    fun setTargetText(sectionId: Long, text: String) = _state.update { s ->
        s.copy(targets = s.targets.map { t ->
            if (t.sectionId != sectionId) t
            else {
                val parsed = parseAmount(text)
                // A section with target 0 can't be in the dial; giving it a target re-enables it.
                val include = when {
                    parsed == null -> t.includeInDial
                    parsed <= 0.0 -> false
                    (t.target ?: 0.0) <= 0.0 -> true
                    else -> t.includeInDial
                }
                t.copy(targetText = text, includeInDial = include)
            }
        })
    }

    fun setIncludeInDial(sectionId: Long, include: Boolean) = _state.update { s ->
        s.copy(targets = s.targets.map { if (it.sectionId == sectionId) it.copy(includeInDial = include) else it })
    }

    private fun finish() {
        val s = _state.value
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            completeOnboarding(
                leetCodeUsername = s.leetCodeUsername,
                gitHubUsername = s.gitHubUsername,
                semester = if (s.setUpSemester) {
                    SemesterSetup(
                        name = s.semesterName,
                        startDate = s.semesterStart,
                        endDate = s.semesterEnd,
                        modules = s.modules.map {
                            ModuleDraft(
                                code = it.code,
                                name = it.name.ifBlank { it.code },
                                accentColor = it.accentColor,
                                weeklyTargetHours = it.hours ?: 0.0,
                                credits = it.credits,
                            )
                        },
                    )
                } else null,
                targets = s.targets.map { t ->
                    if (t.isAcademics) {
                        // Academics' target is derived from modules; it's in the dial when it has some.
                        TargetSetup(t.sectionId, target = null, includeInDial = t.includeInDial)
                    } else {
                        TargetSetup(t.sectionId, t.target ?: 0.0, t.includeInDial)
                    }
                },
            )
            // The app root observes the onboarding flag and switches to the main shell.
        }
    }

    private companion object {
        const val DEFAULT_SEMESTER_WEEKS = 16L
    }
}
