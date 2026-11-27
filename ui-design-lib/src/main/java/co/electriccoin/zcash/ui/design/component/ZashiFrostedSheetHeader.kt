package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import dev.chrisbanes.haze.HazeState

/**
 * Frosted top overlay for a bottom sheet: the stock drag handle band plus an optional [title],
 * blurring the sheet content which scrolls underneath it. [frostColor] defaults to
 * [ZashiModalBottomSheetDefaults.ContainerColor] — the sheet surface it frosts over — as does the
 * opaque bar it degrades to where blur is unsupported, so in both modes the band disappears into
 * the sheet at rest.
 *
 * It exists because Material 3 composes a sheet's drag handle *above* its content, so content can
 * never scroll under the handle. A frosted sheet therefore passes `dragHandle = null` to the sheet
 * and pins this composable over the top of its content instead, reproducing the handle itself.
 *
 * Pass the same [hazeState] to the sheet content's [zashiFrostSource].
 *
 * The band's height is not a constant — a [title] reflows with the font scale and the locale — so
 * the content underneath cannot hard-code the top padding which keeps its first element clear of
 * the band. [onHeightChanged] reports the measured height instead; the standard idiom for a frosted
 * sheet is
 *
 * ```
 * var headerHeight by remember { mutableStateOf(0.dp) }
 * Box {
 *     Column(
 *         modifier = Modifier
 *             .zashiFrostSource(hazeState)
 *             .verticalScroll(rememberScrollState())
 *             .padding(top = headerHeight)
 *     ) { … }
 *     ZashiFrostedSheetHeader(
 *         hazeState = hazeState,
 *         modifier = Modifier.align(Alignment.TopCenter),
 *         onHeightChanged = { headerHeight = it },
 *         title = { … }
 *     )
 * }
 * ```
 *
 * The padding sits *inside* the scroll, so it is content padding rather than a viewport inset and
 * the content really does scroll under the band. Whatever [title] renders must have a height which
 * does not depend on the scrolled content, otherwise the band reflows as the user scrolls.
 */
@Composable
fun ZashiFrostedSheetHeader(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    frostColor: Color = ZashiModalBottomSheetDefaults.ContainerColor,
    onHeightChanged: (Dp) -> Unit = {},
    title: (@Composable () -> Unit)? = null
) {
    val density = LocalDensity.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    onHeightChanged(with(density) { size.height.toDp() })
                }.zashiFrostedHeader(
                    hazeState = hazeState,
                    frostColor = frostColor,
                    fallbackColor = ZashiModalBottomSheetDefaults.ContainerColor
                )
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
