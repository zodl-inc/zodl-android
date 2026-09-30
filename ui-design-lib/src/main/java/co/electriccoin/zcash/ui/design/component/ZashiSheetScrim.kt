package co.electriccoin.zcash.ui.design.component

import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * The dialog window hosting the calling composable, or null outside a dialog.
 */
@Composable
internal fun rememberSheetDialogWindow(): Window? {
    val view = LocalView.current
    return generateSequence(view.parent) { (it as? View)?.parent }
        .filterIsInstance<DialogWindowProvider>()
        .firstOrNull()
        ?.window
}

/**
 * Lets the window dim whatever lies behind it; [applySheetScrim] sets how much.
 */
internal fun Window.enableSheetScrim() {
    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
}

/**
 * Dims behind the window in proportion to [fraction], the share of the sheet on screen.
 */
internal fun Window.applySheetScrim(fraction: Float) {
    setDimAmount(SCRIM_DIM_AMOUNT * fraction)
}

/**
 * Reports how much of the sheet is on screen, from 0 (hidden) to 1 (fully open), whenever the sheet moves:
 * while it animates open or closed and while it is dragged. It must be a direct child of the sheet's content
 * column, whose size and position (drag handle included) it measures; it takes no space itself.
 */
@Composable
internal fun SheetOpenFractionTracker(onFraction: (Float) -> Unit) {
    Spacer(
        Modifier.onGloballyPositioned { coordinates ->
            val sheet = coordinates.parentLayoutCoordinates ?: return@onGloballyPositioned
            val windowHeight = coordinates.findRootCoordinates().size.height
            val sheetHeight = minOf(sheet.size.height, windowHeight)
            if (sheetHeight <= 0) return@onGloballyPositioned
            onFraction(((windowHeight - sheet.positionInWindow().y) / sheetHeight).coerceIn(0f, 1f))
        }
    )
}

/**
 * Matches Material3's BottomSheetDefaults.ScrimColor (scrim at 0.32 opacity); FLAG_DIM_BEHIND draws black, so
 * the dim amount alone reproduces the default scrim.
 */
private const val SCRIM_DIM_AMOUNT = 0.32f
