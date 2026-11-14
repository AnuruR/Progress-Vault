package com.anuru.progressvault.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.CoursePlatform
import com.anuru.progressvault.data.db.entity.CourseStatus
import com.anuru.progressvault.data.repository.CourseWithProgress
import com.anuru.progressvault.ui.components.ConfirmDialog
import com.anuru.progressvault.ui.components.DateField
import com.anuru.progressvault.ui.components.EmptyState
import com.anuru.progressvault.ui.components.GlowProgressBar
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultPrimaryButton
import com.anuru.progressvault.ui.components.VaultTextField
import com.anuru.progressvault.ui.components.VaultTopBar
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.theme.VaultColors
import kotlin.math.roundToInt

private val CourseAccent = VaultColors.Green

@Composable
fun platformLabel(p: CoursePlatform) = stringResource(
    when (p) {
        CoursePlatform.COURSERA -> R.string.platform_coursera
        CoursePlatform.UDEMY -> R.string.platform_udemy
        CoursePlatform.YOUTUBE -> R.string.platform_youtube
        CoursePlatform.OTHER -> R.string.platform_other
    },
)

@Composable
fun statusLabel(s: CourseStatus) = stringResource(
    when (s) {
        CourseStatus.IN_PROGRESS -> R.string.course_in_progress
        CourseStatus.COMPLETED -> R.string.course_completed
        CourseStatus.PAUSED -> R.string.course_paused
    },
)

@Composable
fun CoursesScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: CoursesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.logged.collect { event ->
            val result = snackbar.showSnackbar(
                message = resources.getString(R.string.snack_lesson_logged, event.courseTitle),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(event.logId)
        }
    }

    Scaffold(
        containerColor = VaultColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = { VaultTopBar(stringResource(R.string.section_courses), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0) }, containerColor = CourseAccent, contentColor = VaultColors.OnAccent,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.action_add_course), style = MaterialTheme.typography.labelLarge) },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        if (state.courses.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.courses_empty_title), stringResource(R.string.courses_empty_body), Modifier.padding(padding), accent = CourseAccent)
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.section?.let { s ->
                item {
                    VaultCard(Modifier.fillMaxWidth(), accent = CourseAccent) {
                        Column {
                            TerminalLabel(stringResource(R.string.dashboard_this_week), color = CourseAccent)
                            Text(stringResource(R.string.progress_of_target, formatAmount(s.achieved), formatAmount(s.target), s.unit), style = MaterialTheme.typography.titleLarge, color = CourseAccent)
                            Spacer(Modifier.height(6.dp))
                            GlowProgressBar(s.fraction.toFloat(), CourseAccent)
                        }
                    }
                }
            }
            items(state.courses, key = { it.course.id }) { CourseCard(it, onLesson = { viewModel.logLesson(it.course) }, onEdit = { onEdit(it.course.id) }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseCard(item: CourseWithProgress, onLesson: () -> Unit, onEdit: () -> Unit) {
    val c = item.course
    val uri = LocalUriHandler.current
    val statusColor = when (c.status) {
        CourseStatus.IN_PROGRESS -> VaultColors.Cyan
        CourseStatus.COMPLETED -> VaultColors.Green
        CourseStatus.PAUSED -> VaultColors.TextMuted
    }
    VaultCard(Modifier.fillMaxWidth(), accent = if (c.status == CourseStatus.COMPLETED) CourseAccent else null, onClick = onEdit) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(platformLabel(c.platform), VaultColors.TextSecondary)
                        StatusChip(statusLabel(c.status), statusColor)
                    }
                }
                c.url?.let { IconButton(onClick = { runCatching { uri.openUri(it) } }) { Icon(Icons.Outlined.Link, stringResource(R.string.cd_open_link), tint = VaultColors.TextSecondary) } }
                c.certificateUrl?.let { IconButton(onClick = { runCatching { uri.openUri(it) } }) { Icon(Icons.Outlined.WorkspacePremium, stringResource(R.string.cd_open_certificate), tint = VaultColors.Amber) } }
                if (c.status != CourseStatus.COMPLETED) {
                    TextButton(onClick = onLesson) { Text(stringResource(R.string.action_plus_lesson), color = CourseAccent) }
                }
            }
            Spacer(Modifier.height(8.dp))
            val fraction = item.fraction
            if (fraction != null) {
                GlowProgressBar(fraction.toFloat(), CourseAccent, height = 6.dp)
                Text(
                    pluralStringResource(R.plurals.course_lessons_of, c.totalLessons ?: 0, formatAmount(item.lessonsDone), c.totalLessons ?: 0, (fraction * 100).roundToInt()),
                    style = MaterialTheme.typography.labelSmall, color = VaultColors.TextSecondary,
                )
            } else {
                Text(stringResource(R.string.course_lessons_done, formatAmount(item.lessonsDone)), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextSecondary)
            }
            if (c.skillTags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    c.skillTags.forEach { StatusChip(it, VaultColors.Pink) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseEditScreen(onDone: () -> Unit, viewModel: CourseEditViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(s.done) { if (s.done) onDone() }
    Scaffold(
        containerColor = VaultColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            VaultTopBar(stringResource(if (s.original == null) R.string.course_new_title else R.string.course_edit_title), onDone) {
                if (s.original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.cd_delete_item, s.title)) }
            }
        },
    ) { padding ->
        if (s.isLoading) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            VaultTextField(s.title, { v -> viewModel.update { it.copy(title = v) } }, stringResource(R.string.field_course_title),
                isError = s.showErrors && s.title.isBlank(), supportingText = if (s.showErrors && s.title.isBlank()) stringResource(R.string.error_required) else null)
            Spacer(Modifier.height(12.dp))
            TerminalLabel(stringResource(R.string.field_platform))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CoursePlatform.entries.forEach { p ->
                    FilterChip(s.platform == p, { viewModel.update { it.copy(platform = p) } }, { Text(platformLabel(p)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CourseAccent.copy(alpha = 0.18f), selectedLabelColor = CourseAccent))
                }
            }
            Spacer(Modifier.height(8.dp))
            TerminalLabel(stringResource(R.string.field_status))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CourseStatus.entries.forEach { st ->
                    FilterChip(s.status == st, { viewModel.update { it.copy(status = st) } }, { Text(statusLabel(st)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CourseAccent.copy(alpha = 0.18f), selectedLabelColor = CourseAccent))
                }
            }
            Spacer(Modifier.height(8.dp))
            VaultTextField(s.url, { v -> viewModel.update { it.copy(url = v) } }, stringResource(R.string.field_course_url), placeholder = stringResource(R.string.field_optional), keyboardType = KeyboardType.Uri)
            Spacer(Modifier.height(8.dp))
            VaultTextField(s.totalLessonsText, { v -> viewModel.update { it.copy(totalLessonsText = v) } }, stringResource(R.string.field_total_lessons),
                placeholder = stringResource(R.string.field_optional), keyboardType = KeyboardType.Number,
                isError = s.totalLessonsError, supportingText = if (s.totalLessonsError) stringResource(R.string.error_invalid_number) else null)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(stringResource(R.string.field_start_date), s.startDate, { v -> viewModel.update { it.copy(startDate = v) } }, Modifier.weight(1f), placeholder = stringResource(R.string.field_not_set))
                DateField(stringResource(R.string.field_finish_date), s.finishDate, { v -> viewModel.update { it.copy(finishDate = v) } }, Modifier.weight(1f), placeholder = stringResource(R.string.field_not_set))
            }
            Spacer(Modifier.height(12.dp))
            TerminalLabel(stringResource(R.string.field_skill_tags))
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.tags.forEach { tag ->
                    InputChip(
                        selected = false, onClick = { viewModel.removeTag(tag) }, label = { Text(tag) },
                        trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.cd_remove_tag, tag)) },
                    )
                }
            }
            OutlinedTextField(
                value = s.tagInput,
                onValueChange = { v -> if (v.endsWith(",")) { viewModel.update { it.copy(tagInput = v) }; viewModel.commitTag() } else viewModel.update { it.copy(tagInput = v) } },
                label = { Text(stringResource(R.string.field_add_tag)) },
                placeholder = { Text(stringResource(R.string.field_add_tag_hint), color = VaultColors.TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.commitTag() }),
                trailingIcon = { IconButton(onClick = viewModel::commitTag) { Icon(Icons.Outlined.Add, stringResource(R.string.cd_add_tag)) } },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VaultColors.Cyan, unfocusedBorderColor = VaultColors.Border),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            VaultTextField(s.certificateUrl, { v -> viewModel.update { it.copy(certificateUrl = v) } }, stringResource(R.string.field_certificate_url), placeholder = stringResource(R.string.field_optional), keyboardType = KeyboardType.Uri)
            Spacer(Modifier.height(24.dp))
            VaultPrimaryButton(stringResource(R.string.action_save), viewModel::save, Modifier.fillMaxWidth(), accent = CourseAccent)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.delete_course_title, s.title), stringResource(R.string.delete_course_body), stringResource(R.string.action_delete),
            { confirmDelete = false; viewModel.delete() }, { confirmDelete = false })
    }
}
