package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Preview(showBackground = true)
@Composable
private fun CircularScreenProgressIndicatorComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            Column {
                CircularScreenProgressIndicator()
                CircularMidProgressIndicator()
            }
        }
    }
}

@Composable
fun CircularScreenProgressIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(modifier),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = ZcashTheme.colors.circularProgressBarScreen,
            modifier =
                Modifier
                    .size(ZcashTheme.dimens.circularScreenProgressWidth)
        )
    }
}

@Composable
fun CircularMidProgressIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        color = ZcashTheme.colors.circularProgressBarScreen,
        strokeWidth = 3.dp,
        modifier =
            Modifier
                .size(ZcashTheme.dimens.circularMidProgressWidth)
                .then(modifier)
    )
}

@Composable
fun CircularSmallProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = ZcashTheme.colors.circularProgressBarSmall,
) {
    CircularProgressIndicator(
        color = color,
        strokeWidth = 2.dp,
        modifier =
            Modifier
                .size(ZcashTheme.dimens.circularSmallProgressWidth)
                .then(modifier)
    )
}

@Preview(showBackground = true)
@Composable
private fun LinearProgressIndicatorComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            @Suppress("MagicNumber")
            SmallLinearProgressIndicator(0.75f)
        }
    }
}

@Composable
fun SmallLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        drawStopIndicator = {},
        progress = { progress },
        color = ZcashTheme.colors.linearProgressBarBackground,
        trackColor = ZcashTheme.colors.linearProgressBarTrack,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ZcashTheme.dimens.linearProgressHeight)
                .then(modifier)
    )
}
