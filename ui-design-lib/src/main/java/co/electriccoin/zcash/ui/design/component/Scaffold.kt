package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.orDark

@Preview("Scaffold with blank background", showBackground = true)
@Composable
private fun BlankBgScaffoldComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankBgScaffold {
            Text(text = "Blank background scaffold")
        }
    }
}

@Composable
fun BlankBgScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = ZashiColors.Surfaces.bgPrimary,
        topBar = topBar,
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
        content = content,
        modifier = modifier,
    )
}

@Composable
fun GradientBgScaffold(
    startColor: Color,
    endColor: Color,
    modifier: Modifier = Modifier,
    startStop: Float = VERTICAL_GRADIENT_START_STOP,
    endStop: Float = VERTICAL_GRADIENT_END_STOP_LIGHT orDark VERTICAL_GRADIENT_END_STOP_DARK,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = topBar,
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
        content = content,
        modifier =
            modifier
                .background(
                    zashiVerticalGradient(
                        startColor = startColor,
                        endColor = endColor,
                        startStop = startStop,
                        endStop = endStop
                    )
                ),
    )
}
