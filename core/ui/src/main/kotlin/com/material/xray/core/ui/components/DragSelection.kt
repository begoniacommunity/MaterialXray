package com.material.xray.core.ui.components

class DragSelection<T>(
    private val anchorId: T,
    private val initialSelection: Set<T>,
) {
    private val selecting = anchorId !in initialSelection

    fun selectionAt(ids: List<T>, endId: T): Set<T> {
        val start = ids.indexOf(anchorId)
        val end = ids.indexOf(endId)
        if (start < 0 || end < 0) return initialSelection.intersect(ids.toSet())
        val range = ids.subList(minOf(start, end), maxOf(start, end) + 1).toSet()
        // Apply each range to the original selection so reversing restores rows outside it.
        return if (selecting) initialSelection + range else initialSelection - range
    }
}
