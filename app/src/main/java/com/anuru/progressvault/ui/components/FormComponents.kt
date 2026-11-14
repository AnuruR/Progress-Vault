package com.anuru.progressvault.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anuru.progressvault.R
import com.anuru.progressvault.ui.theme.VaultColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = VaultColors.Background,
            navigationIconContentColor = VaultColors.TextPrimary,
            actionIconContentColor = VaultColors.TextSecondary,
            titleContentColor = VaultColors.TextPrimary,
        ),
    )
}

/** Tappable date card that opens a Material date picker. Null date shows [placeholder]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    placeholder: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val changeDescription = stringResource(R.string.cd_change_date, label)
    VaultCard(
        modifier = modifier.semantics { contentDescription = changeDescription },
        accent = if (isError) VaultColors.Error else null,
        onClick = { open = true },
        contentPadding = PaddingValues(12.dp),
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = VaultColors.Cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(date?.formatMedium() ?: placeholder.orEmpty(), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    if (open) {
        // DatePicker works in UTC millis; convert via UTC so the chosen day never shifts.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) VaultColors.Error else VaultColors.Cyan)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun vaultSwitchColors(accent: Color = VaultColors.Cyan) = SwitchDefaults.colors(
    checkedThumbColor = VaultColors.OnAccent,
    checkedTrackColor = accent,
    uncheckedThumbColor = VaultColors.TextMuted,
    uncheckedTrackColor = VaultColors.Background,
    uncheckedBorderColor = VaultColors.Border,
)

/** Label + description + switch row. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    accent: Color = VaultColors.Cyan,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) VaultColors.TextPrimary else VaultColors.TextMuted)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, colors = vaultSwitchColors(accent))
    }
}
