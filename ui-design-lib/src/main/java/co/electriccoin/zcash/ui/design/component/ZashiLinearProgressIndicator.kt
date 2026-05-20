package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors

@Composable
fun ZashiLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        drawStopIndicator = {},
        progress = { progress },
        color = ZashiColors.Surfaces.brandBg,
        trackColor = ZashiColors.Surfaces.bgTertiary,
        strokeCap = StrokeCap.Round,
        gapSize = (-4).dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .then(modifier)
    )
}

@Preview(showBackground = true)
@Composable
private fun ZashiLinearProgressIndicatorPreview() {
    ZcashTheme(forceDarkMode = false) {
        @Suppress("MagicNumber")
        ZashiLinearProgressIndicator(0.75f)
    }
}
