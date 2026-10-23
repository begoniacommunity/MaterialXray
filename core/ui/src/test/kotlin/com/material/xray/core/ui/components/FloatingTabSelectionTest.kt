package com.material.xray.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingTabSelectionTest {
    @Test
    fun `tap across five tabs never selects intermediate pages while the pager scrolls`() {
        listOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f).forEach { position ->
            val targets = (0..4).map { floatingTabSelectionFraction(it, position, 4, followPager = false) }
            assertEquals(listOf(0f, 0f, 0f, 0f, 1f), targets)
        }
    }

    @Test
    fun `reverse and interrupted programmatic changes animate toward only the new destination`() {
        assertEquals(listOf(1f, 0f, 0f, 0f, 0f), (0..4).map { floatingTabSelectionFraction(it, 2.5f, 0, followPager = false) })
        assertEquals(listOf(0f, 1f, 0f, 0f, 0f), (0..4).map { floatingTabSelectionFraction(it, 2.5f, 1, followPager = false) })
    }

    @Test
    fun `drag and fling follow the physical position including intermediate tabs`() {
        assertEquals(listOf(0f, 0.75f, 0.25f, 0f, 0f), (0..4).map { floatingTabSelectionFraction(it, 1.25f, 4, followPager = true) })
        assertEquals(listOf(0f, 0f, 0f, 0.5f, 0.5f), (0..4).map { floatingTabSelectionFraction(it, 3.5f, 0, followPager = true) })
    }
}
