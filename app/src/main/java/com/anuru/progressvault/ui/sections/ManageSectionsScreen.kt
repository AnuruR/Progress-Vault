package com.anuru.progressvault.ui.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.R
import com.anuru.progressvault.data.repository.SectionRepository
import com.anuru.progressvault.domain.model.Section
import com.anuru.progressvault.ui.components.AccentIcon
import com.anuru.progressvault.ui.components.SectionIcons
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.VaultTopBar
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageSectionsViewModel @Inject constructor(private val repository: SectionRepository) : ViewModel() {
    val sections: StateFlow<List<Section>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun reorder(ids: List<Long>) { viewModelScope.launch { repository.reorder(ids) } }
    fun toggleHidden(section: Section) { viewModelScope.launch { repository.setHidden(section.id, !section.isHidden) } }
}

@Composable
fun ManageSectionsScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    viewModel: ManageSectionsViewModel = hiltViewModel(),
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = VaultColors.Background,
        contentWindowInsets = WindowInsets(0),
        topBar = { VaultTopBar(stringResource(R.string.manage_sections_title), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = VaultColors.Cyan,
                contentColor = VaultColors.OnAccent,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.action_add_section), style = MaterialTheme.typography.labelLarge) },
            )
        },
    ) { padding ->
        val list = sections ?: return@Scaffold
        ReorderableSections(list, viewModel::reorder, onEdit, viewModel::toggleHidden, Modifier.padding(padding))
    }
}

@Composable
private fun ReorderableSections(
    sections: List<Section>,
    onCommit: (List<Long>) -> Unit,
    onEdit: (Long) -> Unit,
    onToggleHidden: (Section) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local copy so the list moves instantly while dragging; persisted when the drag ends.
    var items by remember(sections) { mutableStateOf(sections) }
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = items.indexOfFirst { it.id == from.key }
        val toIdx = items.indexOfFirst { it.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) {
            items = items.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)

    fun moveBy(id: Long, delta: Int): Boolean {
        val from = items.indexOfFirst { it.id == id }
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from < 0 || from == to) return false
        items = items.toMutableList().apply { add(to, removeAt(from)) }
        onCommit(items.map { it.id })
        return true
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hint") { TerminalLabel(stringResource(R.string.manage_sections_hint)) }
        items(items, key = { it.id }) { section ->
            ReorderableItem(reorderState, key = section.id) { isDragging ->
                val accent = accentColor(section.accentColor)
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "dragElevation")
                VaultCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation, MaterialTheme.shapes.medium)
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(moveUp) { moveBy(section.id, -1) },
                                CustomAccessibilityAction(moveDown) { moveBy(section.id, 1) },
                            )
                        },
                    accent = if (isDragging) accent else null,
                    onClick = { onEdit(section.id) },
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.draggableHandle(
                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                onDragStopped = {
                                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    onCommit(items.map { it.id })
                                },
                            ),
                        ) {
                            Icon(Icons.Outlined.DragHandle, stringResource(R.string.cd_drag_handle, section.name), tint = VaultColors.TextMuted)
                        }
                        AccentIcon(SectionIcons.forKey(section.iconKey), accent, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                section.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (section.isHidden) VaultColors.TextMuted else VaultColors.TextPrimary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when {
                                        section.isDerived -> stringResource(R.string.chip_derived)
                                        section.isAcademics -> stringResource(R.string.section_target_from_modules)
                                        else -> stringResource(R.string.onboarding_target_unit, formatAmount(section.defaultWeeklyTarget) + " " + section.unit)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VaultColors.TextMuted,
                                )
                                if (!section.isBuiltIn) StatusChip(stringResource(R.string.chip_custom), accent)
                            }
                        }
                        IconButton(onClick = { onToggleHidden(section) }) {
                            Icon(
                                if (section.isHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(if (section.isHidden) R.string.cd_show_section else R.string.cd_hide_section, section.name),
                                tint = if (section.isHidden) VaultColors.TextMuted else accent,
                            )
                        }
                    }
                }
            }
        }
    }
}
