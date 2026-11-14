package com.anuru.progressvault.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.PlannerTaskEntity
import com.anuru.progressvault.data.db.entity.TaskStatus
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultSecondaryButton
import com.anuru.progressvault.ui.components.formatDayMonth
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import java.time.DayOfWeek

@Composable
fun PlannerScreen(viewModel: PlannerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preview by viewModel.balancePreview.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlannerTaskEntity?>(null) }
    val drag = rememberPlannerDragState { id, zone ->
        when (zone) {
            is DropZone.Day -> viewModel.moveToDay(id, zone.day)
            DropZone.NextWeek -> viewModel.moveToNextWeek(id)
        }
    }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var rootWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootOffset = it.positionInRoot(); rootWidth = it.size.width }) {
        Scaffold(
            containerColor = VaultColors.Background,
            contentWindowInsets = WindowInsets(0),
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { editing = viewModel.newTaskTemplate(null) },
                    containerColor = VaultColors.Green, contentColor = VaultColors.OnAccent,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.action_add_task), style = MaterialTheme.typography.labelLarge) },
                )
            },
        ) { padding ->
            if (state.isLoading) return@Scaffold
            val week = state.week ?: return@Scaffold
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previousWeek) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.cd_previous_week), tint = VaultColors.TextSecondary) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        TerminalLabel(
                            stringResource(if (state.isCurrentWeek) R.string.dashboard_this_week else R.string.planner_label),
                            color = if (state.isCurrentWeek) VaultColors.Green else VaultColors.TextMuted,
                        )
                        Text(stringResource(R.string.week_range, week.start.formatDayMonth(), week.end.formatDayMonth()), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.planner_done_count, state.doneTasks, state.totalTasks), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
                    }
                    IconButton(onClick = viewModel::nextWeek) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.cd_next_week), tint = VaultColors.TextSecondary) }
                }
                if (!state.isCurrentWeek) {
                    TextButton(onClick = viewModel::goToCurrentWeek, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.action_this_week)) }
                }

                DropStrip(state, drag)

                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = !drag.isDragging,
                ) {
                    if (state.pendingRollover > 0 && state.isCurrentWeek) {
                        item(key = "rollover") { RolloverBanner(state.pendingRollover, viewModel::rollOverPrevious) }
                    }
                    if (state.isPastWeek && state.todoTasks > 0) {
                        item(key = "move-past") {
                            VaultCard(Modifier.fillMaxWidth(), accent = VaultColors.Amber) {
                                Column {
                                    Text(pluralStringResource(R.plurals.past_week_unfinished, state.todoTasks, state.todoTasks), style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = viewModel::moveUnfinishedToThisWeek, modifier = Modifier.align(Alignment.End)) {
                                        Text(stringResource(R.string.action_move_to_this_week), color = VaultColors.Amber)
                                    }
                                }
                            }
                        }
                    }
                    if (!state.isPastWeek && state.todoTasks > 0) {
                        item(key = "balance") {
                            VaultSecondaryButton(stringResource(R.string.action_auto_balance), viewModel::previewBalance, Modifier.fillMaxWidth(), icon = Icons.Outlined.AutoFixHigh)
                        }
                    }
                    if (state.totalTasks == 0) {
                        item(key = "empty") {
                            VaultCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.planner_empty), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextMuted) }
                        }
                    } else {
                        item(key = "drag-hint") { Text(stringResource(R.string.planner_drag_hint), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted) }
                    }
                    state.groups.forEach { group ->
                        if (group.day == null && group.tasks.isEmpty()) return@forEach
                        item(key = "h-${group.day}") {
                            DayHeader(group, state, drag, onAdd = { editing = viewModel.newTaskTemplate(group.day) })
                        }
                        items(group.tasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task, state = state, drag = drag, day = group.day,
                                onToggle = { viewModel.toggleDone(task) },
                                onOpen = { editing = task },
                                onMove = { zone ->
                                    when (zone) {
                                        is DropZone.Day -> viewModel.moveToDay(task.id, zone.day)
                                        DropZone.NextWeek -> viewModel.moveToNextWeek(task.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Floating copy of the task under the finger while dragging.
        drag.dragged?.let { task ->
            val density = LocalDensity.current
            val local = drag.position - rootOffset
            Box(
                Modifier
                    .offset {
                        val w = 240.dp.toPx(); val maxX = (rootWidth - w).coerceAtLeast(0f)
                        IntOffset((local.x - 120.dp.toPx()).coerceIn(0f, maxX).roundToInt(), (local.y - 28.dp.toPx()).roundToInt())
                    }
                    .width(240.dp)
                    .zIndex(10f)
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(VaultColors.SurfaceRaised)
                    .border(1.dp, VaultColors.Green, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Column {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(
                        drag.hovered?.let { zoneLabel(it) } ?: stringResource(R.string.planner_drop_somewhere),
                        style = MaterialTheme.typography.labelSmall, color = VaultColors.Green,
                    )
                }
            }
        }
    }

    editing?.let { task ->
        TaskEditorSheet(
            initial = task, sections = state.sections, modules = state.modules, deadlines = state.deadlines,
            onSave = { viewModel.save(it); editing = null },
            onDelete = if (task.id != 0L) ({ viewModel.delete(task); editing = null }) else null,
            onDismiss = { editing = null },
        )
    }
    preview?.let { moves ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBalance,
            containerColor = VaultColors.Surface,
            title = { Text(stringResource(R.string.balance_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                if (moves.isEmpty()) {
                    Text(stringResource(R.string.balance_nothing), color = VaultColors.TextSecondary)
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.balance_body), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted)
                        Spacer(Modifier.height(8.dp))
                        moves.forEach { m ->
                            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(
                                    stringResource(R.string.balance_move, m.move.from?.shortName() ?: stringResource(R.string.planner_unscheduled_short), m.move.to.shortName()),
                                    style = MaterialTheme.typography.labelMedium, color = VaultColors.Cyan,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (moves.isNotEmpty()) TextButton(onClick = viewModel::applyBalance) { Text(stringResource(R.string.action_apply)) }
                else TextButton(onClick = viewModel::dismissBalance) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { if (moves.isNotEmpty()) TextButton(onClick = viewModel::dismissBalance) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun zoneLabel(zone: DropZone): String = when (zone) {
    DropZone.NextWeek -> stringResource(R.string.cd_move_to_next_week)
    is DropZone.Day -> stringResource(R.string.cd_move_to, zone.day?.shortName() ?: stringResource(R.string.planner_unscheduled))
}

@Composable
private fun DropStrip(state: PlannerUiState, drag: PlannerDragState) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), enabled = !drag.isDragging).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.groups.filter { it.day != null }.forEach { g ->
            DropChip(
                label = g.day!!.shortName(), count = g.tasks.count { it.status == TaskStatus.TODO },
                color = if (g.date == state.today) VaultColors.Green else VaultColors.Cyan,
                drag = drag, zone = DropZone.Day(g.day),
            )
        }
        DropChip(stringResource(R.string.planner_next_week), null, VaultColors.Amber, drag, DropZone.NextWeek)
    }
}

@Composable
private fun DropChip(label: String, count: Int?, color: Color, drag: PlannerDragState, zone: DropZone) {
    val hover = drag.hovered == zone
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .dropZone(drag, "strip-$zone", zone)
            .clip(shape)
            .background(if (hover) color.copy(alpha = 0.3f) else VaultColors.Surface)
            .border(if (hover) 2.dp else 1.dp, if (hover || drag.isDragging) color else VaultColors.Border, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
        if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
    }
}

@Composable
private fun DayHeader(group: DayGroup, state: PlannerUiState, drag: PlannerDragState, onAdd: () -> Unit) {
    val zone = DropZone.Day(group.day)
    val hover = drag.hovered == zone
    val isToday = group.date == state.today
    val color = when {
        group.day == null -> VaultColors.Amber
        isToday -> VaultColors.Green
        group.date != null && group.date.isBefore(state.today) -> VaultColors.TextMuted
        else -> VaultColors.TextSecondary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .dropZone(drag, "header-${group.day}", zone)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hover) color.copy(alpha = 0.15f) else Color.Transparent)
            .padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (group.day == null) stringResource(R.string.planner_unscheduled).uppercase()
            else stringResource(R.string.planner_day_header, group.day.shortName().uppercase(), group.date!!.formatDayMonth()),
            style = MaterialTheme.typography.titleSmall, color = color,
            modifier = Modifier.semantics { heading() },
        )
        if (isToday) { Spacer(Modifier.width(8.dp)); StatusChip(stringResource(R.string.planner_today), VaultColors.Green) }
        Spacer(Modifier.weight(1f))
        if (group.tasks.isEmpty()) Text(stringResource(R.string.planner_free), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
        IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, stringResource(R.string.cd_add_task_to, group.day?.shortName() ?: stringResource(R.string.planner_unscheduled)), tint = VaultColors.TextMuted) }
    }
}

@Composable
private fun TaskRow(
    task: PlannerTaskEntity,
    state: PlannerUiState,
    drag: PlannerDragState,
    day: DayOfWeek?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onMove: (DropZone) -> Unit,
) {
    val section = state.sections.firstOrNull { it.id == task.sectionId }
    val module = state.modules.firstOrNull { it.id == task.moduleId }
    val accent = section?.let { accentColor(it.accentColor) } ?: VaultColors.TextSecondary
    val done = task.status == TaskStatus.DONE
    val skipped = task.status == TaskStatus.SKIPPED
    val isDragged = drag.dragged?.id == task.id
    val nextWeekLabel = stringResource(R.string.cd_move_to_next_week)
    val moveLabels = DayOfWeek.entries.filter { it != task.day }.map { it to stringResource(R.string.cd_move_to, it.shortName()) }

    VaultCard(
        modifier = Modifier
            .fillMaxWidth()
            // Dropping onto any task of a day drops into that day.
            .dropZone(drag, "task-${task.id}", DropZone.Day(day))
            .alpha(if (isDragged) 0.35f else 1f)
            .semantics {
                customActions = moveLabels.map { (d, label) -> CustomAccessibilityAction(label) { onMove(DropZone.Day(d)); true } } +
                    CustomAccessibilityAction(nextWeekLabel) { onMove(DropZone.NextWeek); true }
            },
        onClick = onOpen,
        contentPadding = PaddingValues(0.dp),
    ) {
        // The gesture lives on the innermost content so the long-press isn't swallowed by the card's click.
        Row(
            Modifier.fillMaxWidth().dragTask(drag, task).padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).height(40.dp).background(priorityColor(task.priority)))
            Checkbox(
                checked = done, onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = accent, checkmarkColor = VaultColors.OnAccent, uncheckedColor = VaultColors.BorderStrong),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    task.title, style = MaterialTheme.typography.bodyLarge,
                    color = if (done || skipped) VaultColors.TextMuted else VaultColors.TextPrimary,
                    textDecoration = if (done || skipped) TextDecoration.LineThrough else null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    section?.let { Text(it.name, style = MaterialTheme.typography.labelSmall, color = accent) }
                    module?.let { Text(it.code.ifBlank { it.name }, style = MaterialTheme.typography.labelSmall, color = accentColor(it.accentColor)) }
                    if (task.deadlineId != null) Text(stringResource(R.string.planner_deadline_tag), style = MaterialTheme.typography.labelSmall, color = VaultColors.Amber)
                    if (skipped) Text(statusLabel(TaskStatus.SKIPPED), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
                    if (task.rolledOverFrom != null) Text(stringResource(R.string.planner_rolled), style = MaterialTheme.typography.labelSmall, color = VaultColors.Amber)
                }
            }
            Icon(Icons.Outlined.DragIndicator, null, tint = VaultColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RolloverBanner(count: Int, onDecide: (Boolean) -> Unit) {
    VaultCard(Modifier.fillMaxWidth(), accent = VaultColors.Amber) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, tint = VaultColors.Amber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluralStringResource(R.plurals.rollover_banner, count, count), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = { onDecide(false) }) { Text(stringResource(R.string.action_leave_them), color = VaultColors.TextSecondary) }
                TextButton(onClick = { onDecide(true) }) { Text(stringResource(R.string.action_roll_over), color = VaultColors.Amber) }
            }
        }
    }
}
