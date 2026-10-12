package com.material.xray.core.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

const val REORDER_MOVE_MS = 180

@Stable
class ReorderPlacement(private val coroutineScope: CoroutineScope) {
    var offset by mutableFloatStateOf(0f)
        private set
    private var index by mutableIntStateOf(-1)
    private var position = 0f
    private var animation: Job? = null

    fun changingIndex(newIndex: Int): Boolean = index >= 0 && newIndex != index

    fun placed(newIndex: Int, newPosition: Float, dragging: Boolean) {
        if (index >= 0 && newIndex != index && !dragging) {
            animation?.cancel()
            offset += position - newPosition
            animation = coroutineScope.launch {
                animate(offset, 0f, animationSpec = tween(REORDER_MOVE_MS)) { value, _ -> offset = value }
            }
        } else if (dragging) {
            animation?.cancel()
            offset = 0f
        }
        index = newIndex
        position = newPosition
    }
}
