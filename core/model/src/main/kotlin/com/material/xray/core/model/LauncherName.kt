package com.material.xray.core.model

enum class LauncherName(val value: String, val label: String) {
    MaterialXray("material_xray", "Material Xray"),
    MXray("mxray", "MXray"),
    ;

    fun aliasClassName(icon: LauncherIcon): String = when (this) {
        MaterialXray -> icon.aliasClassName.replace("LauncherAlias", "FullNameLauncherAlias")
        MXray -> icon.aliasClassName
    }

    companion object {
        val default = MaterialXray
        fun fromValue(value: String?): LauncherName = entries.firstOrNull { it.value == value } ?: default
    }
}
