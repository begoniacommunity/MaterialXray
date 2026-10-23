package com.material.xray.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

data class FloatingTab(val label: String, @DrawableRes val icon: Int)

/** Space above the selector's outer margin, included in the panel's measured fading area. */
val FloatingTabFadeHeight = 16.dp

/** FloatingToolbar-style tabs: the selected background and icon grow together with the pager. */
@Composable
fun FloatingTabRow(tabs: List<FloatingTab>, pagerState: PagerState, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    var gestureInProgress by remember(pagerState) { mutableStateOf(false) }
    LaunchedEffect(dragging, pagerState.isScrollInProgress) {
        if (dragging) gestureInProgress = true
        if (!pagerState.isScrollInProgress) gestureInProgress = false
    }
    // Keep following a swipe through its settling fling. Programmatic changes animate each
    // tab's target independently, so skipped tabs never become selected along the way.
    val followPager = dragging || gestureInProgress
    val selectedPage = if (followPager) pagerState.currentPage else pagerState.targetPage
    val colors = MaterialTheme.colorScheme
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier.fillMaxWidth().drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to colors.surface.copy(alpha = 0f),
                    0.6f to colors.surface.copy(alpha = 0.9f),
                    1f to colors.surface,
                    startY = 0f,
                    endY = size.height,
                ),
            )
        }.padding(top = FloatingTabFadeHeight).padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.clip(CircleShape).background(colors.surfaceContainerHigh)
                .onSizeChanged { viewportSize = it }
                .horizontalScroll(rememberScrollState()).selectableGroup().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val bringIntoView = remember { BringIntoViewRequester() }
                var tabSize by remember { mutableStateOf(IntSize.Zero) }
                LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress, viewportSize, tabSize) {
                    if (!pagerState.isScrollInProgress && pagerState.settledPage == index) bringIntoView.bringIntoView()
                }
                val selectedFraction by animateFloatAsState(
                    targetValue = floatingTabSelectionFraction(index, position, pagerState.targetPage, followPager),
                    animationSpec = if (followPager) snap() else tween(300),
                    label = "floatingTabSelection:$index",
                )
                val contentColor = lerp(colors.onSurfaceVariant, colors.onSecondaryContainer, selectedFraction)
                Row(
                    modifier = Modifier.clip(CircleShape)
                        .bringIntoViewRequester(bringIntoView).onSizeChanged { tabSize = it }
                        .background(colors.secondaryContainer.copy(alpha = selectedFraction))
                        .selectable(selected = selectedPage == index, role = Role.Tab, onClick = {
                            gestureInProgress = false
                            onSelected(index)
                        })
                        .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.width(26.dp * selectedFraction).clip(RectangleShape)) {
                        Icon(
                            painter = painterResource(tab.icon),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.requiredSize(20.dp).graphicsLayer { alpha = selectedFraction },
                        )
                    }
                    Text(tab.label, color = contentColor, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

internal fun floatingTabSelectionFraction(index: Int, pagerPosition: Float, targetPage: Int, followPager: Boolean): Float = when {
    followPager -> (1f - abs(pagerPosition - index)).coerceIn(0f, 1f)
    index == targetPage -> 1f
    else -> 0f
}
