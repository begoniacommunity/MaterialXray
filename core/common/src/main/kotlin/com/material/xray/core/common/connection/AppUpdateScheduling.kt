package com.material.xray.core.common.connection

/** Schedules, or cancels, the periodic check for a new app release. */
fun interface AppUpdateScheduling {
    fun setEnabled(enabled: Boolean, intervalSeconds: Int)
}
