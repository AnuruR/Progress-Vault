package com.anuru.progressvault.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.R
import com.anuru.progressvault.domain.model.AccentPalette
import com.anuru.progressvault.ui.components.AccentIcon
import com.anuru.progressvault.ui.components.ColorSwatchRow
import com.anuru.progressvault.ui.components.DateField
import com.anuru.progressvault.ui.components.vaultSwitchColors
import com.anuru.progressvault.ui.components.SectionIcons
import com.anuru.progressvault.ui.components.StepIndicator
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultPrimaryButton
import com.anuru.progressvault.ui.components.VaultSecondaryButton
import com.anuru.progressvault.ui.components.VaultTextField
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.formatMedium
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.step != OnboardingStep.WELCOME) { viewModel.back() }

    val steps = OnboardingStep.entries
    val stepIndex = steps.indexOf(state.step)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultColors.Background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        if (state.step != OnboardingStep.WELCOME) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                val progressText = stringResource(R.string.onboarding_step_of, stepIndex, steps.size - 1)
                StepIndicator(current = stepIndex - 1, total = steps.size - 1, contentDescription = progressText)
                Spacer(Modifier.height(8.dp))
                Text(progressText, style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
            }
        }

        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "onboardingStep",
        ) { step ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.ACCOUNTS -> AccountsStep(state, viewModel)
                    OnboardingStep.SEMESTER -> SemesterStep(state, viewModel)
                    OnboardingStep.MODULES -> ModulesStep(state, viewModel)
                    OnboardingStep.TARGETS -> TargetsStep(state, viewModel)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.step != OnboardingStep.WELCOME) {
                VaultSecondaryButton(
                    text = stringResource(R.string.action_back),
                    onClick = { viewModel.back() },
                    modifier = Modifier.weight(1f),
                )
            }
            val nextLabel = when (state.step) {
                OnboardingStep.WELCOME -> R.string.onboarding_get_started
                OnboardingStep.TARGETS -> R.string.onboarding_finish
                OnboardingStep.SEMESTER -> if (state.setUpSemester) R.string.action_next else R.string.onboarding_skip_semester
                else -> R.string.action_next
            }
            VaultPrimaryButton(
                text = stringResource(nextLabel),
                onClick = { viewModel.next() },
                enabled = !state.isSaving,
                modifier = Modifier.weight(if (state.step == OnboardingStep.WELCOME) 1f else 1.4f),
            )
        }
    }

    state.editingModule?.let { module ->
        ModuleEditorDialog(module = module, viewModel = viewModel)
    }
}

@Composable
private fun WelcomeStep() {
    val cursor = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by cursor.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Spacer(Modifier.height(48.dp))
    TerminalLabel(stringResource(R.string.onboarding_welcome_label), color = VaultColors.Cyan)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            stringResource(R.string.app_name_mono),
            style = MaterialTheme.typography.displaySmall,
            color = VaultColors.TextPrimary,
        )
        Text(
            stringResource(R.string.cursor_glyph),
            style = MaterialTheme.typography.displaySmall,
            color = VaultColors.Cyan,
            modifier = Modifier.alpha(cursorAlpha),
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
        color = VaultColors.TextSecondary,
    )
    Spacer(Modifier.height(32.dp))
    val features = listOf(
        Triple(R.string.onboarding_feature_track_title, R.string.onboarding_feature_track_body, VaultColors.Cyan),
        Triple(R.string.onboarding_feature_sync_title, R.string.onboarding_feature_sync_body, VaultColors.Amber),
        Triple(R.string.onboarding_feature_history_title, R.string.onboarding_feature_history_body, VaultColors.Purple),
    )
    features.forEach { (title, body, color) ->
        Row(Modifier.padding(vertical = 8.dp)) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = color)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun StepHeader(label: String, title: String, body: String) {
    TerminalLabel(label, color = VaultColors.Cyan)
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextSecondary)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun fieldErrorText(error: FieldError?): String? = when (error) {
    null -> null
    FieldError.REQUIRED -> stringResource(R.string.error_required)
    FieldError.INVALID_USERNAME -> stringResource(R.string.error_invalid_username)
    FieldError.INVALID_NUMBER -> stringResource(R.string.error_invalid_number)
    FieldError.END_BEFORE_START -> stringResource(R.string.error_end_before_start)
}

@Composable
private fun AccountsStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepHeader(
        label = stringResource(R.string.onboarding_accounts_label),
        title = stringResource(R.string.onboarding_accounts_title),
        body = stringResource(R.string.onboarding_accounts_body),
    )
    VaultTextField(
        value = state.leetCodeUsername,
        onValueChange = vm::setLeetCodeUsername,
        label = stringResource(R.string.field_leetcode_username),
        placeholder = stringResource(R.string.field_optional),
        leadingIcon = Icons.Outlined.Code,
        isError = state.leetCodeError != null,
        supportingText = fieldErrorText(state.leetCodeError),
    )
    Spacer(Modifier.height(12.dp))
    VaultTextField(
        value = state.gitHubUsername,
        onValueChange = vm::setGitHubUsername,
        label = stringResource(R.string.field_github_username),
        placeholder = stringResource(R.string.field_optional),
        leadingIcon = Icons.Outlined.Commit,
        isError = state.gitHubError != null,
        supportingText = fieldErrorText(state.gitHubError) ?: stringResource(R.string.onboarding_github_token_hint),
    )
}

@Composable
private fun SemesterStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepHeader(
        label = stringResource(R.string.onboarding_semester_label),
        title = stringResource(R.string.onboarding_semester_title),
        body = stringResource(R.string.onboarding_semester_body),
    )
    VaultCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIcon(Icons.Outlined.School, VaultColors.Purple)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.onboarding_semester_toggle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.setUpSemester,
                onCheckedChange = vm::setSetUpSemester,
                colors = vaultSwitchColors(),
            )
        }
    }
    if (state.setUpSemester) {
        Spacer(Modifier.height(16.dp))
        VaultTextField(
            value = state.semesterName,
            onValueChange = vm::setSemesterName,
            label = stringResource(R.string.field_semester_name),
            placeholder = stringResource(R.string.field_semester_name_hint),
            isError = state.showErrors && state.semesterNameError != null,
            supportingText = if (state.showErrors) fieldErrorText(state.semesterNameError) else null,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(
                label = stringResource(R.string.field_start_date),
                date = state.semesterStart,
                onDateChange = vm::setSemesterStart,
                modifier = Modifier.weight(1f),
            )
            DateField(
                label = stringResource(R.string.field_end_date),
                date = state.semesterEnd,
                onDateChange = vm::setSemesterEnd,
                isError = state.semesterDateError != null,
                modifier = Modifier.weight(1f),
            )
        }
        fieldErrorText(state.semesterDateError)?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = VaultColors.Error)
        }
    } else {
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_semester_skipped),
            style = MaterialTheme.typography.bodyMedium,
            color = VaultColors.TextMuted,
        )
    }
}

@Composable
private fun ModulesStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepHeader(
        label = stringResource(R.string.onboarding_modules_label),
        title = stringResource(R.string.onboarding_modules_title, state.semesterName.trim()),
        body = stringResource(R.string.onboarding_modules_body),
    )
    if (state.modules.isEmpty()) {
        VaultCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.onboarding_modules_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = VaultColors.TextMuted,
            )
        }
    }
    state.modules.forEach { module ->
        val accent = accentColor(module.accentColor)
        VaultCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), accent = accent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(module.code.ifBlank { module.name }, style = MaterialTheme.typography.titleMedium, color = accent)
                    if (module.code.isNotBlank() && module.name.isNotBlank()) {
                        Text(module.name, style = MaterialTheme.typography.bodyMedium)
                    }
                    val credits = module.credits
                    Text(
                        if (credits != null) {
                            pluralStringResource(R.plurals.module_summary_with_credits, credits, formatAmount(module.hours ?: 0.0), credits)
                        } else {
                            stringResource(R.string.module_summary, formatAmount(module.hours ?: 0.0))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = VaultColors.TextSecondary,
                    )
                }
                val label = module.code.ifBlank { module.name }
                IconButton(onClick = { vm.startEditModule(module.key) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_edit_item, label), tint = VaultColors.TextSecondary)
                }
                IconButton(onClick = { vm.removeModule(module.key) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_delete_item, label), tint = VaultColors.TextSecondary)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    VaultSecondaryButton(
        text = stringResource(R.string.action_add_module),
        onClick = vm::startAddModule,
        icon = Icons.Outlined.Add,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.modules.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_modules_total, formatAmount(state.academicsTargetHours)),
            style = MaterialTheme.typography.labelLarge,
            color = VaultColors.Purple,
        )
    }
}

@Composable
private fun ModuleEditorDialog(module: ModuleInput, viewModel: OnboardingViewModel) {
    var showErrors by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = viewModel::cancelModuleEdit,
        containerColor = VaultColors.Surface,
        title = {
            Text(
                stringResource(if (module.key == 0L) R.string.module_dialog_add else R.string.module_dialog_edit),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VaultTextField(
                    value = module.code,
                    onValueChange = { v -> viewModel.updateEditingModule { it.copy(code = v) } },
                    label = stringResource(R.string.field_module_code),
                    placeholder = stringResource(R.string.field_module_code_hint),
                    isError = showErrors && module.codeError != null,
                    supportingText = if (showErrors) fieldErrorText(module.codeError) else null,
                )
                Spacer(Modifier.height(8.dp))
                VaultTextField(
                    value = module.name,
                    onValueChange = { v -> viewModel.updateEditingModule { it.copy(name = v) } },
                    label = stringResource(R.string.field_module_name),
                    placeholder = stringResource(R.string.field_module_name_hint),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultTextField(
                        value = module.hoursText,
                        onValueChange = { v -> viewModel.updateEditingModule { it.copy(hoursText = v) } },
                        label = stringResource(R.string.field_hours_per_week),
                        keyboardType = KeyboardType.Decimal,
                        isError = showErrors && module.hoursError != null,
                        supportingText = if (showErrors) fieldErrorText(module.hoursError) else null,
                        modifier = Modifier.weight(1f),
                    )
                    VaultTextField(
                        value = module.creditsText,
                        onValueChange = { v -> viewModel.updateEditingModule { it.copy(creditsText = v) } },
                        label = stringResource(R.string.field_credits),
                        placeholder = stringResource(R.string.field_optional),
                        keyboardType = KeyboardType.Number,
                        isError = module.creditsError != null,
                        supportingText = fieldErrorText(module.creditsError),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.field_colour), style = MaterialTheme.typography.labelMedium, color = VaultColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                ColorSwatchRow(
                    colors = AccentPalette.all,
                    selected = module.accentColor,
                    onSelect = { c -> viewModel.updateEditingModule { it.copy(accentColor = c) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showErrors = true
                viewModel.saveEditingModule()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancelModuleEdit) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TargetsStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepHeader(
        label = stringResource(R.string.onboarding_targets_label),
        title = stringResource(R.string.onboarding_targets_title),
        body = stringResource(R.string.onboarding_targets_body),
    )
    state.targets.forEach { target ->
        val accent = accentColor(target.accentColor)
        VaultCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentIcon(SectionIcons.forKey(target.iconKey), accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(target.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (target.isAcademics) {
                            stringResource(R.string.onboarding_target_academics, formatAmount(state.academicsTargetHours))
                        } else {
                            stringResource(R.string.onboarding_target_unit, target.unit)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultColors.TextMuted,
                    )
                }
                if (!target.isAcademics) {
                    val invalid = state.showErrors && target.target == null
                    val targetDescription = stringResource(R.string.cd_weekly_target_for, target.name, target.unit)
                    OutlinedTextField(
                        value = target.targetText,
                        onValueChange = { vm.setTargetText(target.sectionId, it) },
                        singleLine = true,
                        isError = invalid,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.small,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = VaultColors.Border,
                            cursorColor = accent,
                        ),
                        modifier = Modifier
                            .width(76.dp)
                            .semantics { contentDescription = targetDescription },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                val dialDescription = stringResource(R.string.cd_include_in_dial, target.name)
                val targetValue = if (target.isAcademics) state.academicsTargetHours else (target.target ?: 0.0)
                Switch(
                    checked = target.includeInDial && targetValue > 0,
                    enabled = targetValue > 0,
                    onCheckedChange = { vm.setIncludeInDial(target.sectionId, it) },
                    colors = vaultSwitchColors(accent),
                    modifier = Modifier.semantics { contentDescription = dialDescription },
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.onboarding_targets_footer),
        style = MaterialTheme.typography.bodySmall,
        color = VaultColors.TextMuted,
    )
}
