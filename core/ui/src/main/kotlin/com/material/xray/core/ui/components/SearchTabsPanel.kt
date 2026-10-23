package com.material.xray.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** One clock for the panel's layout and its list clearance, including reversed transitions. */
@Composable
fun Transition<TabsPanelState>.searchPanelFraction(): State<Float> = animateFloat(
    transitionSpec = { tween(220) },
    label = "searchPanelFraction",
) { if (it == TabsPanelState.Search) 1f else 0f }

@Composable
fun SearchTabsPanel(
    transition: Transition<TabsPanelState>,
    state: TextFieldState,
    requested: Boolean,
    hint: String,
    tabs: @Composable () -> Unit,
) {
    // Keep the animated keyboard spacer outside the transition between the two small panels.
    Box(modifier = Modifier.fillMaxWidth().imePadding()) {
        transition.AnimatedContent(
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 }) togetherWith
                    (fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 4 }) using
                    SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(220) })
            },
        ) { panel ->
            when (panel) {
                // Focus starts the keyboard animation. Wait until the outgoing selector is gone,
                // otherwise the shared IME inset lifts it while it is still fading out.
                TabsPanelState.Search -> SearchPanel(state, requested && !transition.isRunning && transition.currentState == TabsPanelState.Search, hint)
                TabsPanelState.Tabs -> tabs()
                TabsPanelState.Hidden -> Box(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
