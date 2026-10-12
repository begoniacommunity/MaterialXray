package com.material.xray.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.material.xray.core.ui.R

@Composable
fun SearchClearButton(state: TextFieldState, enabled: Boolean = true) {
    AnimatedVisibility(
        visible = state.text.isNotEmpty(),
        enter = fadeIn(tween(180)) + expandHorizontally(tween(180), expandFrom = Alignment.End) +
            slideInHorizontally(tween(180)) { it },
        exit = fadeOut(tween(180)) + shrinkHorizontally(tween(180), shrinkTowards = Alignment.End) +
            slideOutHorizontally(tween(180)) { it },
    ) {
        TooltipIconButton(
            tooltip = stringResource(R.string.search_clear),
            enabled = enabled,
            onClick = { state.edit { replace(0, length, "") } },
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear))
        }
    }
}
