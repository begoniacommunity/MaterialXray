package com.material.xray.core.data.repository

import com.material.xray.core.model.BackupData
import com.material.xray.core.model.LauncherIcon
import com.material.xray.core.model.LauncherName
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LauncherSettingsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `launcher name persists independently from icon and round trips through settings backup`() = runBlocking {
        val store = PreferenceDataStores.settings { File(folder.root, "settings.preferences_pb") }
        val repository = SettingsRepository(store) { }
        assertEquals(LauncherName.MaterialXray, repository.launcherName.first())
        repository.setLauncherName(LauncherName.MXray)
        repository.setLauncherIcon(LauncherIcon.Material)
        val recreated = SettingsRepository(store) { }
        assertEquals(LauncherName.MXray, recreated.settingsSnapshot.first().launcherName)
        val backup = recreated.getAllAsMap()
        repository.setLauncherName(LauncherName.MaterialXray)
        repository.restoreFromMap(backup, BackupData.CURRENT_VERSION)
        assertEquals(LauncherName.MXray, repository.launcherName.first())
        assertEquals(LauncherIcon.Material, repository.launcherIcon.first())
        repository.restoreFromMap(emptyMap(), BackupData.CURRENT_VERSION)
        assertEquals(LauncherName.MaterialXray, repository.launcherName.first())
    }
}
