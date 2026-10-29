package com.material.xray.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateAlarmPermissionTest {
    @Test
    fun `missing permission launches the request on Android 12 and newer`() {
        for (sdkInt in listOf(31, 33, 34, 37)) {
            var launches = 0
            requestUpdateAlarmPermission(sdkInt, canScheduleExactAlarms = { false }, launch = { launches++ })
            assertEquals(1, launches)
        }
    }

    @Test
    fun `granted permission does not launch another request`() {
        var launches = 0
        requestUpdateAlarmPermission(34, canScheduleExactAlarms = { true }, launch = { launches++ })
        assertEquals(0, launches)
    }

    @Test
    fun `older Android versions neither query permission nor launch settings`() {
        requestUpdateAlarmPermission(
            sdkInt = 30,
            canScheduleExactAlarms = { error("Exact alarm access is only queried on Android 12 and newer") },
            launch = { error("Older Android versions have no exact alarm permission request") },
        )
    }
}
