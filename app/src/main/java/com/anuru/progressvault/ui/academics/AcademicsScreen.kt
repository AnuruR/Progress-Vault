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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.ModuleDeadlineEntity
import com.anuru.progressvault.domain.model.AccentPalette
import com.anuru.progressvault.domain.model.Module
import com.anuru.progressvault.domain.model.ModuleDraft
import com.anuru.progressvault.domain.progress.ModuleProgress
import com.anuru.progressvault.ui.components.ConfirmDialog
import com.anuru.progressvault.ui.components.EmptyState
import com.anuru.progressvault.ui.components.GlowProgressBar
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultPrimaryButton
import com.anuru.progressvault.ui.components.VaultSecondaryButton
import com.anuru.progressvault.ui.components.VaultTopBar
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.formatMedium
import com.anuru.progressvault.ui.dashboard.LogSheet
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun AcademicsScreen(
    onBack: () -> Unit,
    onNewSemester: () -> Unit,
    onOpenSemester: (Long) -> Unit,
    viewModel: AcademicsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var editingModule by remember { mutableStateOf<Module?>(null) }
    var addingModule by remember { mutableStateOf(false) }
    var deletingModule by remember { mutableStateOf<Module?>(null) }
    var loggingModule by remember { mutableStateOf<ModuleProgress?>(null) }
    var addingDeadline by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == AcademicsEvent.ModuleInUse) snackbar.showSnackbar(resources.getString(R.string.module_in_use)) }
    }

    Scaffold(
        containerColor = VaultColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = { VaultTopBar(stringResource(R.string.section_academics), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        val semester = state.semester
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (semester == null) {
                item {
                    EmptyState(
                        Icons.Outlined.School,
                        stringResource(R.string.academics_empty_title),
                        stringResource(R.string.academics_empty_body),
                        Modifier.height(360.dp),
                        accent = VaultColors.Purple,
                    ) { VaultPrimaryButton(stringResource(R.string.action_start_semester), onNewSemester, accent = VaultColors.Purple) }
                }
            } else {
                item {
                    VaultCard(Modifier.fillMaxWidth(), accent = VaultColors.Purple) {
                        Column {
                            TerminalLabel(stringResource(R.string.academics_active_semester), color = VaultColors.Purple)
                            Text(semester.name, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                stringResource(R.string.date_range, semester.startDate.formatMedium(), semester.endDate.formatMedium()),
                                style = MaterialTheme.typography.labelMedium, color = VaultColors.TextSecondary,
                            )
                            state.semesterWeek?.let { (w, total) ->
                                Text(stringResource(R.string.semester_week_of, w, total), style = MaterialTheme.typography.labelLarge, color = VaultColors.Purple)
                            }
                            state.section?.let { s ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    stringResource(R.string.academics_this_week, formatAmount(s.achieved), formatAmount(s.target)),
                                    style = MaterialTheme.typography.labelMedium, color = VaultColors.TextSecondary,
                                )
                                GlowProgressBar(s.fraction.toFloat(), VaultColors.Purple)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)); TerminalLabel(stringResource(R.string.onboarding_modules_label)) }
                if (state.modules.isEmpty()) {
                    item { VaultCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.dashboard_no_modules), color = VaultColors.TextMuted) } }
                }
                items(state.modules, key = { it.id }) { module ->
                    val week = state.weekModules.firstOrNull { it.moduleId == module.id }
                    ModuleCard(
                        module = module,
                        week = week,
                        lifetimeHours = state.lifetimeHours[module.id] ?: 0.0,
                        onLog = { week?.let { loggingModule = it } },
                        onEdit = { editingModule = module },
                        onDelete = { deletingModule = module },
                    )
                }
                item {
                    VaultSecondaryButton(stringResource(R.string.action_add_module), { addingModule = true }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
                }

                item { Spacer(Modifier.height(8.dp)); TerminalLabel(stringResource(R.string.academics_deadlines)) }
                val upcoming = state.deadlines.filterNot { it.isDone }
                val done = state.deadlines.filter { it.isDone }
                if (upcoming.isEmpty()) {
                    item { VaultCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.academics_no_deadlines), color = VaultColors.TextMuted) } }
                }
                items(upcoming + done, key = { "d${it.id}" }) { d ->
                    DeadlineRow(d, state.modules.firstOrNull { it.id == d.moduleId }, state.today, viewModel::toggleDeadline, viewModel::deleteDeadline)
                }
                item {
                    VaultSecondaryButton(
                        stringResource(R.string.action_add_deadline), { addingDeadline = true }, Modifier.fillMaxWidth(),
                        enabled = state.modules.isNotEmpty(), icon = Icons.Outlined.Event,
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    VaultSecondaryButton(stringResource(R.string.action_start_new_semester), onNewSemester, Modifier.fillMaxWidth(), icon = Icons.Outlined.School)
                }
            }

            if (state.archived.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)); TerminalLabel(stringResource(R.string.academics_archived)) }
                items(state.archived, key = { "s${it.id}" }) { s ->
                    VaultCard(Modifier.fillMaxWidth(), onClick = { onOpenSemester(s.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.date_range, s.startDate.formatMedium(), s.endDate.formatMedium()),
                                    style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = VaultColors.TextMuted)
                        }
                    }
                }
            }
        }
    }

    if (addingModule) {
        ModuleEditorDialog(null, AccentPalette.forIndex(state.modules.size), { viewModel.addModule(it); addingModule = false }, { addingModule = false })
    }
    editingModule?.let { m ->
        ModuleEditorDialog(
            ModuleDraft(m.code, m.name, m.accentColor, m.weeklyTargetHours, m.credits), m.accentColor,
            { viewModel.updateModule(m, it); editingModule = null }, { editingModule = null },
        )
    }
    deletingModule?.let { m ->
        ConfirmDialog(
            stringResource(R.string.delete_module_title, m.code.ifBlank { m.name }), stringResource(R.string.delete_module_body),
            stringResource(R.string.action_delete), { viewModel.deleteModule(m); deletingModule = null }, { deletingModule = null },
        )
    }
    val section = state.section
    loggingModule?.let { m ->
        if (section != null) {
            LogSheet(section, listOf(m), { loggingModule = null }) {
                viewModel.logHours(section.sectionId, m.moduleId, it.amount, it.note)
                loggingModule = null
            }
        }
    }
    if (addingDeadline) {
        DeadlineDialog(state.modules, state.today, { viewModel.addDeadline(it); addingDeadline = false }, { addingDeadline = false })
    }
}

@Composable
private fun ModuleCard(
    module: Module,
    week: ModuleProgress?,
    lifetimeHours: Double,
    onLog: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = accentColor(module.accentColor)
    val label = module.code.ifBlank { module.name }
    VaultCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = c)
                    if (module.name != label) Text(module.name, style = MaterialTheme.typography.bodySmall, color = VaultColors.TextSecondary)
                }
                IconButton(onClick = onLog) { Icon(Icons.Outlined.Timer, stringResource(R.string.cd_log_hours, label), tint = c) }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.cd_edit_item, label), tint = VaultColors.TextMuted) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.cd_delete_item, label), tint = VaultColors.TextMuted) }
            }
            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlowProgressBar((week?.fraction ?: 0.0).toFloat(), c, Modifier.weight(1f), height = 6.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.module_hours_progress, formatAmount(week?.hours ?: 0.0), formatAmount(module.weeklyTargetHours)),
                    style = MaterialTheme.typography.labelSmall, color = VaultColors.TextSecondary,
                )
            }
            val credits = module.credits
            Text(
                if (credits != null) pluralStringResource(R.plurals.module_lifetime_with_credits, credits, formatAmount(lifetimeHours), credits)
                else stringResource(R.string.module_lifetime, formatAmount(lifetimeHours)),
                style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted,
            )
        }
    }
}

@Composable
fun countdownText(due: LocalDate, today: LocalDate): Pair<String, Color> {
    val days = ChronoUnit.DAYS.between(today, due).toInt()
    return when {
        days < 0 -> pluralStringResource(R.plurals.deadline_overdue, -days, -days) to VaultColors.Error
        days == 0 -> stringResource(R.string.deadline_today) to VaultColors.Amber
        days == 1 -> stringResource(R.string.deadline_tomorrow) to VaultColors.Amber
        else -> pluralStringResource(R.plurals.deadline_in_days, days, days) to if (days <= 7) VaultColors.Amber else VaultColors.Cyan
    }
}

@Composable
private fun DeadlineRow(
    d: ModuleDeadlineEntity,
    module: Module?,
    today: LocalDate,
    onToggle: (ModuleDeadlineEntity) -> Unit,
    onDelete: (ModuleDeadlineEntity) -> Unit,
) {
    val due = d.dueAt.atZone(ZoneId.systemDefault()).toLocalDate()
    val c = module?.let { accentColor(it.accentColor) } ?: VaultColors.TextSecondary
    VaultCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = d.isDone, onCheckedChange = { onToggle(d) },
                colors = CheckboxDefaults.colors(checkedColor = c, checkmarkColor = VaultColors.OnAccent, uncheckedColor = VaultColors.BorderStrong),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    d.title, style = MaterialTheme.typography.bodyLarge,
                    color = if (d.isDone) VaultColors.TextMuted else VaultColors.TextPrimary,
                    textDecoration = if (d.isDone) TextDecoration.LineThrough else null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.deadline_meta, module?.code?.ifBlank { module.name }.orEmpty(), deadlineTypeLabel(d.type), due.formatMedium()),
                        style = MaterialTheme.typography.labelSmall, color = c,
                    )
                }
                if (!d.isDone) {
                    val (text, color) = countdownText(due, today)
                    Spacer(Modifier.height(4.dp))
                    StatusChip(text, color)
                }
            }
            IconButton(onClick = { onDelete(d) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.cd_delete_item, d.title), tint = VaultColors.TextMuted) }
        }
    }
}
