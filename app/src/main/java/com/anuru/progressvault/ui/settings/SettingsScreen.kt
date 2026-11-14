package com.anuru.progressvault.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.anuru.progressvault.BuildConfig
import com.anuru.progressvault.R
import com.anuru.progressvault.data.backup.BackupOutcome
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.domain.model.Section
import com.anuru.progressvault.ui.components.ConfirmDialog
import com.anuru.progressvault.ui.components.SwitchRow
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultPrimaryButton
import com.anuru.progressvault.ui.components.VaultSecondaryButton
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.vaultSwitchColors
import com.anuru.progressvault.ui.connections.syncStatusText
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun SettingsScreen(
    onEditSection: (Long) -> Unit,
    onManageSections: () -> Unit,
    onConnections: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showTime by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { r ->
            snackbar.showSnackbar(
                when (r) {
                    is BackupOutcome.Exported -> resources.getQuantityString(R.plurals.backup_exported, r.rows, r.rows)
                    is BackupOutcome.Imported -> resources.getQuantityString(R.plurals.backup_imported, r.rows, r.rows)
                    is BackupOutcome.Failed -> resources.getString(
                        when (r.reason) {
                            BackupOutcome.Reason.NOT_A_BACKUP -> R.string.backup_error_not_backup
                            BackupOutcome.Reason.TOO_NEW -> R.string.backup_error_too_new
                            BackupOutcome.Reason.CORRUPT -> R.string.backup_error_corrupt
                            BackupOutcome.Reason.IO -> R.string.backup_error_io
                        },
                    )
                },
            )
        }
    }

    Scaffold(containerColor = VaultColors.Background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                TerminalLabel(stringResource(R.string.settings_label), color = VaultColors.Blue)
                Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            }
            item { NotificationPermissionCard() }

            item {
                Card(stringResource(R.string.settings_targets)) {
                    Text(stringResource(R.string.settings_targets_body), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted)
                }
            }
            items(state.sections, key = { it.id }) { s ->
                SectionTargetRow(
                    section = s,
                    share = state.dialShare(s),
                    onEdit = { onEditSection(s.id) },
                    onInclude = { viewModel.setInclude(s, it) },
                    onWeight = { viewModel.changeWeight(s, it) },
                )
            }
            item { VaultSecondaryButton(stringResource(R.string.action_manage_sections), onManageSections, Modifier.fillMaxWidth()) }

            item {
                Card(stringResource(R.string.settings_accounts)) {
                    AccountRow(stringResource(R.string.connections_leetcode), state.settings.leetCodeUsername, state.sync[SyncSource.LEETCODE])
                    AccountRow(stringResource(R.string.connections_github), state.settings.gitHubUsername, state.sync[SyncSource.GITHUB])
                    Text(
                        stringResource(R.string.settings_sync_every, state.settings.syncIntervalHours),
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultColors.TextMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VaultSecondaryButton(stringResource(R.string.action_connections), onConnections, Modifier.weight(1f))
                        VaultSecondaryButton(
                            stringResource(if (state.syncing) R.string.settings_syncing else R.string.action_sync_now),
                            { viewModel.syncNow() },
                            Modifier.weight(1f),
                            enabled = !state.syncing,
                        )
                    }
                }
            }

            item {
                Card(stringResource(R.string.settings_reminders)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_reminder_time), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.settings_reminder_time_body), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted)
                        }
                        TextButton(onClick = { showTime = true }) { Text(state.settings.reminderTime.format(timeFormat), style = MaterialTheme.typography.titleMedium) }
                    }
                    SwitchRow(stringResource(R.string.settings_daily), state.settings.dailyReminderEnabled, { viewModel.setDaily(it) }, subtitle = stringResource(R.string.settings_daily_body))
                    SwitchRow(stringResource(R.string.settings_pace), state.settings.paceNudgeEnabled, { viewModel.setPace(it) }, subtitle = stringResource(R.string.settings_pace_body))
                    SwitchRow(stringResource(R.string.settings_weekly), state.settings.weeklySummaryEnabled, { viewModel.setWeekly(it) }, subtitle = stringResource(R.string.settings_weekly_body))
                    SwitchRow(stringResource(R.string.settings_monthly), state.settings.monthlyRecapEnabled, { viewModel.setMonthly(it) }, subtitle = stringResource(R.string.settings_monthly_body))
                }
            }

            item {
                Card(stringResource(R.string.settings_backup)) {
                    Text(stringResource(R.string.settings_backup_body), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VaultPrimaryButton(
                            stringResource(R.string.action_export),
                            { exportLauncher.launch("progress-vault-${LocalDate.now()}.json") },
                            Modifier.weight(1f),
                            enabled = !state.backupBusy,
                        )
                        VaultSecondaryButton(stringResource(R.string.action_import), { confirmImport = true }, Modifier.weight(1f), enabled = !state.backupBusy)
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultColors.TextMuted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showTime) {
        ReminderTimeDialog(state.settings.reminderTime, onDismiss = { showTime = false }) {
            viewModel.setReminderTime(it); showTime = false
        }
    }
    if (confirmImport) {
        ConfirmDialog(
            title = stringResource(R.string.import_confirm_title),
            message = stringResource(R.string.import_confirm_body),
            confirmLabel = stringResource(R.string.action_choose_file),
            onConfirm = { confirmImport = false; importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onDismiss = { confirmImport = false },
        )
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    VaultCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TerminalLabel(title, color = VaultColors.Blue)
            content()
        }
    }
}

@Composable
private fun SectionTargetRow(section: Section, share: Double, onEdit: () -> Unit, onInclude: (Boolean) -> Unit, onWeight: (Double) -> Unit) {
    val accent = accentColor(section.accentColor)
    VaultCard(Modifier.fillMaxWidth(), accent = if (section.includeInDial) accent else null, onClick = onEdit, contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(section.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (section.isAcademics) stringResource(R.string.settings_target_academics)
                        else stringResource(R.string.settings_target_value, formatAmount(section.defaultWeeklyTarget), section.unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultColors.TextSecondary,
                    )
                }
                val includeDescription = stringResource(R.string.cd_include_in_dial, section.name)
                Switch(
                    checked = section.includeInDial,
                    onCheckedChange = onInclude,
                    enabled = section.defaultWeeklyTarget > 0 || section.isAcademics,
                    colors = vaultSwitchColors(accent),
                    modifier = Modifier.semantics { contentDescription = includeDescription },
                )
            }
            if (section.includeInDial) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_weight, formatAmount(section.weight), (share * 100).roundToInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onWeight(-0.5) }, enabled = section.weight > 0.5) {
                        Icon(Icons.Outlined.Remove, stringResource(R.string.cd_weight_down, section.name))
                    }
                    IconButton(onClick = { onWeight(0.5) }, enabled = section.weight < 5.0) {
                        Icon(Icons.Outlined.Add, stringResource(R.string.cd_weight_up, section.name))
                    }
                }
            } else {
                Text(
                    stringResource(if (section.defaultWeeklyTarget > 0 || section.isAcademics) R.string.settings_excluded else R.string.settings_no_target),
                    style = MaterialTheme.typography.labelMedium,
                    color = VaultColors.TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountRow(name: String, username: String, sync: com.anuru.progressvault.data.db.entity.SyncStateEntity?) {
    val (status, color) = syncStatusText(sync, username.isNotBlank())
    // Stacked so a long username and a long error never squeeze each other.
    Column(Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Text(username.ifBlank { stringResource(R.string.settings_not_set) }, style = MaterialTheme.typography.bodySmall, color = VaultColors.TextSecondary)
        Text(status, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Shows only when notifications are blocked; requests the permission or opens system settings. */
@Composable
private fun NotificationPermissionCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun enabled() = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    var granted by remember { mutableStateOf(enabled()) }
    // Re-check when returning from system settings.
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { granted = enabled() } }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok && enabled(); asked = true }
    if (granted) return
    VaultCard(Modifier.fillMaxWidth(), accent = VaultColors.Amber) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsOff, null, tint = VaultColors.Amber)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.notif_off_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.notif_off_body), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextSecondary)
            VaultPrimaryButton(
                stringResource(R.string.action_allow_notifications),
                {
                    val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (canAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
                Modifier.fillMaxWidth(),
                accent = VaultColors.Amber,
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun ReminderTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onSave: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initial.hour, initial.minute, is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.Surface,
        title = { Text(stringResource(R.string.settings_reminder_time), style = MaterialTheme.typography.titleLarge) },
        text = {
            TimePicker(
                state = picker,
                colors = TimePickerDefaults.colors(
                    clockDialColor = VaultColors.Background,
                    selectorColor = VaultColors.Cyan,
                    timeSelectorSelectedContainerColor = VaultColors.Cyan.copy(alpha = 0.2f),
                    timeSelectorSelectedContentColor = VaultColors.Cyan,
                    periodSelectorSelectedContainerColor = VaultColors.Cyan.copy(alpha = 0.2f),
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

