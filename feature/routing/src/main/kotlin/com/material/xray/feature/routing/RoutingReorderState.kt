package com.material.xray.feature.routing

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.material.xray.core.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Stable
internal class RoutingReorderState(
    private val listState: LazyListState,
    private val coroutineScope: CoroutineScope,
    private val edgeSize: Float,
) {
    var draggingKey by mutableStateOf<String?>(null)
        private set
    private var center by mutableFloatStateOf(0f)
    private var candidates = emptyList<String>()
    private var direction = 1
    private var changed = false
    private var pinned: PinnableContainer.PinnedHandle? = null
    private var itemOffset by mutableFloatStateOf(0f)
    private var itemSize = 0
    private var scrolling: Job? = null
    var keys: (String) -> List<String> = { emptyList() }
    var onMove: (String, String) -> Unit = { _, _ -> }
    var onFinish: (String, Boolean) -> Unit = { _, _ -> }

    fun offset(key: String): Float {
        if (key != draggingKey) return 0f
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        return center - ((item?.offset?.toFloat() ?: itemOffset) + (item?.size ?: itemSize) / 2f)
    }

    fun placed(key: String, offset: Float, size: Int) {
        if (key == draggingKey) {
            itemOffset = offset - listState.layoutInfo.beforeContentPadding
            itemSize = size
        }
    }

    fun start(key: String, container: PinnableContainer?) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        changed = false
        pinned = container?.pin()
        itemOffset = item.offset.toFloat()
        itemSize = item.size
        candidates = keys(key)
        center = item.offset + item.size / 2f
        scrolling = coroutineScope.launch {
            while (isActive) {
                val layout = listState.layoutInfo
                val speed = when {
                    center < layout.viewportStartOffset + edgeSize -> -edgeSize / 8f
                    center > layout.viewportEndOffset - edgeSize -> edgeSize / 8f
                    else -> 0f
                }
                if (speed != 0f) {
                    direction = if (speed > 0f) 1 else -1
                    itemOffset -= listState.scrollBy(speed)
                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { itemOffset = it.offset.toFloat() }
                    moveAcrossNeighbor()
                }
                delay(16)
            }
        }
    }

    fun drag(delta: Float) {
        if (delta != 0f) direction = if (delta > 0f) 1 else -1
        center += delta
        moveAcrossNeighbor()
    }

    private fun moveAcrossNeighbor() {
        val key = draggingKey ?: return
        val current = candidates.indexOf(key)
        val neighborKey = candidates.getOrNull(current + direction) ?: return
        val neighbor = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == neighborKey } ?: return
        val crossed = if (direction > 0) center > neighbor.offset + neighbor.size / 2f else center < neighbor.offset + neighbor.size / 2f
        if (crossed) {
            changed = true
            candidates = candidates.moved(key, neighborKey)
            onMove(key, neighborKey)
        }
    }

    fun finish(cancelled: Boolean = false) {
        scrolling?.cancel()
        val key = draggingKey ?: return
        draggingKey = null
        pinned?.release()
        pinned = null
        onFinish(key, cancelled || !changed)
    }

    fun moveAccessible(key: String, direction: Int): Boolean {
        val order = keys(key)
        val neighbor = order.getOrNull(order.indexOf(key) + direction) ?: return false
        onMove(key, neighbor)
        onFinish(key, false)
        return true
    }
}

internal fun <T> List<T>.moved(from: T, to: T): List<T> {
    val source = indexOf(from)
    val target = indexOf(to)
    if (source < 0 || target < 0 || source == target) return this
    return toMutableList().apply { add(target, removeAt(source)) }
}

@Composable
internal fun RoutingDragHandle(key: String, label: String, moveUp: String, moveDown: String, state: RoutingReorderState, enabled: Boolean = true) {
    val container = LocalPinnableContainer.current
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp)
            .semantics {
                contentDescription = label
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(moveUp) { state.moveAccessible(key, -1) },
                        CustomAccessibilityAction(moveDown) { state.moveAccessible(key, 1) },
                    )
                }
            }
            .pointerInput(key, enabled, state) {
                if (enabled) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            state.start(key, container)
                            if (state.draggingKey == key) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = { state.finish() },
                        onDragCancel = { state.finish(cancelled = true) },
                        onDrag = { change, delta ->
                            change.consume()
                            state.drag(delta.y)
                        },
                    )
                }
            },
    ) {
        Icon(painterResource(R.drawable.drag_handle_24px), contentDescription = null, modifier = Modifier.size(24.dp))
    }
}
