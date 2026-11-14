package com.anuru.progressvault.ui.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.DeadlineType
import com.anuru.progressvault.domain.model.AccentPalette
import com.anuru.progressvault.domain.model.Module
import com.anuru.progressvault.domain.model.ModuleDraft
import com.anuru.progressvault.ui.components.ColorSwatchRow
import com.anuru.progressvault.ui.components.DateField
import com.anuru.progressvault.ui.components.VaultTextField
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.parseAmount
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import java.time.LocalDate

/** Add/edit a module. [defaultColor] is used for new modules. */
@Composable
fun ModuleEditorDialog(
    initial: ModuleDraft?,
    defaultColor: Long,
    onSave: (ModuleDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf(initial?.code.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var hours by remember { mutableStateOf(initial?.weeklyTargetHours?.let(::formatAmount).orEmpty()) }
    var credits by remember { mutableStateOf(initial?.credits?.toString().orEmpty()) }
    var color by remember { mutableStateOf(initial?.accentColor ?: defaultColor) }
    var showErrors by remember { mutableStateOf(false) }
    val hoursValue = parseAmount(hours)?.takeIf { it > 0 }
    val creditsValue = credits.trim().toIntOrNull()
    val creditsBad = credits.isNotBlank() && (creditsValue == null || creditsValue < 0)
    val nameMissing = code.isBlank() && name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.Surface,
        title = { Text(stringResource(if (initial == null) R.string.module_dialog_add else R.string.module_dialog_edit), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VaultTextField(code, { code = it }, stringResource(R.string.field_module_code), placeholder = stringResource(R.string.field_module_code_hint),
                    isError = showErrors && nameMissing, supportingText = if (showErrors && nameMissing) stringResource(R.string.error_required) else null)
                Spacer(Modifier.height(8.dp))
                VaultTextField(name, { name = it }, stringResource(R.string.field_module_name), placeholder = stringResource(R.string.field_module_name_hint))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultTextField(hours, { hours = it }, stringResource(R.string.field_hours_per_week), keyboardType = KeyboardType.Decimal,
                        isError = showErrors && hoursValue == null, supportingText = if (showErrors && hoursValue == null) stringResource(R.string.error_invalid_number) else null,
                        modifier = Modifier.weight(1f))
                    VaultTextField(credits, { credits = it }, stringResource(R.string.field_credits), placeholder = stringResource(R.string.field_optional),
                        keyboardType = KeyboardType.Number, isError = creditsBad, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.field_colour), style = MaterialTheme.typography.labelMedium, color = VaultColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                ColorSwatchRow(AccentPalette.all, color, { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showErrors = true
                if (!nameMissing && hoursValue != null && !creditsBad) {
                    onSave(ModuleDraft(code.trim(), name.trim().ifEmpty { code.trim() }, color, hoursValue, creditsValue))
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun deadlineTypeLabel(type: DeadlineType): String = stringResource(
    when (type) {
        DeadlineType.ASSIGNMENT -> R.string.deadline_assignment
        DeadlineType.EXAM -> R.string.deadline_exam
        DeadlineType.QUIZ -> R.string.deadline_quiz
        DeadlineType.PROJECT -> R.string.deadline_project
        DeadlineType.OTHER -> R.string.deadline_other
    },
)

data class DeadlineDraft(val moduleId: Long, val title: String, val type: DeadlineType, val date: LocalDate, val notes: String?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeadlineDialog(modules: List<Module>, today: LocalDate, onSave: (DeadlineDraft) -> Unit, onDismiss: () -> Unit) {
    var moduleId by remember { mutableStateOf(modules.singleOrNull()?.id) }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(DeadlineType.ASSIGNMENT) }
    var date by remember { mutableStateOf(today.plusDays(7)) }
    var notes by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.Surface,
        title = { Text(stringResource(R.string.deadline_add_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.log_pick_module), style = MaterialTheme.typography.labelMedium, color = VaultColors.TextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modules.forEach { m ->
                        val c = accentColor(m.accentColor)
                        FilterChip(
                            selected = moduleId == m.id, onClick = { moduleId = m.id },
                            label = { Text(m.code.ifBlank { m.name }) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.18f), selectedLabelColor = c),
                        )
                    }
                }
                if (showErrors && moduleId == null) Text(stringResource(R.string.error_pick_module), color = VaultColors.Error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                VaultTextField(title, { title = it }, stringResource(R.string.field_deadline_title), placeholder = stringResource(R.string.field_deadline_title_hint),
                    isError = showErrors && title.isBlank(), supportingText = if (showErrors && title.isBlank()) stringResource(R.string.error_required) else null)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeadlineType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(deadlineTypeLabel(t)) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                DateField(stringResource(R.string.field_due_date), date, { date = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                VaultTextField(notes, { notes = it }, stringResource(R.string.log_note), placeholder = stringResource(R.string.field_optional), singleLine = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showErrors = true
                val id = moduleId
                if (id != null && title.isNotBlank()) onSave(DeadlineDraft(id, title, type, date, notes))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
