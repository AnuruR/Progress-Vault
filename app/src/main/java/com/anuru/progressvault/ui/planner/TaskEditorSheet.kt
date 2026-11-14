package com.anuru.progressvault.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.ModuleDeadlineEntity
import com.anuru.progressvault.data.db.entity.PlannerTaskEntity
import com.anuru.progressvault.data.db.entity.TaskPriority
import com.anuru.progressvault.data.db.entity.TaskStatus
import com.anuru.progressvault.domain.model.Module
import com.anuru.progressvault.domain.model.Section
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultPrimaryButton
import com.anuru.progressvault.ui.components.VaultSecondaryButton
import com.anuru.progressvault.ui.components.VaultTextField
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun priorityLabel(p: TaskPriority) = stringResource(
    when (p) { TaskPriority.LOW -> R.string.priority_low; TaskPriority.MEDIUM -> R.string.priority_medium; TaskPriority.HIGH -> R.string.priority_high },
)

fun priorityColor(p: TaskPriority): Color = when (p) {
    TaskPriority.LOW -> VaultColors.TextMuted
    TaskPriority.MEDIUM -> VaultColors.Cyan
    TaskPriority.HIGH -> VaultColors.Pink
}

@Composable
fun statusLabel(s: TaskStatus) = stringResource(
    when (s) { TaskStatus.TODO -> R.string.status_todo; TaskStatus.DONE -> R.string.status_done; TaskStatus.SKIPPED -> R.string.status_skipped },
)

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

@Composable
private fun chipColors(c: Color) = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.18f), selectedLabelColor = c)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    initial: PlannerTaskEntity,
    sections: List<Section>,
    modules: List<Module>,
    deadlines: List<ModuleDeadlineEntity>,
    onSave: (PlannerTaskEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var sectionId by remember { mutableStateOf(initial.sectionId) }
    var moduleId by remember { mutableStateOf(initial.moduleId) }
    var deadlineId by remember { mutableStateOf(initial.deadlineId) }
    var day by remember { mutableStateOf(initial.day) }
    var priority by remember { mutableStateOf(initial.priority) }
    var status by remember { mutableStateOf(initial.status) }
    var notes by remember { mutableStateOf(initial.notes.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    val selectedSection = sections.firstOrNull { it.id == sectionId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = VaultColors.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding()) {
            Text(stringResource(if (initial.id == 0L) R.string.task_new else R.string.task_edit), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            VaultTextField(title, { title = it }, stringResource(R.string.field_task_title),
                isError = showErrors && title.isBlank(), supportingText = if (showErrors && title.isBlank()) stringResource(R.string.error_required) else null)

            Spacer(Modifier.height(12.dp))
            TerminalLabel(stringResource(R.string.field_section))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(sectionId == null, { sectionId = null; moduleId = null }, { Text(stringResource(R.string.task_no_section)) })
                sections.forEach { s ->
                    FilterChip(sectionId == s.id, { sectionId = s.id; if (!s.isAcademics) moduleId = null }, { Text(s.name) }, colors = chipColors(accentColor(s.accentColor)))
                }
            }
            if (selectedSection?.isAcademics == true && modules.isNotEmpty()) {
                TerminalLabel(stringResource(R.string.log_pick_module))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modules.forEach { m ->
                        FilterChip(moduleId == m.id, { moduleId = if (moduleId == m.id) null else m.id }, { Text(m.code.ifBlank { m.name }) }, colors = chipColors(accentColor(m.accentColor)))
                    }
                }
                val relevant = deadlines.filter { !it.isDone && (moduleId == null || it.moduleId == moduleId) }
                if (relevant.isNotEmpty()) {
                    TerminalLabel(stringResource(R.string.field_linked_deadline))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        relevant.forEach { d ->
                            FilterChip(deadlineId == d.id, { deadlineId = if (deadlineId == d.id) null else d.id }, { Text(d.title) }, colors = chipColors(VaultColors.Amber))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TerminalLabel(stringResource(R.string.field_day))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(day == null, { day = null }, { Text(stringResource(R.string.planner_unscheduled)) })
                DayOfWeek.entries.forEach { d -> FilterChip(day == d, { day = d }, { Text(d.shortName()) }, colors = chipColors(VaultColors.Cyan)) }
            }
            Spacer(Modifier.height(8.dp))
            TerminalLabel(stringResource(R.string.field_priority))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskPriority.entries.forEach { p -> FilterChip(priority == p, { priority = p }, { Text(priorityLabel(p)) }, colors = chipColors(priorityColor(p))) }
            }
            if (initial.id != 0L) {
                Spacer(Modifier.height(8.dp))
                TerminalLabel(stringResource(R.string.field_status))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskStatus.entries.forEach { s -> FilterChip(status == s, { status = s }, { Text(statusLabel(s)) }) }
                }
            }
            Spacer(Modifier.height(8.dp))
            VaultTextField(notes, { notes = it }, stringResource(R.string.log_note), placeholder = stringResource(R.string.field_optional), singleLine = false)
            Spacer(Modifier.height(20.dp))
            VaultPrimaryButton(stringResource(R.string.action_save), {
                showErrors = true
                if (title.isNotBlank()) {
                    onSave(
                        initial.copy(
                            title = title, sectionId = sectionId,
                            moduleId = if (selectedSection?.isAcademics == true) moduleId else null,
                            deadlineId = if (selectedSection?.isAcademics == true) deadlineId else null,
                            day = day, priority = priority, status = status, notes = notes,
                        ),
                    )
                }
            }, Modifier.fillMaxWidth())
            if (onDelete != null) {
                Spacer(Modifier.height(8.dp))
                VaultSecondaryButton(stringResource(R.string.action_delete), onDelete, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
