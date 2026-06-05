package co.electriccoin.zcash.ui.design.component

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import co.electriccoin.zcash.ui.design.LocalSheetStateManager
import co.electriccoin.zcash.ui.design.util.LocalNavRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : ModalBottomSheetState> ZashiScreenModalBottomSheet(
    state: T?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
    shape: Shape = ZashiModalBottomSheetDefaults.SheetShape,
    containerColor: Color = ZashiModalBottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { ZashiModalBottomSheetDragHandle() },
    content: @Composable ColumnScope.(state: T, contentPadding: PaddingValues) -> Unit = { _, _ -> },
) {
    val parent = LocalView.current.parent
    SideEffect {
        (parent as? DialogWindowProvider)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        (parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
    }

    state?.let {
        ZashiModalBottomSheet(
            sheetState = sheetState,
            shape = shape,
            containerColor = containerColor,
            dragHandle = dragHandle,
            content = {
                BackHandler {
                    it.onBack()
                }
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
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZashiScreenModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
    shape: Shape = ZashiModalBottomSheetDefaults.SheetShape,
    containerColor: Color = ZashiModalBottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.(contentPadding: PaddingValues) -> Unit = {},
) {
    ZashiScreenModalBottomSheet(
        state =
            remember(onDismissRequest) {
                object : ModalBottomSheetState {
                    override val onBack: () -> Unit = {
                        onDismissRequest()
                    }
                }
            },
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        content = { _, contentPadding ->
            content(contentPadding)
            HookupKeyboardController()
        },
    )
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
): SheetState {
    val sheetManager = LocalSheetStateManager.current
    val sheetState =
        rememberSheetState(
            skipPartiallyExpanded = skipPartiallyExpanded,
            confirmValueChange = confirmValueChange,
            initialValue = initialValue,
            skipHiddenState = skipHiddenState,
        )
    val route = LocalNavRoute.current
    DisposableEffect(sheetState) {
        sheetManager.onSheetOpened(sheetState, route)
        onDispose {
            sheetManager.onSheetDisposed(sheetState)
        }
    }
    return sheetState
}
