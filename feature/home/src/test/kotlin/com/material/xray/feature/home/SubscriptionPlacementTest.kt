package com.material.xray.feature.home

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionPlacementTest {
    @Test
    fun `expanding collapsing and scrolling keep cards at their layout positions`() = runTest {
        val placement = placement()
        placement.placed(1, 80f, dragging = false)
        placement.placed(1, 680f, dragging = false)
        assertEquals(0f, placement.offset, 0f)
        placement.placed(1, 80f, dragging = false)
        placement.placed(1, -20f, dragging = false)
        advanceUntilIdle()
        assertEquals(0f, placement.offset, 0f)
    }

    @Test
    fun `reordered card moves from its visible position without overshooting`() = runTest {
        val placement = placement()
        placement.placed(0, 80f, dragging = false)
        assertTrue(placement.changingIndex(1))
        placement.placed(1, 680f, dragging = false)
        assertEquals(80f, 680f + placement.offset, 0f)
        assertFalse(placement.changingIndex(1))
        runCurrent()
        var previous = placement.offset
        repeat(14) {
            advanceTimeBy(16)
            runCurrent()
            assertTrue(placement.offset in previous..0f)
            previous = placement.offset
        }
        assertEquals(0f, placement.offset, 0f)
    }

    @Test
    fun `reversing a move starts at the current visible position`() = runTest {
        val placement = placement()
        placement.placed(0, 80f, dragging = false)
        placement.placed(1, 680f, dragging = false)
        runCurrent()
        advanceTimeBy(80)
        runCurrent()
        val visiblePosition = 680f + placement.offset
        placement.placed(0, 80f, dragging = false)
        assertEquals(visiblePosition, 80f + placement.offset, 0.001f)
        runCurrent()
        var previous = placement.offset
        repeat(14) {
            advanceTimeBy(16)
            runCurrent()
            assertTrue(placement.offset in 0f..previous)
            previous = placement.offset
        }
        assertEquals(0f, placement.offset, 0f)
    }

    @Test
    fun `held card follows the finger instead of its previous placement animation`() = runTest {
        val placement = placement()
        placement.placed(0, 80f, dragging = false)
        placement.placed(1, 680f, dragging = false)
        placement.placed(1, 680f, dragging = true)
        advanceUntilIdle()
        assertEquals(0f, placement.offset, 0f)
    }

    private fun TestScope.placement(): SubscriptionPlacement {
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(testScheduler.currentTime * 1_000_000)
            }
        }
        return SubscriptionPlacement(CoroutineScope(coroutineContext + clock))
    }
}
