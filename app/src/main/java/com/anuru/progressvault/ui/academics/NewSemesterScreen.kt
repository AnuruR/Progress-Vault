package com.anuru.progressvault.ui.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.R
import com.anuru.progressvault.data.repository.AcademicsRepository
import com.anuru.progressvault.domain.model.AccentPalette
import com.anuru.progressvault.domain.model.ModuleDraft
import com.anuru.progressvault.domain.model.Semester
import com.anuru.progressvault.domain.time.WeekCalculator
import com.anuru.progressvault.ui.components.ConfirmDialog
import com.anuru.progressvault.ui.components.DateField
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultPrimaryButton
import com.anuru.progressvault.ui.components.VaultSecondaryButton
import com.anuru.progressvault.ui.components.VaultTextField
import com.anuru.progressvault.ui.components.VaultTopBar
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class NewSemesterState(
    val name: String = "",
    val start: LocalDate,
    val end: LocalDate,
    val modules: List<ModuleDraft> = emptyList(),
    val showErrors: Boolean = false,
    val saved: Boolean = false,
) {
    val datesValid: Boolean get() = !end.isBefore(start)
    val isValid: Boolean get() = name.isNotBlank() && datesValid
}

@HiltViewModel
class NewSemesterViewModel @Inject constructor(
    private val repository: AcademicsRepository,
    weekCalculator: WeekCalculator,
) : ViewModel() {
    private val today = weekCalculator.today()
    private val _state = MutableStateFlow(NewSemesterState(start = today, end = today.plusWeeks(16)))
    val state: StateFlow<NewSemesterState> = _state.asStateFlow()
    val activeSemester: StateFlow<Semester?> = repository.observeActiveSemester()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(t: (NewSemesterState) -> NewSemesterState) = _state.update(t)

    fun save() {
        val s = _state.value
        if (!s.isValid) { _state.update { it.copy(showErrors = true) }; return }
        viewModelScope.launch {
            repository.startSemester(s.name, s.start, s.end, s.modules)
            _state.update { it.copy(saved = true) }
        }
    }
}

@Composable
fun NewSemesterScreen(onDone: () -> Unit, viewModel: NewSemesterViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.activeSemester.collectAsStateWithLifecycle()
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(s.saved) { if (s.saved) onDone() }

    Scaffold(
        containerColor = VaultColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = { VaultTopBar(stringResource(R.string.action_start_new_semester), onDone) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            active?.let {
                Text(stringResource(R.string.new_semester_archive_note, it.name), style = MaterialTheme.typography.bodyMedium, color = VaultColors.Amber)
                Spacer(Modifier.height(16.dp))
            }
            VaultTextField(
                s.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.field_semester_name),
                placeholder = stringResource(R.string.field_semester_name_hint),
                isError = s.showErrors && s.name.isBlank(),
                supportingText = if (s.showErrors && s.name.isBlank()) stringResource(R.string.error_required) else null,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(stringResource(R.string.field_start_date), s.start, { v -> viewModel.update { it.copy(start = v) } }, Modifier.weight(1f))
                DateField(stringResource(R.string.field_end_date), s.end, { v -> viewModel.update { it.copy(end = v) } }, Modifier.weight(1f), isError = !s.datesValid)
            }
            if (!s.datesValid) Text(stringResource(R.string.error_end_before_start), color = VaultColors.Error, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(20.dp))
            TerminalLabel(stringResource(R.string.onboarding_modules_label))
            Spacer(Modifier.height(8.dp))
            s.modules.forEachIndexed { index, m ->
                val c = accentColor(m.accentColor)
                VaultCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.code.ifBlank { m.name }, style = MaterialTheme.typography.titleSmall, color = c)
                            Text(stringResource(R.string.module_summary, formatAmount(m.weeklyTargetHours)), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
                        }
                        IconButton(onClick = { editingIndex = index }) { Icon(Icons.Outlined.Edit, stringResource(R.string.cd_edit_item, m.code), tint = VaultColors.TextMuted) }
                        IconButton(onClick = { viewModel.update { st -> st.copy(modules = st.modules.filterIndexed { i, _ -> i != index }) } }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.cd_delete_item, m.code), tint = VaultColors.TextMuted)
                        }
                    }
                }
            }
            VaultSecondaryButton(stringResource(R.string.action_add_module), { adding = true }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
            Spacer(Modifier.height(24.dp))
            VaultPrimaryButton(
                stringResource(R.string.action_start_semester),
                { if (active != null && s.isValid) confirm = true else viewModel.save() },
                Modifier.fillMaxWidth(), accent = VaultColors.Purple,
            )
        }
    }
    if (adding) {
        ModuleEditorDialog(null, AccentPalette.forIndex(s.modules.size), { d -> viewModel.update { it.copy(modules = it.modules + d) }; adding = false }, { adding = false })
    }
    editingIndex?.let { i ->
        ModuleEditorDialog(s.modules[i], s.modules[i].accentColor, { d ->
            viewModel.update { st -> st.copy(modules = st.modules.mapIndexed { j, m -> if (j == i) d else m }) }; editingIndex = null
        }, { editingIndex = null })
    }
    if (confirm) {
        ConfirmDialog(
            stringResource(R.string.new_semester_confirm_title), stringResource(R.string.new_semester_archive_note, active?.name.orEmpty()),
            stringResource(R.string.action_start_semester), { confirm = false; viewModel.save() }, { confirm = false }, destructive = false,
        )
    }
}
