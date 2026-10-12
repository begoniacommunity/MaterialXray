package com.material.xray.core.ui.components

import androidx.compose.runtime.Immutable

/** Each instance represents one completion, including repeated successes or failures. */
@Immutable
class ActionFeedback(val successful: Boolean) {
    internal val completedAtNanos = System.nanoTime()
}
