package com.material.xray.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayCoreRequirementTest {
    @Test
    fun `bounds are inclusive and either may be missing`() {
        val range = XrayCoreRequirement(min = "26.10.10", max = "26.12.31")
        assertFalse(range.accepts("26.9.30"))
        assertTrue(range.accepts("v26.10.10"))
        assertTrue(range.accepts("26.12.31"))
        assertFalse(range.accepts("27.1.1"))
        assertTrue(XrayCoreRequirement(min = "26.10.10").accepts("30.0.0"))
        assertTrue(XrayCoreRequirement(max = "26.10.10").accepts("26.1.23"))
    }

    @Test
    fun `a version that cannot be compared is let through`() {
        assertTrue(XrayCoreRequirement(min = "26.10.10").accepts("custom-build"))
    }

    @Test
    fun `of normalizes versions and drops values that are not versions`() {
        assertEquals(XrayCoreRequirement("26.10.10", null), XrayCoreRequirement.of("v26.10.10", "latest"))
        assertEquals(XrayCoreRequirement(null, "26.12.31"), XrayCoreRequirement.of(null, " v26.12.31 "))
        assertNull(XrayCoreRequirement.of("1", "x"))
        assertNull(XrayCoreRequirement.of(null, null))
    }
}
