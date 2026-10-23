package com.material.xray.core.android.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.material.xray.core.data.platform.LauncherIconSwitcher
import com.material.xray.core.model.LauncherIcon
import com.material.xray.core.model.LauncherName
import org.koin.core.annotation.Singleton

@Singleton(binds = [LauncherIconSwitcher::class])
class LauncherIconManager(
    private val context: Context,
) : LauncherIconSwitcher {
    override fun apply(icon: LauncherIcon, name: LauncherName) {
        val packageManager = context.packageManager
        packageManager.setComponentEnabledSetting(
            componentName(name.aliasClassName(icon)),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )

        val selected = name.aliasClassName(icon)
        LauncherName.entries.flatMap { launcherName -> LauncherIcon.entries.map(launcherName::aliasClassName) }
            .filterNot { it == selected }
            .forEach { alias ->
                packageManager.setComponentEnabledSetting(
                    componentName(alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
    }

    private fun componentName(alias: String): ComponentName = ComponentName(context, "${context.packageName}.$alias")
}
