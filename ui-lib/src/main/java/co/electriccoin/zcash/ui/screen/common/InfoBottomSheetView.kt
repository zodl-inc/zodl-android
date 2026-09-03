package co.electriccoin.zcash.ui.screen.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource

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
fun InfoBottomSheetView(
    onBack: () -> Unit,
    primaryButton: ButtonState? = null,
    secondaryButton: ButtonState? = null,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ZashiScreenModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = sheetState,
        dragHandle = null,
    ) { contentPadding ->
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
                            start = 24.dp,
                            end = 24.dp,
                            top = headerHeight,
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
            ) {
                content()
                if (primaryButton != null) {
                    Spacer(32.dp)
                    secondaryButton?.let {
                        ZashiButton(
                            state = it,
                            modifier = Modifier.fillMaxWidth(),
                            defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
                        )
                        Spacer(12.dp)
                    }
                    ZashiButton(
                        state = primaryButton,
                        modifier = Modifier.fillMaxWidth(),
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
