package co.electriccoin.zcash.ui.screen.swap.refundwarning

import androidx.compose.foundation.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiCheckbox
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.InfoBottomSheetView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapRefundWarningView(
    state: SwapRefundWarningState,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    InfoBottomSheetView(
        onBack = state.onBack,
        primaryButton = state.continueButton,
        secondaryButton = state.cancelButton,
        sheetState = sheetState,
    ) {
        Image(painterResource(R.drawable.ic_swap_refund_warning), contentDescription = null)
        Spacer(12.dp)
        Text(
            text = state.title.getValue(),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(8.dp)
        Text(
            text = state.message.getValue(),
            color = ZashiColors.Text.textTertiary,
            style = ZashiTypography.textSm,
        )
        Spacer(24.dp)
        ZashiCheckbox(state = state.checkbox)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        SwapRefundWarningView(
            state =
                SwapRefundWarningState(
                    title = stringRes(R.string.swap_refund_warning_title),
                    message = stringRes(R.string.swap_refund_warning_message_swap),
                    checkbox =
                        CheckboxState(
                            title = stringRes(R.string.swap_refund_warning_checkbox),
                            isChecked = false,
                            onClick = {}
                        ),
                    cancelButton =
                        ButtonState(text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel)),
                    continueButton = ButtonState(text = stringRes(R.string.swap_refund_warning_continue)),
                    onBack = {}
                )
        )
    }
