package com.material.xray.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

data class LazyListSelectionGesture(val modifier: Modifier, val dragging: Boolean)

@Composable
fun <T : Any> rememberLazyListSelectionGesture(
    listState: LazyListState,
    keys: List<T>,
    selectedKeys: Set<T>,
    onSelectionChange: (Set<T>) -> Unit,
    protectedEnd: Dp = 0.dp,
    startOnItemOnly: Boolean = false,
    enabled: Boolean = true,
    handleTaps: Boolean = true,
): LazyListSelectionGesture {
    val currentKeys by rememberUpdatedState(keys)
    val keyMap = remember(keys) { keys.associateBy<T, Any> { it } }
    val currentKeyMap by rememberUpdatedState(keyMap)
    val currentSelectedKeys by rememberUpdatedState(selectedKeys)
    val changeSelection by rememberUpdatedState(onSelectionChange)
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val edgeSize = with(LocalDensity.current) { 48.dp.toPx() }
    val protectedEndPx = with(LocalDensity.current) { protectedEnd.toPx() }
    var gesture by remember { mutableStateOf<DragSelection<T>?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }

    fun keyAt(y: Float, exact: Boolean = false): T? {
        val item = selectableListItemAt(listState.layoutInfo.visibleItemsInfo, currentKeyMap.keys, y, exact) ?: return null
        return currentKeyMap[item.key]
    }

    fun extendSelection(y: Float) {
        val end = keyAt(y) ?: return
        gesture?.let { changeSelection(it.selectionAt(currentKeys, end)) }
    }

    LaunchedEffect(gesture, enabled, edgeSize) {
        if (gesture == null || !enabled) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val viewport = listState.layoutInfo
            val edgeDistance = when {
                fingerY < viewport.viewportStartOffset + edgeSize -> fingerY - viewport.viewportStartOffset - edgeSize
                fingerY > viewport.viewportEndOffset - edgeSize -> fingerY - viewport.viewportEndOffset + edgeSize
                else -> 0f
            }.coerceIn(-edgeSize, edgeSize)
            if (edgeDistance != 0f) {
                listState.scrollBy(edgeDistance * 10f * ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f))
                extendSelection(fingerY)
            }
            previousFrame = frame
        }
    }

    val modifier = Modifier.pointerInput(listState) {
        awaitPointerEventScope {
            consumeSelectionRelease { gesture != null }
        }
    }.pointerInput(listState, enabled, protectedEndPx, rtl, startOnItemOnly, handleTaps) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!selectionGestureCanStart(down.position.x, size.width.toFloat(), protectedEndPx, rtl)) return@awaitEachGesture
            val anchor = keyAt(down.position.y, exact = startOnItemOnly) ?: return@awaitEachGesture
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress == null) {
                val tapped = currentEvent.changes.any { it.id == down.id && it.changedToUp() }
                if (handleTaps && tapped && currentSelectedKeys.isNotEmpty()) {
                    changeSelection(if (anchor in currentSelectedKeys) currentSelectedKeys - anchor else currentSelectedKeys + anchor)
                }
            } else {
                try {
                    gesture = DragSelection(anchor, currentSelectedKeys)
                    fingerY = longPress.position.y
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    extendSelection(fingerY)
                    longPress.consume()
                    drag(longPress.id) { change ->
                        fingerY = change.position.y
                        extendSelection(fingerY)
                        change.consume()
                    }
                    currentEvent.changes.forEach { it.consume() }
                } finally {
                    gesture = null
                }
            }
        }
    }
    return LazyListSelectionGesture(modifier, gesture != null)
}

private suspend fun AwaitPointerEventScope.consumeSelectionRelease(selecting: () -> Boolean) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        // A completed long press must not become a click in a child card.
        if (selecting()) event.changes.filter { it.changedToUpIgnoreConsumed() }.forEach { it.consume() }
    }
}

internal fun selectionGestureCanStart(x: Float, width: Float, protectedEnd: Float, rtl: Boolean): Boolean = x >= 0f && x <= width && if (rtl) x > protectedEnd else x < width - protectedEnd

internal fun selectableListItemAt(items: List<LazyListItemInfo>, keys: Set<Any>, y: Float, exact: Boolean = false): LazyListItemInfo? {
    val selectable = items.filter { it.key in keys }
    val hit = selectable.firstOrNull { y >= it.offset && y < it.offset + it.size }
    if (hit != null || exact) return hit
    return selectable.minByOrNull { minOf(kotlin.math.abs(y - it.offset), kotlin.math.abs(y - it.offset - it.size)) }
}
