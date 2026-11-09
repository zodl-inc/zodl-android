package co.electriccoin.zcash.ui.design.util

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * @see [FocusRequester.requestFocus]
 *
 * @return true if the focus was successfully requested, false if the focus request was canceled or null if request
 * focus failed
 */
fun FocusRequester.tryRequestFocus(): Boolean? =
    try {
        requestFocus()
    } catch (_: IllegalStateException) {
        null
    }

/**
 * Requests focus once [sheetState] is hidden and [view]'s window has the window focus back.
 *
 * A modal bottom sheet lives in its own dialog window, which keeps the window focus until it is torn down after the
 * hide animation. A request made before that is lost (no keyboard), so this waits for both instead of a timer.
 *
 * @see tryRequestFocus
 */
@OptIn(ExperimentalMaterial3Api::class)
suspend fun FocusRequester.requestFocusWhenHidden(
    sheetState: SheetState,
    view: View
): Boolean? {
    snapshotFlow { sheetState.isVisible }.first { !it }
    view.awaitWindowFocus()
    return tryRequestFocus()
}

private suspend fun View.awaitWindowFocus() {
    if (hasWindowFocus()) return
    suspendCancellableCoroutine { continuation ->
        val listener =
            object : ViewTreeObserver.OnWindowFocusChangeListener {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (hasFocus) {
                        viewTreeObserver.removeOnWindowFocusChangeListener(this)
                        continuation.resume(Unit)
                    }
                }
            }
        viewTreeObserver.addOnWindowFocusChangeListener(listener)
        continuation.invokeOnCancellation { viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
}
