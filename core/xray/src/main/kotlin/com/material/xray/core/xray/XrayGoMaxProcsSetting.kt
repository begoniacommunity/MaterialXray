package com.material.xray.core.xray

import kotlinx.coroutines.flow.Flow

/** The user's GOMAXPROCS for every core the app starts; 0 leaves it to Go. */
interface XrayGoMaxProcsSetting {
    val xrayGoMaxProcs: Flow<Int>
}

/** The environment that applies [goMaxProcs] to a core, empty when Go should pick. */
fun xrayGoMaxProcsEnvironment(goMaxProcs: Int): Map<String, String> = if (goMaxProcs > 0) mapOf("GOMAXPROCS" to goMaxProcs.toString()) else emptyMap()
