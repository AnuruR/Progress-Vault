package com.anuru.progressvault.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.ActivityLogEntity
import com.anuru.progressvault.data.db.entity.LogSource
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.progress.SectionProgress
import com.anuru.progressvault.ui.components.AccentIcon
import com.anuru.progressvault.ui.components.ConfirmDialog
import com.anuru.progressvault.ui.components.EmptyState
import com.anuru.progressvault.ui.components.GlowProgressBar
import com.anuru.progressvault.ui.components.SectionIcons
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultPrimaryButton
import com.anuru.progressvault.ui.components.VaultSecondaryButton
import com.anuru.progressvault.ui.components.VaultTextField
import com.anuru.progressvault.ui.components.VaultTopBar
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.formatMedium
import com.anuru.progressvault.ui.components.parseAmount
import com.anuru.progressvault.ui.dashboard.LogSheet
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
fun SectionDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onConnections: () -> Unit,
    viewModel: SectionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val section = state.section
    val syncPanel by viewModel.syncPanel.collectAsStateWithLifecycle()
    var showLog by remember { mutableStateOf(false) }
    var showOverride by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ActivityLogEntity?>(null) }

    Scaffold(
        containerColor = VaultColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            VaultTopBar(title = section?.name.orEmpty(), onBack = onBack) {
                if (section != null) {
                    IconButton(onClick = { onEdit(section.id) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_edit_item, section.name))
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        if (section == null) {
            EmptyState(Icons.Outlined.Delete, stringResource(R.string.section_missing_title), stringResource(R.string.section_missing_body), Modifier.padding(padding))
            return@Scaffold
        }
        val accent = accentColor(section.accentColor)
        val isLinkedIn = section.builtInKey == BuiltInSectionKey.LINKEDIN
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.progress?.let { p ->
                item {
                    ThisWeekCard(
                        progress = p,
                        defaultTarget = section.defaultWeeklyTarget,
                        overridden = state.override != null,
                        canOverride = !section.isAcademics && !section.isDerived,
                        onOverride = { showOverride = true },
                    )
                }
            }
            if (syncPanel.source != null) {
                item { SyncPanel(syncPanel, accent, viewModel::syncNow, onConnections) }
            }
            if (section.isHidden) {
                item { StatusChip(stringResource(R.string.chip_hidden), VaultColors.TextMuted) }
            }
            if (!section.isDerived && !section.isAcademics) {
                item {
                    VaultPrimaryButton(
                        text = stringResource(if (isLinkedIn) R.string.action_add_post else R.string.action_log_progress),
                        onClick = { showLog = true },
                        accent = accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                TerminalLabel(stringResource(if (isLinkedIn) R.string.section_posts_label else R.string.section_history_label))
            }
            if (state.logs.isEmpty()) {
                item {
                    VaultCard(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(if (isLinkedIn) R.string.section_no_posts else R.string.section_no_logs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VaultColors.TextMuted,
                        )
                    }
                }
            }
            items(state.logs, key = { it.id }) { log ->
                LogRow(log, section.unit, accent, onDelete = { pendingDelete = log })
            }
        }
    }

    val p = state.progress
    if (showLog && p != null) {
        LogSheet(section = p, modules = emptyList(), onDismiss = { showLog = false }, onLog = {
            viewModel.log(it.amount, it.title, it.url, it.note)
            showLog = false
        })
    }
    if (showOverride && section != null) {
        OverrideDialog(
            current = state.progress?.target ?: section.defaultWeeklyTarget,
            defaultTarget = section.defaultWeeklyTarget,
            unit = section.unit,
            hasOverride = state.override != null,
            onSave = { viewModel.setOverride(it); showOverride = false },
            onClear = { viewModel.clearOverride(); showOverride = false },
            onDismiss = { showOverride = false },
        )
    }
    pendingDelete?.let { log ->
        ConfirmDialog(
            title = stringResource(R.string.delete_log_title),
            message = stringResource(R.string.delete_log_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteLog(log); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ThisWeekCard(
    progress: SectionProgress,
    defaultTarget: Double,
    overridden: Boolean,
    canOverride: Boolean,
    onOverride: () -> Unit,
) {
    val accent = accentColor(progress.accentColor)
    VaultCard(Modifier.fillMaxWidth(), accent = accent) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentIcon(SectionIcons.forKey(progress.iconKey), accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    TerminalLabel(stringResource(R.string.dashboard_this_week))
                    Text(
                        stringResource(R.string.progress_of_target, formatAmount(progress.achieved), formatAmount(progress.target), progress.unit),
                        style = MaterialTheme.typography.headlineSmall,
                        color = accent,
                    )
                }
                Text(stringResource(R.string.percent_value, (progress.fraction * 100).roundToInt()), style = MaterialTheme.typography.titleLarge, color = accent)
            }
            Spacer(Modifier.height(12.dp))
            GlowProgressBar(progress.fraction.toFloat(), accent)
            if (canOverride) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (overridden) stringResource(R.string.override_active, formatAmount(defaultTarget))
                        else stringResource(R.string.override_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overridden) VaultColors.Amber else VaultColors.TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOverride) { Text(stringResource(R.string.action_override_week)) }
                }
            }
        }
    }
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
private fun LogRow(log: ActivityLogEntity, unit: String, accent: androidx.compose.ui.graphics.Color, onDelete: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    VaultCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.log_amount_signed, formatAmount(log.amount)),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                modifier = Modifier.width(56.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(log.title ?: unit, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val zone = java.time.ZoneId.systemDefault()
                Text(
                    stringResource(R.string.log_when, log.localDate.formatMedium(), log.timestamp.atZone(zone).toLocalTime().format(timeFormat)),
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultColors.TextMuted,
                )
                log.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VaultColors.TextSecondary) }
                if (log.source != LogSource.MANUAL) {
                    Spacer(Modifier.height(4.dp))
                    StatusChip(stringResource(R.string.chip_imported), VaultColors.TextMuted)
                }
            }
            log.url?.let { url ->
                IconButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                    Icon(Icons.Outlined.Link, contentDescription = stringResource(R.string.cd_open_link), tint = accent)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_delete_item, log.title ?: unit), tint = VaultColors.TextMuted)
            }
        }
    }
}

@Composable
private fun OverrideDialog(
    current: Double,
    defaultTarget: Double,
    unit: String,
    hasOverride: Boolean,
    onSave: (Double) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(formatAmount(current)) }
    val value = parseAmount(text)?.takeIf { it >= 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.Surface,
        title = { Text(stringResource(R.string.override_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(stringResource(R.string.override_body, formatAmount(defaultTarget), unit), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextSecondary)
                Spacer(Modifier.height(12.dp))
                VaultTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.override_field, unit),
                    keyboardType = KeyboardType.Decimal,
                    isError = value == null,
                )
                if (hasOverride) {
                    Spacer(Modifier.height(8.dp))
                    VaultSecondaryButton(stringResource(R.string.action_reset_to_default), onClear, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

