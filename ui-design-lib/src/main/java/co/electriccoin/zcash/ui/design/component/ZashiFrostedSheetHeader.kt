package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import dev.chrisbanes.haze.HazeState

/**
 * Frosted top overlay for a bottom sheet: the stock drag handle band plus an optional [title],
 * blurring the sheet content which scrolls underneath it.
 *
 * It exists because Material 3 composes a sheet's drag handle *above* its content, so content can
 * never scroll under the handle. A frosted sheet therefore passes `dragHandle = null` to the sheet
 * and pins this composable over the top of its content instead, reproducing the handle itself.
 *
 * Pass the same [hazeState] to the sheet content's [zashiFrostSource].
 */
@Composable
fun ZashiFrostedSheetHeader(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    frostColor: Color = ZashiColors.Surfaces.bgPrimary,
    title: (@Composable () -> Unit)? = null
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .zashiFrostedHeader(hazeState, frostColor)
    ) {
        ZashiModalBottomSheetDragHandle()
        title?.invoke()
    }
}

@PreviewScreens
@Composable
private fun ZashiFrostedSheetHeaderPreview() =
    ZcashTheme {
        ZashiFrostedSheetHeader(
            hazeState = rememberZashiFrostState(),
            title = {
                Text("Title")
            }
        )
    }
