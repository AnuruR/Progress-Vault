package com.anuru.progressvault.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.SectionKind
import com.anuru.progressvault.domain.model.AccentPalette
import com.anuru.progressvault.ui.components.ColorSwatchRow
import com.anuru.progressvault.ui.components.ConfirmDialog
import com.anuru.progressvault.ui.components.SectionIcons
import com.anuru.progressvault.ui.components.SwitchRow
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultPrimaryButton
import com.anuru.progressvault.ui.components.VaultTextField
import com.anuru.progressvault.ui.components.VaultTopBar
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionEditScreen(onDone: () -> Unit, viewModel: SectionEditViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(s.done) { if (s.done) onDone() }
    val accent = accentColor(s.accentColor)

    Scaffold(
        containerColor = VaultColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            VaultTopBar(
                title = stringResource(if (s.isNew) R.string.section_new_title else R.string.section_edit_title),
                onBack = onDone,
            ) {
                if (s.canDelete) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_delete_item, s.name))
                    }
                }
            }
        },
    ) { padding ->
        if (s.isLoading) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            VaultTextField(
                value = s.name, onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = stringResource(R.string.field_section_name),
                isError = s.showErrors && s.nameError,
                supportingText = if (s.showErrors && s.nameError) stringResource(R.string.error_required) else null,
            )
            Spacer(Modifier.height(8.dp))
            VaultTextField(
                value = s.unit, onValueChange = { v -> viewModel.update { it.copy(unit = v) } },
                label = stringResource(R.string.field_unit),
                placeholder = stringResource(R.string.field_unit_hint),
                isError = s.showErrors && s.unitError,
                supportingText = if (s.showErrors && s.unitError) stringResource(R.string.error_required) else null,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.kind == SectionKind.STANDARD) {
                    VaultTextField(
                        value = s.targetText, onValueChange = { v -> viewModel.update { it.copy(targetText = v) } },
                        label = stringResource(R.string.field_weekly_target),
                        keyboardType = KeyboardType.Decimal,
                        isError = s.showErrors && s.target == null,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (s.kind != SectionKind.DERIVED) {
                    VaultTextField(
                        value = s.weightText, onValueChange = { v -> viewModel.update { it.copy(weightText = v) } },
                        label = stringResource(R.string.field_dial_weight),
                        keyboardType = KeyboardType.Decimal,
                        isError = s.showErrors && s.weight == null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            when (s.kind) {
                SectionKind.ACADEMICS -> Hint(stringResource(R.string.section_edit_academics_hint))
                SectionKind.DERIVED -> Hint(stringResource(R.string.section_edit_derived_hint))
                SectionKind.STANDARD -> Hint(stringResource(R.string.section_edit_weight_hint))
            }

            Spacer(Modifier.height(16.dp))
            TerminalLabel(stringResource(R.string.field_colour))
            Spacer(Modifier.height(8.dp))
            ColorSwatchRow(AccentPalette.all, s.accentColor, { c -> viewModel.update { it.copy(accentColor = c) } })

            Spacer(Modifier.height(16.dp))
            TerminalLabel(stringResource(R.string.field_icon))
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionIcons.pickerKeys.forEach { key ->
                    val selected = key == s.iconKey
                    val desc = stringResource(R.string.cd_icon_option, key)
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) accent.copy(alpha = 0.18f) else VaultColors.Surface)
                            .border(1.dp, if (selected) accent else VaultColors.Border, RoundedCornerShape(10.dp))
                            .clickable(role = Role.RadioButton) { viewModel.update { it.copy(iconKey = key) } }
                            .semantics { contentDescription = desc; this.selected = selected },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(SectionIcons.forKey(key), null, tint = if (selected) accent else VaultColors.TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            VaultCard(Modifier.fillMaxWidth()) {
                Column {
                    if (s.kind != SectionKind.DERIVED) {
                        val targetPositive = s.kind == SectionKind.ACADEMICS || (s.target ?: 0.0) > 0
                        SwitchRow(
                            title = stringResource(R.string.field_include_in_dial),
                            subtitle = if (!targetPositive) stringResource(R.string.include_needs_target) else null,
                            checked = s.includeInDial && targetPositive,
                            enabled = targetPositive,
                            onCheckedChange = { v -> viewModel.update { it.copy(includeInDial = v) } },
                            accent = accent,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    SwitchRow(
                        title = stringResource(R.string.field_hidden),
                        subtitle = stringResource(R.string.field_hidden_hint),
                        checked = s.isHidden,
                        onCheckedChange = { v -> viewModel.update { it.copy(isHidden = v) } },
                        accent = accent,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            VaultPrimaryButton(stringResource(R.string.action_save), viewModel::save, Modifier.fillMaxWidth(), accent = accent)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_section_title, s.name),
            message = stringResource(R.string.delete_section_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
}
