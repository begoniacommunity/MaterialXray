package com.material.xray.core.ui.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyListSelectionGestureTest {
    @Test
    fun `trailing control zone cannot start selection in either layout direction`() {
        assertTrue(selectionGestureCanStart(199f, 300f, 100f, rtl = false))
        assertFalse(selectionGestureCanStart(200f, 300f, 100f, rtl = false))
        assertFalse(selectionGestureCanStart(299f, 300f, 100f, rtl = false))
        assertFalse(selectionGestureCanStart(100f, 300f, 100f, rtl = true))
        assertFalse(selectionGestureCanStart(1f, 300f, 100f, rtl = true))
        assertTrue(selectionGestureCanStart(101f, 300f, 100f, rtl = true))
    }

    @Test
    fun `headers cannot anchor selection and are skipped when extending it`() {
        val items = listOf(item("custom:a", 0, 80), item("scope:subscription", 80, 40), item("subscription:b", 120, 80))
        val keys = setOf<Any>("custom:a", "subscription:b")
        assertNull(selectableListItemAt(items, keys, 100f, exact = true))
        assertEquals("custom:a", selectableListItemAt(items, keys, 85f)?.key)
        assertEquals("subscription:b", selectableListItemAt(items, keys, 115f)?.key)
        assertEquals("custom:a", selectableListItemAt(items, keys, -20f)?.key)
        assertEquals("subscription:b", selectableListItemAt(items, keys, 250f)?.key)
    }

    @Test
    fun `range selection across rule categories restores previous selection when reversed`() {
        val keys = listOf("custom:a", "custom:b", "subscription:c", "profile:d")
        val gesture = DragSelection("custom:b", setOf("profile:d", "hidden:rule"))
        assertEquals(setOf("custom:b", "subscription:c", "profile:d", "hidden:rule"), gesture.selectionAt(keys, "profile:d"))
        assertEquals(setOf("custom:b", "profile:d", "hidden:rule"), gesture.selectionAt(keys, "custom:b"))
    }

    private fun item(itemKey: String, itemOffset: Int, itemSize: Int): LazyListItemInfo = object : LazyListItemInfo {
        override val key: Any = itemKey
        override val offset: Int = itemOffset
        override val size: Int = itemSize
        override val index: Int = 0
        override val contentType: Any? = null
    }
}
