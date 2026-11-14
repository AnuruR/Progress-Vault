package com.anuru.progressvault.ui.planner

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import com.anuru.progressvault.data.db.entity.PlannerTaskEntity
import java.time.DayOfWeek

/** Where a dragged task can land. */
sealed interface DropZone {
    data class Day(val day: DayOfWeek?) : DropZone
    data object NextWeek : DropZone
}

/**
 * In-app drag & drop for planner tasks (no platform DnD): long-press a task, a floating
 * copy follows the finger, and drop zones register their on-screen bounds for hit-testing.
 */
@Stable
class PlannerDragState(private val onDrop: (taskId: Long, zone: DropZone) -> Unit) {
    var dragged by mutableStateOf<PlannerTaskEntity?>(null)
        private set
    /** Finger position in root coordinates. */
    var position by mutableStateOf(Offset.Zero)
        private set
    var hovered by mutableStateOf<DropZone?>(null)
        private set

    private val zones = mutableStateMapOf<Any, Pair<DropZone, Rect>>()

    val isDragging: Boolean get() = dragged != null

    fun register(key: Any, zone: DropZone, bounds: Rect) { zones[key] = zone to bounds }
    fun unregister(key: Any) { zones.remove(key) }

    fun start(task: PlannerTaskEntity, rootPos: Offset) {
        dragged = task
        position = rootPos
        hovered = hitTest(rootPos)
    }

    fun moveBy(delta: Offset) {
        position += delta
        hovered = hitTest(position)
    }

    fun end() {
        val task = dragged
        val zone = hovered
        dragged = null
        hovered = null
        if (task != null && zone != null) onDrop(task.id, zone)
    }

    fun cancel() { dragged = null; hovered = null }

    // Later-registered zones (e.g. the always-visible strip) win over list rows beneath them.
    private fun hitTest(p: Offset): DropZone? = zones.values.lastOrNull { it.second.contains(p) }?.first
}

@Composable
fun rememberPlannerDragState(onDrop: (Long, DropZone) -> Unit): PlannerDragState = remember { PlannerDragState(onDrop) }

/** Marks this element as a drop zone. */
fun Modifier.dropZone(state: PlannerDragState, key: Any, zone: DropZone): Modifier = composed {
    DisposableEffect(key) { onDispose { state.unregister(key) } }
    onGloballyPositioned { state.register(key, zone, it.boundsInRoot()) }
}

/** Long-press on this element starts dragging [task]. */
fun Modifier.dragTask(state: PlannerDragState, task: PlannerTaskEntity): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    this
        .onGloballyPositioned { coords = it }
        .pointerInput(task.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(task, coords?.localToRoot(local) ?: local)
                },
                onDrag = { change, amount -> change.consume(); state.moveBy(amount) },
                onDragEnd = { state.end() },
                onDragCancel = { state.cancel() },
            )
        }
}
