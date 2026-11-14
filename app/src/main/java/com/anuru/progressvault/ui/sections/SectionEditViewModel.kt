package com.anuru.progressvault.ui.sections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anuru.progressvault.data.db.entity.SectionKind
import com.anuru.progressvault.data.repository.SectionDraft
import com.anuru.progressvault.data.repository.SectionRepository
import com.anuru.progressvault.domain.model.AccentPalette
import com.anuru.progressvault.ui.components.SectionIconKey
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.parseAmount
import com.anuru.progressvault.ui.navigation.SectionEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SectionEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val kind: SectionKind = SectionKind.STANDARD,
    val isBuiltIn: Boolean = false,
    val name: String = "",
    val unit: String = "",
    val targetText: String = "1",
    val weightText: String = "1",
    val iconKey: String = SectionIconKey.STAR,
    val accentColor: Long = AccentPalette.CYAN,
    val includeInDial: Boolean = true,
    val isHidden: Boolean = false,
    val showErrors: Boolean = false,
    /** Set once saved or deleted; the screen pops. */
    val done: Boolean = false,
) {
    val target: Double? get() = if (kind != SectionKind.STANDARD) 0.0 else parseAmount(targetText)?.takeIf { it >= 0 }
    val weight: Double? get() = parseAmount(weightText)?.takeIf { it >= 0 }
    val nameError: Boolean get() = name.isBlank()
    val unitError: Boolean get() = unit.isBlank()
    val isValid: Boolean get() = !nameError && !unitError && target != null && weight != null
    val canDelete: Boolean get() = !isNew && !isBuiltIn
}

@HiltViewModel
class SectionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SectionRepository,
) : ViewModel() {
    private val sectionId = savedStateHandle.toRoute<SectionEditRoute>().sectionId
    private val _state = MutableStateFlow(SectionEditUiState())
    val state: StateFlow<SectionEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (sectionId == 0L) {
                _state.update { it.copy(isLoading = false) }
            } else {
                val s = repository.observe(sectionId).first() ?: run { _state.update { it.copy(done = true) }; return@launch }
                _state.update {
                    it.copy(
                        isLoading = false, isNew = false, kind = s.kind, isBuiltIn = s.isBuiltIn,
                        name = s.name, unit = s.unit, targetText = formatAmount(s.defaultWeeklyTarget),
                        weightText = formatAmount(s.weight), iconKey = s.iconKey, accentColor = s.accentColor,
                        includeInDial = s.includeInDial, isHidden = s.isHidden,
                    )
                }
            }
        }
    }

    fun update(transform: (SectionEditUiState) -> SectionEditUiState) = _state.update(transform)

    fun save() {
        val s = _state.value
        if (!s.isValid) { _state.update { it.copy(showErrors = true) }; return }
        val draft = SectionDraft(
            name = s.name, iconKey = s.iconKey, accentColor = s.accentColor, unit = s.unit,
            defaultWeeklyTarget = s.target ?: 0.0, weight = s.weight ?: 1.0,
            includeInDial = s.includeInDial, isHidden = s.isHidden,
        )
        viewModelScope.launch {
            if (s.isNew) repository.create(draft) else repository.update(sectionId, draft)
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        if (!_state.value.canDelete) return
        viewModelScope.launch {
            repository.deleteCustom(sectionId)
            _state.update { it.copy(done = true) }
        }
    }
}
