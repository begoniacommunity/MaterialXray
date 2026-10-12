package com.material.xray.core.ui.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf

/** Lets a transient screen mode use the full viewport while its keyboard is animating. */
val LocalBottomBarVisibility = compositionLocalOf<MutableState<Boolean>?> { null }
