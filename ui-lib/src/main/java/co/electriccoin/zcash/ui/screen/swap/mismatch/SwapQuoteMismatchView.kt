package co.electriccoin.zcash.ui.screen.swap.mismatch

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
import co.electriccoin.zcash.ui.design.component.Spacer
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
fun SwapQuoteMismatchView(
    state: SwapQuoteMismatchState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    state ?: return
    InfoBottomSheetView(
        onBack = state.onBack,
        primaryButton = state.reportButton,
        secondaryButton = state.goBackButton,
        sheetState = sheetState,
    ) {
        Image(painterResource(R.drawable.ic_swap_quote_error), contentDescription = null)
        Spacer(12.dp)
        Text(
            text = state.title.getValue(),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
        )
        state.paragraphs.forEach { paragraph ->
            Spacer(8.dp)
            Text(
                text = paragraph.getValue(),
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textSm,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        SwapQuoteMismatchView(
            state =
                SwapQuoteMismatchState(
                    title = stringRes(R.string.swap_mismatch_title),
                    paragraphs =
                        listOf(
                            stringRes(R.string.swap_mismatch_msg_1),
                            stringRes(R.string.swap_mismatch_msg_2),
                            stringRes(R.string.swap_mismatch_msg_3)
                        ),
                    goBackButton = ButtonState(text = stringRes(R.string.swap_mismatch_goBack)),
                    reportButton = ButtonState(text = stringRes(R.string.send_report)),
                    onBack = {}
                )
        )
    }
