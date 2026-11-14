package com.anuru.progressvault.ui.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anuru.progressvault.R
import com.anuru.progressvault.domain.skills.Skill
import com.anuru.progressvault.domain.skills.SkillSource
import com.anuru.progressvault.ui.components.ConfirmDialog
import com.anuru.progressvault.ui.components.DateField
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultSecondaryButton
import com.anuru.progressvault.ui.components.VaultTextField
import com.anuru.progressvault.ui.components.formatMedium
import com.anuru.progressvault.ui.theme.VaultColors
import java.time.LocalDate

@Composable
fun SkillDetailDialog(
    skill: Skill,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPracticed: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skill.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                StatusChip(skill.level.label(), skill.level.color())
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalLabel(stringResource(R.string.skill_sources))
                Text(skill.sources.sortedBy { it.ordinal }.map { it.label() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                Text(skillCountsText(skill), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextSecondary)
                if (skill.courseTitles.isNotEmpty()) {
                    TerminalLabel(stringResource(R.string.skill_courses_label))
                    skill.courseTitles.forEach { Text("› $it", style = MaterialTheme.typography.bodyMedium) }
                }
                TerminalLabel(stringResource(R.string.skill_timeline))
                Text(
                    stringResource(R.string.skill_first_seen, skill.firstSeen?.formatMedium() ?: stringResource(R.string.unknown)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        R.string.skill_last_practiced,
                        skill.lastPracticed?.let { relativeDays(java.time.temporal.ChronoUnit.DAYS.between(it, today)) } ?: stringResource(R.string.unknown),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                skill.notes?.let {
                    TerminalLabel(stringResource(R.string.field_notes))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(4.dp))
                VaultSecondaryButton(stringResource(R.string.action_practiced_today), onPracticed, Modifier.fillMaxWidth(), enabled = skill.lastPracticed != today)
                if (SkillSource.MANUAL !in skill.sources) {
                    Text(stringResource(R.string.skill_practiced_note), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = {
            if (skill.manualId != null) {
                Row {
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.action_delete), color = VaultColors.Error) }
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                }
            }
        },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.skill_delete_title),
            message = stringResource(
                if (skill.sources.size > 1) R.string.skill_delete_body_derived else R.string.skill_delete_body,
                skill.name,
            ),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun SkillEditDialog(initial: SkillDraft, onDismiss: () -> Unit, onSave: (SkillDraft) -> Unit) {
    var d by remember { mutableStateOf(initial) }
    var showErrors by remember { mutableStateOf(false) }
    val dateError = d.lastPracticed != null && d.lastPracticed!!.isBefore(d.firstSeen)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.Surface,
        title = {
            Text(
                stringResource(if (initial.manualId == null) R.string.skill_add_title else R.string.skill_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VaultTextField(
                    value = d.name,
                    onValueChange = { d = d.copy(name = it) },
                    label = stringResource(R.string.field_skill_name),
                    placeholder = stringResource(R.string.field_skill_name_hint),
                    isError = showErrors && d.name.isBlank(),
                    supportingText = if (showErrors && d.name.isBlank()) stringResource(R.string.error_required) else null,
                )
                DateField(stringResource(R.string.field_first_seen), d.firstSeen, { d = d.copy(firstSeen = it) })
                DateField(
                    stringResource(R.string.field_last_practiced),
                    d.lastPracticed,
                    { d = d.copy(lastPracticed = it) },
                    isError = dateError,
                    placeholder = stringResource(R.string.field_optional),
                )
                if (dateError) Text(stringResource(R.string.error_practiced_before_seen), style = MaterialTheme.typography.bodySmall, color = VaultColors.Error)
                VaultTextField(
                    value = d.notes,
                    onValueChange = { d = d.copy(notes = it) },
                    label = stringResource(R.string.field_notes),
                    singleLine = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showErrors = true
                if (d.name.isNotBlank() && !dateError) onSave(d)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
