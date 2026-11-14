package com.anuru.progressvault.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.data.db.entity.SyncStateEntity
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.progress.ModuleProgress
import com.anuru.progressvault.domain.progress.PaceStatus
import com.anuru.progressvault.domain.progress.SectionProgress
import com.anuru.progressvault.ui.components.AccentIcon
import com.anuru.progressvault.ui.components.ConfettiOverlay
import com.anuru.progressvault.ui.components.GlowProgressBar
import com.anuru.progressvault.ui.components.ProgressDial
import com.anuru.progressvault.ui.components.SectionIcons
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.formatDayMonth
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import kotlin.math.roundToInt

private data class Burst(val id: Long, val big: Boolean)

@Composable
fun DashboardScreen(
    onOpenSection: (SectionProgress) -> Unit,
    onManageSections: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var sheetSection by remember { mutableStateOf<SectionProgress?>(null) }
    var burst by remember { mutableStateOf<Burst?>(null) }
    var burstCounter by remember { mutableLongStateOf(0L) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DashboardEvent.Logged -> {
                    val result = snackbar.showSnackbar(
                        message = resources.getString(R.string.snack_logged, formatAmount(event.amount), event.sectionName),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.logId)
                }
                is DashboardEvent.SectionCompleted -> {
                    burst = Burst(++burstCounter, big = false)
                    snackbar.showSnackbar(resources.getString(R.string.snack_section_complete, event.sectionName))
                }
                DashboardEvent.DialCompleted -> {
                    burst = Burst(++burstCounter, big = true)
                    snackbar.showSnackbar(resources.getString(R.string.snack_dial_complete))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = VaultColors.Background,
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        ) { padding ->
            if (state.isLoading) {
                Box(Modifier.fillMaxSize().padding(padding))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { WeekSelector(state, viewModel) }
                    item { DialBlock(state) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TerminalLabel(stringResource(R.string.dashboard_sections_label), Modifier.weight(1f))
                            IconButton(onClick = onManageSections) {
                                Icon(Icons.Outlined.Tune, stringResource(R.string.manage_sections_title), tint = VaultColors.TextSecondary)
                            }
                        }
                    }
                    if (state.sections.isEmpty()) {
                        item {
                            VaultCard(Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.dashboard_no_sections), color = VaultColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    items(state.sections, key = { it.sectionId }) { section ->
                        SectionCard(
                            section = section,
                            modules = if (section.isAcademics) state.modules else emptyList(),
                            sync = syncFor(section, state.sync),
                            editable = state.isCurrentWeek && !state.isArchived,
                            onOpen = { onOpenSection(section) },
                            onQuickLog = {
                                if (section.isAcademics && state.modules.size != 1) {
                                    sheetSection = section
                                } else if (section.isAcademics) {
                                    viewModel.log(section, 1.0, moduleId = state.modules.single().moduleId)
                                } else {
                                    viewModel.quickLog(section)
                                }
                            },
                        )
                    }
                }
            }
        }
        burst?.let { b -> ConfettiOverlay(burstId = b.id, big = b.big, onFinished = { if (burst?.id == b.id) burst = null }) }
    }

    sheetSection?.let { section ->
        LogSheet(
            section = section,
            modules = state.modules,
            onDismiss = { sheetSection = null },
            onLog = { req ->
                viewModel.log(section, req.amount, req.moduleId, req.title, req.url, req.note)
                sheetSection = null
            },
        )
    }
}

private fun syncFor(section: SectionProgress, sync: Map<SyncSource, SyncStateEntity?>): SyncStateEntity? = when (section.builtInKey) {
    BuiltInSectionKey.LEETCODE -> sync[SyncSource.LEETCODE]
    BuiltInSectionKey.GITHUB -> sync[SyncSource.GITHUB]
    else -> null
}

@Composable
private fun WeekSelector(state: DashboardUiState, vm: DashboardViewModel) {
    val week = state.week ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::previousWeek) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.cd_previous_week), tint = VaultColors.TextSecondary)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            TerminalLabel(
                stringResource(if (state.isCurrentWeek) R.string.dashboard_this_week else R.string.dashboard_past_week),
                color = if (state.isCurrentWeek) VaultColors.Cyan else VaultColors.Amber,
            )
            Text(
                stringResource(R.string.week_range, week.start.formatDayMonth(), week.end.formatDayMonth()),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (state.isCurrentWeek) {
            Spacer(Modifier.width(48.dp))
        } else {
            IconButton(onClick = vm::nextWeek) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.cd_next_week), tint = VaultColors.TextSecondary)
            }
        }
    }
    if (!state.isCurrentWeek) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = VaultColors.TextMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (state.isArchived) R.string.dashboard_read_only_archived else R.string.dashboard_read_only_live),
                style = MaterialTheme.typography.labelSmall,
                color = VaultColors.TextMuted,
            )
            TextButton(onClick = vm::goToCurrentWeek) { Text(stringResource(R.string.action_this_week)) }
        }
    }
}

@Composable
private fun DialBlock(state: DashboardUiState) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressDial(progress = state.overall.toFloat(), label = stringResource(R.string.dial_label))
        if (!state.hasDialSections) {
            Text(
                stringResource(R.string.dashboard_no_dial_sections),
                style = MaterialTheme.typography.bodySmall,
                color = VaultColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (state.isCurrentWeek && state.pace != null) {
            Spacer(Modifier.height(4.dp))
            val (paceText, paceColor) = when (state.pace) {
                PaceStatus.AHEAD -> R.string.pace_ahead to VaultColors.Green
                PaceStatus.ON_PACE -> R.string.pace_on to VaultColors.Cyan
                PaceStatus.BEHIND -> R.string.pace_behind to VaultColors.Amber
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(pluralStringResource(R.plurals.days_left, state.daysLeft, state.daysLeft), VaultColors.TextSecondary)
                StatusChip(stringResource(paceText), paceColor)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    R.string.pace_detail,
                    (state.overall * 100).roundToInt(),
                    (state.elapsed * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = VaultColors.TextMuted,
            )
        }
    }
}

@Composable
private fun SectionCard(
    section: SectionProgress,
    modules: List<ModuleProgress>,
    sync: SyncStateEntity?,
    editable: Boolean,
    onOpen: () -> Unit,
    onQuickLog: () -> Unit,
) {
    val accent = accentColor(section.accentColor)
    VaultCard(
        modifier = Modifier.fillMaxWidth(),
        accent = if (section.isComplete) accent else null,
        onClick = if (editable) onOpen else null,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentIcon(SectionIcons.forKey(section.iconKey), accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(section.name, style = MaterialTheme.typography.titleMedium)
                        AnimatedVisibility(section.isComplete, enter = scaleIn() + fadeIn()) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = stringResource(R.string.cd_target_reached),
                                tint = accent,
                                modifier = Modifier.padding(start = 6.dp).size(16.dp),
                            )
                        }
                    }
                    Text(
                        if (section.target > 0) {
                            stringResource(R.string.progress_of_target, formatAmount(section.achieved), formatAmount(section.target), section.unit)
                        } else {
                            stringResource(R.string.progress_no_target, formatAmount(section.achieved), section.unit)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (section.isComplete) accent else VaultColors.TextSecondary,
                    )
                }
                if (editable) {
                    PlusOneButton(accent, section.name, onQuickLog)
                }
            }
            Spacer(Modifier.height(10.dp))
            GlowProgressBar(
                progress = section.fraction.toFloat(),
                color = accent,
                contentDescription = stringResource(R.string.cd_section_progress, section.name, (section.fraction * 100).roundToInt()),
            )
            if (section.isAcademics && modules.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                modules.forEach { ModuleRow(it) }
            }
            val chips = buildList<Pair<String, Color>> {
                if (section.isAutoSynced) add(syncChip(sync))
                if (!section.countsInDial) add(stringResource(R.string.chip_not_in_dial) to VaultColors.TextMuted)
                if (section.isAcademics && modules.isEmpty()) add(stringResource(R.string.section_academics_no_modules) to VaultColors.TextMuted)
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips.forEach { (t, c) -> StatusChip(t, c) } }
            }
        }
    }
}

@Composable
private fun syncChip(sync: SyncStateEntity?): Pair<String, Color> {
    val last = sync?.lastSuccessAt
    return when {
        sync?.lastError != null && (last == null || (sync.lastAttemptAt ?: last) > last) ->
            stringResource(R.string.chip_sync_error) to VaultColors.Error
        last != null -> stringResource(
            R.string.chip_synced_ago,
            if (System.currentTimeMillis() - last.toEpochMilli() < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.sync_just_now)
            else DateUtils.getRelativeTimeSpanString(last.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
        ) to VaultColors.Cyan
        else -> stringResource(R.string.chip_never_synced) to VaultColors.TextMuted
    }
}

@Composable
private fun ModuleRow(module: ModuleProgress) {
    val c = accentColor(module.accentColor)
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            module.code.ifBlank { module.name },
            style = MaterialTheme.typography.labelMedium,
            color = c,
            maxLines = 1,
            modifier = Modifier.width(84.dp),
        )
        GlowProgressBar(
            progress = module.fraction.toFloat(),
            color = c,
            height = 5.dp,
            modifier = Modifier.weight(1f),
            contentDescription = stringResource(R.string.cd_section_progress, module.code.ifBlank { module.name }, (module.fraction * 100).roundToInt()),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.module_hours_progress, formatAmount(module.hours), formatAmount(module.targetHours)),
            style = MaterialTheme.typography.labelSmall,
            color = VaultColors.TextSecondary,
        )
    }
}

@Composable
private fun PlusOneButton(accent: Color, sectionName: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.cd_quick_log, sectionName), tint = accent)
        }
    }
}

