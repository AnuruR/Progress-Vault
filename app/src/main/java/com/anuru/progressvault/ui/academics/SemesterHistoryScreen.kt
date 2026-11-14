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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.ModuleDeadlineEntity
import com.anuru.progressvault.data.repository.AcademicsRepository
import com.anuru.progressvault.domain.model.Module
import com.anuru.progressvault.domain.model.Semester
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultTopBar
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.formatMedium
import com.anuru.progressvault.ui.navigation.SemesterHistoryRoute
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

data class SemesterHistoryState(
    val semester: Semester? = null,
    val modules: List<Module> = emptyList(),
    val hours: Map<Long, Double> = emptyMap(),
    val deadlines: List<ModuleDeadlineEntity> = emptyList(),
)

@HiltViewModel
class SemesterHistoryViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: AcademicsRepository) : ViewModel() {
    private val id = savedStateHandle.toRoute<SemesterHistoryRoute>().semesterId
    val state: StateFlow<SemesterHistoryState> = combine(
        repository.observeSemester(id), repository.observeModules(id), repository.observeModuleHours(), repository.observeDeadlines(id),
    ) { s, m, h, d -> SemesterHistoryState(s, m, h, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SemesterHistoryState())
}

/** Read-only view of an archived semester. */
@Composable
fun SemesterHistoryScreen(onBack: () -> Unit, viewModel: SemesterHistoryViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = VaultColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = { VaultTopBar(s.semester?.name.orEmpty(), onBack) },
    ) { padding ->
        val semester = s.semester ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                VaultCard(Modifier.fillMaxWidth(), accent = VaultColors.Purple) {
                    Column {
                        StatusChip(stringResource(R.string.chip_archived), VaultColors.TextMuted)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.date_range, semester.startDate.formatMedium(), semester.endDate.formatMedium()),
                            style = MaterialTheme.typography.labelMedium, color = VaultColors.TextSecondary)
                        val total = s.modules.sumOf { s.hours[it.id] ?: 0.0 }
                        Text(stringResource(R.string.semester_total_hours, formatAmount(total)), style = MaterialTheme.typography.headlineSmall, color = VaultColors.Purple)
                    }
                }
            }
            item { TerminalLabel(stringResource(R.string.onboarding_modules_label)) }
            items(s.modules, key = { it.id }) { m ->
                val c = accentColor(m.accentColor)
                VaultCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.code.ifBlank { m.name }, style = MaterialTheme.typography.titleSmall, color = c)
                            Text(m.name, style = MaterialTheme.typography.bodySmall, color = VaultColors.TextSecondary)
                        }
                        Text(stringResource(R.string.module_lifetime, formatAmount(s.hours[m.id] ?: 0.0)), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (s.deadlines.isNotEmpty()) {
                item { TerminalLabel(stringResource(R.string.academics_deadlines)) }
                items(s.deadlines, key = { "d${it.id}" }) { d ->
                    val m = s.modules.firstOrNull { it.id == d.moduleId }
                    Text(
                        stringResource(R.string.deadline_history_row, d.title, m?.code.orEmpty(), d.dueAt.atZone(ZoneId.systemDefault()).toLocalDate().formatMedium()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (d.isDone) VaultColors.TextSecondary else VaultColors.TextMuted,
                        textDecoration = if (d.isDone) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
    }
}
