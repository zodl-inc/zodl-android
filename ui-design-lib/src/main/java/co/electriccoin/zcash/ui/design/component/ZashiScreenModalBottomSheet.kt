package co.electriccoin.zcash.ui.design.component

import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import co.electriccoin.zcash.ui.design.LocalKeyboardManager
import co.electriccoin.zcash.ui.design.R

/**
 * A bottom sheet that is a whole navigation destination.
 *
 * The dim behind the sheet is drawn by the host navigation dialog window, which is stretched to fill
 * the screen: its content is empty, so it would otherwise be zero-sized, and Samsung's window manager
 * draws no dim behind a zero-sized window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : ModalBottomSheetState> ZashiScreenModalBottomSheet(
    state: T?,
    sheetGesturesEnabled: Boolean = true,
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
    shape: Shape = ZashiModalBottomSheetDefaults.SheetShape,
    dragHandle: @Composable (() -> Unit)? = { ZashiModalBottomSheetDragHandle() },
    content: @Composable ColumnScope.(state: T, contentPadding: PaddingValues) -> Unit = { _, _ -> },
) {
    // The dim is carried by this host (Nav dialog) window via FLAG_DIM_BEHIND rather than Material's
    // own scrim, so it does not translate when the sheet window slides out. Its window exit
    // animation fades the dim in place on dismissals that tear the composition down (forward/back).
    val hostWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

    val openFraction = remember { mutableFloatStateOf(0f) }
    SideEffect {
        hostWindow?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        hostWindow?.enableSheetScrim()
        hostWindow?.applySheetScrim(fraction = if (state != null) openFraction.floatValue else 0f)
        hostWindow?.setWindowAnimations(R.style.ZashiBottomSheetScrimAnimation)
    }

    state?.let {
        ZashiModalBottomSheet(
            sheetState = sheetState,
            sheetGesturesEnabled = sheetGesturesEnabled,
            scrimColor = Color.Transparent,
            shape = shape,
            dragHandle = dragHandle,
            content = {
                SheetOpenFractionTracker { fraction ->
                    openFraction.floatValue = fraction
                    hostWindow?.applySheetScrim(fraction = fraction)
                }
                BackHandler {
                    it.onBack()
                }
                BottomSheetWindowAnimationEffect()
                content(
                    it,
                    PaddingValues(
                        bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 24.dp
                    )
                )
                LaunchedEffect(Unit) {
                    sheetState.show()
                }

                var wasShown by remember { mutableStateOf(false) }
                LaunchedEffect(sheetState.currentValue) {
                    when (sheetState.currentValue) {
                        Expanded -> wasShown = true
                        Hidden -> if (wasShown) it.onBack()
                        else -> Unit
                    }
                }

                HookupKeyboardController()
            },
            onDismissRequest = it.onBack,
            properties = properties,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZashiScreenModalBottomSheet(
    onDismissRequest: (() -> Unit)? = null,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
    shape: Shape = ZashiModalBottomSheetDefaults.SheetShape,
    dragHandle: @Composable (() -> Unit)? = { ZashiModalBottomSheetDragHandle() },
    content: @Composable ColumnScope.(contentPadding: PaddingValues) -> Unit = {},
) {
    ZashiScreenModalBottomSheet(
        state =
            remember(onDismissRequest) {
                if (onDismissRequest == null) {
                    null
                } else {
                    object : ModalBottomSheetState {
                        override val onBack: () -> Unit = {
                            onDismissRequest()
                        }
                    }
                }
            },
        sheetState = sheetState,
        shape = shape,
        dragHandle = dragHandle,
        content = { _, contentPadding ->
            content(contentPadding)
            HookupKeyboardController()
        },
    )
}

/**
 * Re-applies a slide-down window exit animation to the Material [ModalBottomSheet]'s own dialog
 * window. Material3 1.4.0 sets that window theme's `windowAnimationStyle` to `@null`, so the OS no
 * longer animates the window away on dismissals that don't drive `sheetState.hide()` (e.g.
 * forward/replace navigation, where the composition is torn down in the same frame). Setting the
 * animation at runtime overrides the `@null` and makes every dismissal path animate uniformly.
 *
 * Only the exit animation is applied: opening uses Material's in-Compose slide-up, and on the
 * drag/scrim/back paths the sheet is already off-screen by the time the window is dismissed, so the
 * exit animation is a no-op there. The dim is handled separately on the host window, so this slide
 * is translate-only (the sheet moves, the dim stays put and fades).
 */
@Composable
private fun BottomSheetWindowAnimationEffect() {
    val view = LocalView.current
    SideEffect {
        val window =
            generateSequence(view.parent) { (it as? View)?.parent }
                .filterIsInstance<DialogWindowProvider>()
                .firstOrNull()
                ?.window
        window?.setWindowAnimations(R.style.ZashiBottomSheetDialogAnimation)
    }
}

@Composable
private fun HookupKeyboardController() {
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val keyboardManager = LocalKeyboardManager.current
    DisposableEffect(softwareKeyboardController, keyboardManager) {
        if (softwareKeyboardController != null) {
            keyboardManager.onDialogOpened(softwareKeyboardController)
        }

        onDispose {
            if (softwareKeyboardController != null) {
                keyboardManager.onDialogClosed(softwareKeyboardController)
            }
        }
    }
}

@Composable
@ExperimentalMaterial3Api
fun rememberScreenModalBottomSheetState(
    initialValue: SheetValue = if (LocalInspectionMode.current) Expanded else Hidden,
    skipHiddenState: Boolean = LocalInspectionMode.current,
    skipPartiallyExpanded: Boolean = true,
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState =
    rememberSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
        confirmValueChange = confirmValueChange,
        initialValue = initialValue,
        skipHiddenState = skipHiddenState,
    )
