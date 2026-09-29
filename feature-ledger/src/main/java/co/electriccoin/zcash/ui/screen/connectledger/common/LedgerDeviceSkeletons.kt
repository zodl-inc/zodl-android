package co.electriccoin.zcash.ui.screen.connectledger.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.rememberZashiShimmer
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import com.valentinilk.shimmer.shimmer

/**
 * The Figma placeholder list of the Ledger screens: three rows under a gradient that turns fully
 * opaque halfway down, with [overlay] laid over them for an indicator anchored to their lower part.
 * The gradient spans only the rows; Figma's extends into the 20 dp inset beside them, where it lies
 * over the same background and cannot show.
 */
@Composable
internal fun LedgerDeviceSkeletons(
    isShimmering: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(SKELETON_ROWS) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (isShimmering) Modifier.shimmer(rememberZashiShimmer()) else Modifier)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ZashiColors.Surfaces.bgPrimary)
                            .border(
                                BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary),
                                RoundedCornerShape(12.dp)
                            ).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .background(SkeletonFill, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SkeletonBar(width = SKELETON_TITLE_WIDTH, height = 16, radius = 4)
                        SkeletonBar(width = SKELETON_SUBTITLE_WIDTH, height = 12, radius = 3)
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .background(SkeletonFill, CircleShape)
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to ZashiColors.Surfaces.bgPrimary.copy(alpha = 0f),
                            GRADIENT_OPAQUE_AT to ZashiColors.Surfaces.bgPrimary,
                            1f to ZashiColors.Surfaces.bgPrimary,
                        )
                    )
        )
        overlay()
    }
}

/**
 * Figma fills the placeholders in a gray lighter than bgTertiary: Gray 50 by day, which this token
 * is exactly, and a dark gray at night that this token's Shark 900 matches most closely.
 */
private val SkeletonFill: Color
    @Composable get() = ZashiColors.Utility.Gray.utilityGray50

@Composable
private fun SkeletonBar(width: Int, height: Int, radius: Int) {
    Box(
        modifier =
            Modifier
                .height(height.dp)
                .width(width.dp)
                .clip(RoundedCornerShape(radius.dp))
                .background(SkeletonFill)
    )
}

private const val SKELETON_ROWS = 3

private const val SKELETON_TITLE_WIDTH = 100

private const val SKELETON_SUBTITLE_WIDTH = 68

private const val GRADIENT_OPAQUE_AT = 0.5f
