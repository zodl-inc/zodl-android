package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors

@Composable
fun ZashiLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    colors: ZashiLinearProgressIndicatorColors = ZashiLinearProgressIndicatorDefaults.defaultColors(),
    size: ZashiLinearProgressIndicatorSize = ZashiLinearProgressIndicatorDefaults.defaultSize(),
) {
    LinearProgressIndicator(
        drawStopIndicator = {},
        progress = { progress },
        color = colors.progressColor,
        trackColor = colors.trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = size.gap,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(size.height)
                .then(modifier)
    )
}

data class ZashiLinearProgressIndicatorColors(
    val progressColor: Color,
    val trackColor: Color,
)

data class ZashiLinearProgressIndicatorSize(
    val height: Dp,
    val gap: Dp = -height
)

object ZashiLinearProgressIndicatorDefaults {
    @Composable
    fun keystoneColors(
        progressColor: Color = ZashiColors.Surfaces.brandBg,
        trackColor: Color = ZashiColors.Surfaces.bgTertiary,
    ) = ZashiLinearProgressIndicatorColors(
        progressColor = progressColor,
        trackColor = trackColor,
    )

    @Composable
    fun defaultColors(
        progressColor: Color = ZashiColors.Text.textPrimary,
        trackColor: Color = ZashiColors.Surfaces.bgQuaternary,
    ) = ZashiLinearProgressIndicatorColors(
        progressColor = progressColor,
        trackColor = trackColor,
    )

    fun keystoneSize(height: Dp = 4.dp) = ZashiLinearProgressIndicatorSize(height = height)

    fun defaultSize(height: Dp = 8.dp) = ZashiLinearProgressIndicatorSize(height = height)
}

@Preview
@Composable
private fun ZashiLinearProgressIndicatorPreview() {
    ZcashTheme(forceDarkMode = false) {
        @Suppress("MagicNumber")
        ZashiLinearProgressIndicator(0.75f)
    }
}
