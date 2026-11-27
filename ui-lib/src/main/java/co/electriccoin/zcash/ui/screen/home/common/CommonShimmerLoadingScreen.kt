package co.electriccoin.zcash.ui.screen.home.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import com.valentinilk.shimmer.LocalShimmerTheme
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer

/**
 * @param color the placeholder color; must contrast with the surface the screen is drawn on, so a
 * bottom sheet (whose container is already [ZashiColors.Surfaces.bgSecondary]) needs a different one.
 */
@Suppress("MagicNumber")
@Composable
fun CommonShimmerLoadingScreen(
    shimmerItemsCount: Int,
    modifier: Modifier = Modifier,
    color: Color = ZashiColors.Surfaces.bgSecondary,
    contentPaddingValues: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    disableShimmer: Boolean = false,
    showDivider: Boolean = true
) {
    Column(
        modifier =
            modifier then
                if (disableShimmer) {
                    Modifier
                } else {
                    Modifier.shimmer(
                        customShimmer =
                            rememberShimmer(
                                ShimmerBounds.View,
                                LocalShimmerTheme.current.copy(
                                    animationSpec =
                                        infiniteRepeatable(
                                            animation =
                                                tween(
                                                    durationMillis = 750,
                                                    easing = LinearEasing,
                                                    delayMillis = 450,
                                                ),
                                            repeatMode = RepeatMode.Restart,
                                        )
                                )
                            )
                    )
                }
    ) {
        repeat(shimmerItemsCount) {
            if (it != 0 && showDivider) {
                ZashiHorizontalDivider(modifier = Modifier.padding(4.dp))
            }
            FakeItem(
                color = color,
                modifier = Modifier.padding(contentPaddingValues)
            )
        }
    }
}

@Composable
private fun FakeItem(
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(color, CircleShape)
        )

        Spacer(Modifier.width(16.dp))

        Column {
            Box(
                modifier =
                    Modifier
                        .width(86.dp)
                        .height(14.dp)
                        .background(color, CircleShape)
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier =
                    Modifier
                        .width(64.dp)
                        .height(14.dp)
                        .background(color, CircleShape)
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier =
                Modifier
                    .width(32.dp)
                    .height(14.dp)
                    .background(color, CircleShape)
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            CommonShimmerLoadingScreen(
                10,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
