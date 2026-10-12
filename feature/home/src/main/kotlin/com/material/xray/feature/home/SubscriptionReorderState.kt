package com.material.xray.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex
import com.material.xray.core.ui.components.REORDER_MOVE_MS
import com.material.xray.core.ui.components.ReorderPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Stable
internal class SubscriptionReorderState(
    private val listState: LazyListState,
    private val order: SnapshotStateList<Long>,
    private val coroutineScope: CoroutineScope,
    private val edgeSize: Float,
) {
    var draggingId by mutableStateOf<Long?>(null)
        private set
    private var top by mutableFloatStateOf(0f)
    private var pointerY = 0f
    private var direction = 1
    private var initialOrder = emptyList<Long>()
    private var itemOffset by mutableFloatStateOf(0f)
    private var itemSize = 0
    private var pinned: PinnableContainer.PinnedHandle? = null
    private var scrolling: Job? = null
    var onFinish: (List<Long>) -> Unit = {}

    fun indexOf(id: Long): Int = order.indexOf(id)

    fun offset(id: Long): Float {
        if (id != draggingId) return 0f
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
        return top - (item?.offset?.toFloat() ?: itemOffset)
    }

    fun placed(id: Long, offset: Float, size: Int) {
        if (id == draggingId) {
            itemOffset = offset - listState.layoutInfo.beforeContentPadding
            itemSize = size
        }
    }

    fun start(id: Long, pointerOffset: Float, container: PinnableContainer?) {
        if (draggingId != null || id !in order) return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        draggingId = id
        initialOrder = order.toList()
        pinned = container?.pin()
        top = item.offset.toFloat()
        itemOffset = top
        itemSize = item.size
        pointerY = top + pointerOffset
        scrolling = coroutineScope.launch {
            while (isActive) {
                val layout = listState.layoutInfo
                val speed = when {
                    pointerY < layout.viewportStartOffset + edgeSize -> -edgeSize / 8f
                    pointerY > layout.viewportEndOffset - edgeSize -> edgeSize / 8f
                    else -> 0f
                }
                if (speed != 0f) {
                    direction = if (speed > 0f) 1 else -1
                    itemOffset -= listState.scrollBy(speed)
                    moveAcrossNeighbor()
                }
                delay(16)
            }
        }
    }

    fun drag(delta: Float) {
        if (draggingId == null || delta == 0f) return
        direction = if (delta > 0f) 1 else -1
        top += delta
        pointerY += delta
        moveAcrossNeighbor()
    }

    private fun moveAcrossNeighbor() {
        val id = draggingId ?: return
        val current = order.indexOf(id)
        val next = current + direction
        val neighborId = order.getOrNull(next) ?: return
        val neighbor = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == neighborId } ?: return
        val midpoint = neighbor.offset + neighbor.size / 2f
        val crossed = if (direction > 0) top + itemSize > midpoint else top < midpoint
        if (crossed) order.add(next, order.removeAt(current))
    }

    fun finish(cancelled: Boolean = false) {
        scrolling?.cancel()
        if (draggingId == null) return
        draggingId = null
        pinned?.release()
        pinned = null
        if (cancelled) {
            order.clear()
            order.addAll(initialOrder)
        } else if (order != initialOrder) {
            onFinish(order.toList())
        }
    }

    fun moveAccessible(id: Long, direction: Int): Boolean {
        val current = order.indexOf(id)
        val next = current + direction
        if (current < 0 || next !in order.indices) return false
        order.add(next, order.removeAt(current))
        onFinish(order.toList())
        return true
    }
}

internal typealias SubscriptionPlacement = ReorderPlacement

@Composable
internal fun subscriptionReorderModifier(id: Long, state: SubscriptionReorderState): Modifier {
    val coroutineScope = rememberCoroutineScope()
    val placement = remember(id, coroutineScope) { SubscriptionPlacement(coroutineScope) }
    val index = state.indexOf(id)
    val dragging = state.draggingId == id
    val dragOffset by animateFloatAsState(
        targetValue = state.offset(id),
        animationSpec = if (dragging) snap() else tween(REORDER_MOVE_MS),
        label = "subscriptionDrop",
    )
    val floating = dragging || dragOffset != 0f
    val placementMoving = placement.offset != 0f || placement.changingIndex(index)
    val moving = floating || placementMoving
    val container = LocalPinnableContainer.current
    DisposableEffect(container, moving) {
        val pinned = if (moving) container?.pin() else null
        onDispose { pinned?.release() }
    }
    return Modifier
        .zIndex(if (floating) 1f else 0f)
        .onPlaced {
            val position = it.positionInParent().y
            state.placed(id, position, it.size.height)
            placement.placed(index, position, dragging)
        }
        .graphicsLayer {
            translationY = if (dragging) state.offset(id) else dragOffset + placement.offset
        }
}
