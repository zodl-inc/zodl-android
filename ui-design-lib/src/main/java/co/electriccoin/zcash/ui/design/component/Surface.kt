package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Preview("Blank background", showBackground = true)
@Composable
private fun BlankSurfacePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            Text(
                text = "Test text on the blank app background",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun BlankSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = ZcashTheme.colors.backgroundColor,
        shape = RectangleShape,
        content = content,
        modifier = modifier
    )
}
