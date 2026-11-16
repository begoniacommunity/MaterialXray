package com.material.xray.core.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.IntSize
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionDragTest {
    @Test
    fun `large first swipe and release are consumed before the scrolling main pass`() {
        val events = listOf(
            event(change(y = 200f, previousY = 20f)),
            event(change(y = 300f, previousY = 200f)),
            event(change(y = 300f, pressed = false)),
        )

        assertEquals(listOf(200f, 300f), runDrag(events))
        assertTrue(events.all { it.changes.all(PointerInputChange::isConsumed) })
    }

    @Test
    fun `lifting the selected pointer hands selection to the remaining finger`() {
        val events = listOf(
            event(change(y = 100f), change(id = 1, y = 120f, previousPressed = false)),
            event(change(y = 100f, pressed = false), change(id = 1, y = 140f)),
            event(change(id = 1, y = 200f, previousY = 140f)),
            event(change(id = 1, y = 200f, pressed = false)),
        )

        assertEquals(listOf(100f, 140f, 200f), runDrag(events))
        assertTrue(events.all { it.changes.all(PointerInputChange::isConsumed) })
    }

    @Test
    fun `an ancestor consuming the initial pass cancels selection`() {
        val consumed = change(y = 200f).apply { consume() }
        assertTrue(runDrag(listOf(event(consumed))).isEmpty())
    }

    @Test
    fun `a missing selected pointer cancels without consuming unrelated input`() {
        val unrelated = change(id = 1, y = 200f)
        assertTrue(runDrag(listOf(event(unrelated))).isEmpty())
        assertFalse(unrelated.isConsumed)
    }

    private fun runDrag(events: List<PointerEvent>): List<Float> {
        val positions = mutableListOf<Float>()
        val scope = SelectionPointerScope(events)
        var completion: Result<Unit>? = null
        val block: suspend AwaitPointerEventScope.() -> Unit = {
            dragSelection(PointerId(0)) { change ->
                assertTrue(currentEvent.changes.all(PointerInputChange::isConsumed))
                positions += change.position.y
            }
        }
        block.startCoroutine(
            scope,
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    completion = result
                }
            },
        )
        checkNotNull(completion).getOrThrow()
        return positions
    }

    private fun event(vararg changes: PointerInputChange) = PointerEvent(changes.toList())

    private fun change(id: Long = 0, y: Float, previousY: Float = y, pressed: Boolean = true, previousPressed: Boolean = true) = PointerInputChange(
        id = PointerId(id),
        uptimeMillis = 1,
        position = Offset(0f, y),
        pressed = pressed,
        previousUptimeMillis = 0,
        previousPosition = Offset(0f, previousY),
        previousPressed = previousPressed,
        isInitiallyConsumed = false,
        type = PointerType.Touch,
    )

    private class SelectionPointerScope(private val events: List<PointerEvent>) : AwaitPointerEventScope {
        override val density = 1f
        override val fontScale = 1f
        override val size = IntSize(300, 600)
        override val viewConfiguration: ViewConfiguration get() = error("Drag after long press needs no timing configuration")
        override var currentEvent = PointerEvent(emptyList())
            private set
        private var nextEvent = 0

        override suspend fun awaitPointerEvent(pass: PointerEventPass): PointerEvent {
            assertEquals(PointerEventPass.Initial, pass)
            return events[nextEvent++].also { currentEvent = it }
        }
    }
}
