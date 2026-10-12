package com.material.xray.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.material.xray.core.ui.R
import kotlinx.coroutines.delay

private enum class ActionIconState { Idle, Loading, Success, Error }

private const val SHRINK_DURATION_MS = 90
private const val GROW_DURATION_MS = 110
private const val FEEDBACK_DURATION_MS = 2_000L

@Composable
fun AnimatedActionIcon(
    loading: Boolean,
    feedback: ActionFeedback? = null,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
    loadingContent: @Composable () -> Unit = {
        CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 2.dp)
    },
    content: @Composable () -> Unit,
) {
    var lastFeedback by remember { mutableStateOf(if (loading) feedback else null) }
    var target by remember { mutableStateOf(if (loading) ActionIconState.Loading else ActionIconState.Idle) }
    LaunchedEffect(loading, feedback) {
        when {
            loading -> target = ActionIconState.Loading
            feedback != null && feedback !== lastFeedback -> {
                lastFeedback = feedback
                val remainingMs = FEEDBACK_DURATION_MS - (System.nanoTime() - feedback.completedAtNanos) / 1_000_000L
                if (remainingMs > 0L) {
                    target = if (feedback.successful) ActionIconState.Success else ActionIconState.Error
                    delay(remainingMs + SHRINK_DURATION_MS + GROW_DURATION_MS)
                }
                target = ActionIconState.Idle
            }
            else -> target = ActionIconState.Idle
        }
    }

    var displayed by remember { mutableStateOf(target) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (displayed == target && scale.value == 1f) return@LaunchedEffect
        scale.animateTo(0f, tween(SHRINK_DURATION_MS, easing = FastOutSlowInEasing))
        displayed = target
        scale.animateTo(1f, tween(GROW_DURATION_MS, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        contentAlignment = Alignment.Center,
    ) {
        when (displayed) {
            ActionIconState.Idle -> content()
            ActionIconState.Loading -> loadingContent()
            ActionIconState.Success -> Icon(
                painterResource(R.drawable.check_24px),
                contentDescription = stringResource(R.string.action_completed),
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
            ActionIconState.Error -> Icon(
                painterResource(R.drawable.error_24px),
                contentDescription = stringResource(R.string.action_failed),
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        }
    }
}
