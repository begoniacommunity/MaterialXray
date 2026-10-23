package com.material.xray.core.ui.components

enum class TabsPanelState { Tabs, Search, Hidden }

fun tabsPanelState(searching: Boolean, selecting: Boolean): TabsPanelState = when {
    searching -> TabsPanelState.Search
    selecting -> TabsPanelState.Hidden
    else -> TabsPanelState.Tabs
}
