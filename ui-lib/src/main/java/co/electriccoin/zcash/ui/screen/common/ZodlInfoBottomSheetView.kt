package co.electriccoin.zcash.ui.screen.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors

/**
 * Shared chrome for info/help bottom-sheet dialogs.
 *
 * Handles [ZashiScreenModalBottomSheet], scrollable [Column], and padding.
 * When [primaryButton] is non-null the shell renders it (and optionally [secondaryButton])
 * below a 32 dp spacer. Pass null to manage buttons yourself inside [content].
 *
 * The sheet's drag handle is replaced by a pinned [ZashiFrostedSheetHeader] band which [content]
 * scrolls underneath. The band carries the handle only: every caller supplies its own title from
 * inside [content], so the shell has no title element of its own to pin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZodlInfoBottomSheetView(
    onBack: (() -> Unit)? = null,
    primaryButton: ButtonState? = null,
    secondaryButton: ButtonState? = null,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
    contentPadding: PaddingValues = ZodlInfoBottomSheetDefaults.contentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    ZodlInfoBottomSheetView(
        state =
            remember(onBack) {
                onBack?.let { dismiss ->
                    object : ModalBottomSheetState {
                        override val onBack: () -> Unit = dismiss
                    }
                }
            },
        primaryButton = primaryButton,
        secondaryButton = secondaryButton,
        sheetState = sheetState,
        contentPadding = contentPadding,
        content = { content() },
    )
}

object ZodlInfoBottomSheetDefaults {
    val contentPadding = PaddingValues(horizontal = 24.dp)
}

/**
 * The sheet-as-screen variant: [state] null keeps the sheet hidden, otherwise [content] receives the
 * non-null state, and the wrapper wires [ModalBottomSheetState.onBack] to back press, scrim tap and drag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : ModalBottomSheetState> ZodlInfoBottomSheetView(
    state: T?,
    primaryButton: ButtonState? = null,
    secondaryButton: ButtonState? = null,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
    contentPadding: PaddingValues = ZodlInfoBottomSheetDefaults.contentPadding,
    content: @Composable ColumnScope.(state: T) -> Unit,
) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
        dragHandle = null,
    ) { innerState, sheetPadding ->
        val hazeState = rememberZashiFrostState()
        var headerHeight by remember { mutableStateOf(0.dp) }
        Box(modifier = Modifier.weight(1f, false)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostSource(hazeState)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = headerHeight,
                            bottom = sheetPadding.calculateBottomPadding(),
                        ),
            ) {
                Column(modifier = Modifier.padding(contentPadding)) {
                    content(innerState)
                }
                if (primaryButton != null) {
                    Spacer(36.dp)
                    secondaryButton?.let {
                        ZashiButton(
                            state = it,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(ZodlInfoBottomSheetDefaults.contentPadding),
                            defaultPrimaryColors =
                                ZashiButtonDefaults.secondaryColors(
                                    borderColor = ZashiColors.Btns.Secondary.btnSecondaryBorder
                                ),
                        )
                        Spacer(8.dp)
                    }
                    ZashiButton(
                        state = primaryButton,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(ZodlInfoBottomSheetDefaults.contentPadding),
                    )
                }
            }

            ZashiFrostedSheetHeader(
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                onHeightChanged = { headerHeight = it },
            )
        }
    }
}
