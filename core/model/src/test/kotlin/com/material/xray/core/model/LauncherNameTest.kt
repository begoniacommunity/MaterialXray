package com.material.xray.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherNameTest {
    @Test
    fun `each icon and name has its own launcher component while old components stay valid`() {
        val names = LauncherName.entries.flatMap { name -> LauncherIcon.entries.map(name::aliasClassName) }
        assertEquals(4, names.toSet().size)
        assertEquals("DefaultLauncherAlias", LauncherName.MXray.aliasClassName(LauncherIcon.Default))
        assertEquals("MaterialLauncherAlias", LauncherName.MXray.aliasClassName(LauncherIcon.Material))
        assertEquals("DefaultFullNameLauncherAlias", LauncherName.MaterialXray.aliasClassName(LauncherIcon.Default))
        assertEquals("MaterialFullNameLauncherAlias", LauncherName.MaterialXray.aliasClassName(LauncherIcon.Material))
        assertEquals(LauncherName.MaterialXray, LauncherName.fromValue(null))
        assertEquals(LauncherName.MaterialXray, LauncherName.fromValue("unknown"))
        assertEquals(LauncherName.MXray, LauncherName.fromValue("mxray"))
    }
}
