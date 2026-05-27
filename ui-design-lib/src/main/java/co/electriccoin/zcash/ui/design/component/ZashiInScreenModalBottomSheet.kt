package co.electriccoin.zcash.ui.design.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : ModalBottomSheetState> ZashiInScreenModalBottomSheet(
    state: T?,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberInScreenModalBottomSheetState(),
    shape: Shape = ZashiModalBottomSheetDefaults.SheetShape,
    containerColor: Color = ZashiModalBottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { ZashiModalBottomSheetDragHandle() },
    content: @Composable ColumnScope.(T) -> Unit = {},
) {
    var normalizedState: T? by remember { mutableStateOf(state) }

    normalizedState?.let {
        ZashiModalBottomSheet(
            onDismissRequest = { it.onBack() },
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = containerColor,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
            dragHandle = dragHandle
        ) {
            BackHandler { it.onBack() }
            content(it)
            Spacer(24.dp)
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            LaunchedEffect(Unit) {
                sheetState.show()
            }
        }
    }

    LaunchedEffect(state) {
        if (state == null) {
            sheetState.hide()
        }

        normalizedState = state
    }
}

@Composable
@ExperimentalMaterial3Api
fun rememberInScreenModalBottomSheetState(
    initialValue: SheetValue = if (LocalInspectionMode.current) Expanded else Hidden,
    skipHiddenState: Boolean = LocalInspectionMode.current,
    skipPartiallyExpanded: Boolean = true,
    confirmValueChange: (SheetValue) -> Boolean = { true },
) = rememberSheetState(
    skipPartiallyExpanded = skipPartiallyExpanded,
    confirmValueChange = confirmValueChange,
    initialValue = initialValue,
    skipHiddenState = skipHiddenState,
)

interface ModalBottomSheetState {
    val onBack: () -> Unit
}
