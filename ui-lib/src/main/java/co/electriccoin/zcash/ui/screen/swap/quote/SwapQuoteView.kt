package co.electriccoin.zcash.ui.screen.swap.quote

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.SwapQuoteHeaderState
import co.electriccoin.zcash.ui.design.component.SwapTokenAmountState
import co.electriccoin.zcash.ui.design.component.ZashiAutoSizeText
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiInfoText
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiSwapQuoteHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.balances.LocalBalancesAvailable
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwapQuoteView(state: SwapQuoteState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        content = { innerState, contentPadding ->
            when (innerState) {
                is SwapQuoteState.Success ->
                    Success(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, false),
                        state = innerState,
                        contentPadding = contentPadding
                    )

                is SwapQuoteState.Error ->
                    Error(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, false),
                        state = innerState,
                        contentPadding = contentPadding
                    )
            }
        }
    )
}

@Composable
private fun Error(
    state: SwapQuoteState.Error,
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
        if (state.icon is ImageResource.ByDrawable) {
            Image(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                painter = painterResource(state.icon.resource),
                contentDescription = null
            )
        }
        Spacer(8.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.title.getValue(),
            textAlign = TextAlign.Center,
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(8.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.subtitle.getValue(),
            textAlign = TextAlign.Center,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(32.dp)
        ZashiButton(
            state = state.negativeButton,
            modifier = Modifier.fillMaxWidth(),
            defaultPrimaryColors = ZashiButtonDefaults.destructive1Colors()
        )
        ZashiButton(
            state = state.positiveButton,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Suppress("MagicNumber")
@Composable
private fun Success(
    state: SwapQuoteState.Success,
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
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.title.getValue(),
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(24.dp)
        CompositionLocalProvider(LocalBalancesAvailable provides true) {
            ZashiSwapQuoteHeader(
                state =
                    SwapQuoteHeaderState(
                        from = state.from,
                        to = state.to
                    )
            )
        }
        Spacer(32.dp)
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
        if (state.infoText != null) {
            Spacer(48.dp)
            ZashiInfoText(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                textModifier = Modifier.padding(top = 4.dp),
                text = state.infoText.getValue()
            )
        }
        Spacer(24.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.primaryButton
        )
    }
}

@Composable
fun SwapQuoteInfo(
    item: SwapQuoteInfoItem,
    descriptionStyle: TextStyle = ZashiTypography.textSm,
    descriptionFontWeight: FontWeight = FontWeight.Normal,
    descriptionColor: Color = ZashiColors.Text.textTertiary
) {
    Row {
        Text(
            modifier = Modifier.weight(1f),
            text = item.description.getValue(),
            style = descriptionStyle,
            fontWeight = descriptionFontWeight,
            color = descriptionColor
        )
        Column(
            horizontalAlignment = Alignment.End
        ) {
            SelectionContainer {
                ZashiAutoSizeText(
                    text = item.title.getValue(),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Text.textPrimary,
                    maxLines = 1
                )
            }
            if (item.subtitle != null) {
                SelectionContainer {
                    ZashiAutoSizeText(
                        text = item.subtitle.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@PreviewScreens
@Composable
private fun SuccessPreview() =
    ZcashTheme {
        SwapQuoteView(
            state =
                SwapQuoteState.Success(
                    from =
                        SwapTokenAmountState(
                            bigIcon = imageRes(R.drawable.ic_zec_round_full),
                            smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_shielded),
                            amount = stringRes("0.0000004214"),
                            fiatAmount = stringResByDynamicCurrencyNumber(21312, "$"),
                            chain = stringRes("Chain"),
                            token = stringRes("Token")
                        ),
                    to =
                        SwapTokenAmountState(
                            bigIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_token_btc),
                            smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_token_btc),
                            amount = stringRes("0.0000004214"),
                            fiatAmount = stringResByDynamicCurrencyNumber(21312, "$"),
                            chain = stringRes("Chain"),
                            token = stringRes("Token")
                        ),
                    items =
                        listOf(
                            SwapQuoteInfoItem(
                                description = stringRes("Pay from"),
                                title = stringRes("Zodl"),
                                subtitle = null
                            ),
                            SwapQuoteInfoItem(
                                description = stringRes("Pay to"),
                                title = stringResByAddress("Asdwae12easdasd"),
                                subtitle = null
                            ),
                            SwapQuoteInfoItem(
                                description = stringRes("ZEC transaction fee"),
                                title = stringRes(Zatoshi(1231234)),
                                subtitle = null
                            ),
                            SwapQuoteInfoItem(
                                description = stringRes("Max slippage 1%"),
                                title = stringRes(Zatoshi(1231234)),
                                subtitle = stringResByDynamicCurrencyNumber(23, "$")
                            )
                        ),
                    amount =
                        SwapQuoteInfoItem(
                            description = stringRes("Total amount"),
                            title = stringRes(Zatoshi(123213)),
                            subtitle = stringResByDynamicCurrencyNumber(12312, "$")
                        ),
                    primaryButton =
                        ButtonState(
                            text = stringRes("Confirm"),
                            onClick = {}
                        ),
                    onBack = {},
                    rotateIcon = false,
                    infoText = stringRes("Total amount includes max slippage of 0.5%."),
                    title = stringRes("Pay now")
                )
        )
    }

@PreviewScreens
@Composable
private fun ErrorPreview() =
    ZcashTheme {
        SwapQuoteView(
            state =
                SwapQuoteState.Error(
                    icon = imageRes(R.drawable.ic_zec_round_full),
                    title = stringRes("Title"),
                    subtitle = stringRes("Subtitle"),
                    negativeButton =
                        ButtonState(
                            text = stringRes("Negative"),
                            onClick = {}
                        ),
                    positiveButton =
                        ButtonState(
                            text = stringRes("Positive"),
                            onClick = {}
                        ),
                    onBack = {}
                )
        )
    }
