package com.flexa.spend.main.main_screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

@Composable
internal fun PagerDots(
    pagerState: PagerState,
    count: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    dotSize: Dp = 6.dp,
    spacing: Dp = 8.dp
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        repeat(count) { page ->
            val isSelected = pagerState.currentPage == page
            val pageOffset =
                ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

            val animatedScale by animateFloatAsState(
                targetValue = if (isSelected) 1.5f else 1.0f,
                label = "dotScale"
            )

            val animatedAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else (1.2f - pageOffset).coerceAtLeast(0.3f),
                label = "dotAlpha"
            )

            Canvas(
                modifier = Modifier
                    .size(dotSize * animatedScale)
                    .graphicsLayer {
                        alpha = animatedAlpha
                    }
            ) {
                drawCircle(color = if (isSelected) activeColor else inactiveColor)
            }
        }
    }
}
