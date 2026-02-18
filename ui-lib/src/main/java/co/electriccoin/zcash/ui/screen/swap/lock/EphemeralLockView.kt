package co.electriccoin.zcash.ui.screen.swap.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteInfo
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EphemeralLockView(state: EphemeralLockState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        content = { state, contentPadding ->
            Content(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, false),
                state = state,
                contentPadding = contentPadding
            )
        }
    )
}

@Suppress("MagicNumber")
@Composable
private fun Content(
    state: EphemeralLockState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = contentPadding.calculateBottomPadding()
                )
    ) {
        Image(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            painter = painterResource(R.drawable.ic_swap_quote_error),
            contentDescription = null
        )
        Spacer(12.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Action Required",
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(12.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text =
                "Zodl uses temporary addresses during swaps and payments to protect your privacy. If too many " +
                    "remain unused, Zodl hits a limit and can’t create new ones.",
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(12.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text =
                "To keep everything working smoothly, at least one of the generated addresses needs to receive " +
                    "funds. Completing this internal wallet transaction will get your swaps and payments unblocked.",
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(24.dp)
        state.items.forEachIndexed { index, item ->
            if (index != 0) {
                Spacer(12.dp)
            }
            SwapQuoteInfo(item)
        }
        Spacer(12.dp)
        ZashiHorizontalDivider()
        Spacer(12.dp)
        SwapQuoteInfo(
            item = state.amount,
            descriptionStyle = ZashiTypography.textSm,
            descriptionFontWeight = FontWeight.Medium,
            descriptionColor = ZashiColors.Text.textPrimary
        )
        Spacer(32.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.secondaryButton,
            defaultPrimaryColors = ZashiButtonDefaults.destructive1Colors()
        )
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.primaryButton
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        EphemeralLockView(
            state =
                EphemeralLockState(
                    items =
                        listOf(
                            SwapQuoteInfoItem(
                                description = stringRes("Send to"),
                                title = stringResByAddress("Asdwae12easdasd"),
                            ),
                            SwapQuoteInfoItem(
                                description = stringRes("Amount"),
                                title = stringRes(Zatoshi(1231234)),
                            ),
                            SwapQuoteInfoItem(
                                description = stringRes("Fee"),
                                title = stringRes(Zatoshi(1231234)),
                            )
                        ),
                    amount =
                        SwapQuoteInfoItem(
                            description = stringRes("Total amount"),
                            title = stringRes(Zatoshi(123213)),
                            subtitle = stringResByDynamicCurrencyNumber(12312, "$")
                        ),
                    secondaryButton =
                        ButtonState(
                            text = stringRes("Cancel"),
                            onClick = {}
                        ),
                    primaryButton =
                        ButtonState(
                            text = stringRes("Confirm Transaction"),
                            onClick = {}
                        ),
                    onBack = {},
                )
        )
    }
