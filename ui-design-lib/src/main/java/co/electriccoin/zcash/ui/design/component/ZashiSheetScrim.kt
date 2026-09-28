package co.electriccoin.zcash.ui.design.component

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt

/**
 * The blur radius behind a bottom sheet at full opening, in pixels for the current density.
 */
@Composable
internal fun rememberSheetScrimBlurRadiusPx(): Float = with(LocalDensity.current) { SCRIM_BLUR_RADIUS.toPx() }

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
 * Lets the window dim and, from Android 12, blur whatever lies behind it; [applySheetScrim] sets how much.
 */
internal fun Window.enableSheetScrim() {
    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
}

/**
 * Dims and blurs behind the window in proportion to [fraction], the share of the sheet on screen. The
 * blur needs the device's cross-window blur; where the system has it turned off, only the dim shows.
 */
internal fun Window.applySheetScrim(
    fraction: Float,
    blurRadiusPx: Float
) {
    setDimAmount(SCRIM_DIM_AMOUNT * fraction)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        attributes = attributes.apply { blurBehindRadius = (blurRadiusPx * fraction).roundToInt() }
    }
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

private val SCRIM_BLUR_RADIUS = 12.dp
