package com.anuru.progressvault.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anuru.progressvault.R
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.progress.ModuleProgress
import com.anuru.progressvault.domain.progress.SectionProgress
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultPrimaryButton
import com.anuru.progressvault.ui.components.VaultTextField
import com.anuru.progressvault.ui.components.parseAmount
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor

data class LogRequest(
    val amount: Double,
    val moduleId: Long?,
    val title: String?,
    val url: String?,
    val note: String?,
)

/** Bottom sheet for logging progress with details (amount, module, post title/URL, note). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogSheet(
    section: SectionProgress,
    modules: List<ModuleProgress>,
    onDismiss: () -> Unit,
    onLog: (LogRequest) -> Unit,
) {
    val accent = accentColor(section.accentColor)
    val isLinkedIn = section.builtInKey == BuiltInSectionKey.LINKEDIN
    var amountText by remember { mutableStateOf("1") }
    var moduleId by remember { mutableStateOf(modules.singleOrNull()?.moduleId) }
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    val amount = parseAmount(amountText)?.takeIf { it > 0 }
    val moduleMissing = section.isAcademics && moduleId == null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = VaultColors.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            TerminalLabel(stringResource(R.string.log_label), color = accent)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.log_title, section.name), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (section.isAcademics) {
                if (modules.isEmpty()) {
                    Text(stringResource(R.string.log_no_modules), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextMuted)
                } else {
                    Text(stringResource(R.string.log_pick_module), style = MaterialTheme.typography.labelMedium, color = VaultColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        modules.forEach { m ->
                            val c = accentColor(m.accentColor)
                            FilterChip(
                                selected = moduleId == m.moduleId,
                                onClick = { moduleId = m.moduleId },
                                label = { Text(m.code.ifBlank { m.name }) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = c.copy(alpha = 0.18f),
                                    selectedLabelColor = c,
                                ),
                            )
                        }
                    }
                    if (showErrors && moduleMissing) {
                        Text(stringResource(R.string.error_pick_module), style = MaterialTheme.typography.bodySmall, color = VaultColors.Error)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            VaultTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = stringResource(R.string.log_amount, section.unit),
                keyboardType = KeyboardType.Decimal,
                isError = showErrors && amount == null,
                supportingText = if (showErrors && amount == null) stringResource(R.string.error_invalid_number) else null,
            )
            if (isLinkedIn) {
                Spacer(Modifier.height(8.dp))
                VaultTextField(value = title, onValueChange = { title = it }, label = stringResource(R.string.log_post_topic), placeholder = stringResource(R.string.field_optional))
                Spacer(Modifier.height(8.dp))
                VaultTextField(value = url, onValueChange = { url = it }, label = stringResource(R.string.log_post_url), placeholder = stringResource(R.string.field_optional), keyboardType = KeyboardType.Uri)
            }
            Spacer(Modifier.height(8.dp))
            VaultTextField(value = note, onValueChange = { note = it }, label = stringResource(R.string.log_note), placeholder = stringResource(R.string.field_optional), singleLine = false)
            Spacer(Modifier.height(20.dp))
            VaultPrimaryButton(
                text = stringResource(R.string.action_log),
                accent = accent,
                enabled = !(section.isAcademics && modules.isEmpty()),
                onClick = {
                    showErrors = true
                    if (amount != null && !moduleMissing) {
                        onLog(LogRequest(amount, moduleId, title, url, note))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
