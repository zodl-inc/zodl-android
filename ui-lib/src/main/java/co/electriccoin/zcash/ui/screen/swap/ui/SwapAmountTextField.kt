package co.electriccoin.zcash.ui.screen.swap.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.AssetCardState
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.LottieProgress
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiAssetCard
import co.electriccoin.zcash.ui.design.component.ZashiAutoSizeText
import co.electriccoin.zcash.ui.design.component.ZashiNumberTextField
import co.electriccoin.zcash.ui.design.component.ZashiNumberTextFieldDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber

@Composable
internal fun SwapAmountTextField(
    state: SwapAmountTextFieldState,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    Column(modifier = modifier) {
        SwapTextFieldCard(
            modifier = Modifier.fillMaxWidth(),
            focusRequester = focusRequester,
            state = state
        )

        if (state.isError) {
            val error = state.error ?: state.textField.defaultNumberError

            if (error.getValue().isNotEmpty()) {
                Spacer(8.dp)
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = error.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Inputs.ErrorDefault.hint,
                    textAlign = TextAlign.End
                )
                Spacer(8.dp)
            }
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun SwapTextFieldCard(
    state: SwapAmountTextFieldState,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val textFieldInteractionSource = remember { MutableInteractionSource() }
    val isTextFieldFocused by textFieldInteractionSource.collectIsFocusedAsState()

    Surface(
        modifier = modifier,
        color = ZashiColors.Surfaces.bgPrimary
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Text.textPrimary
                )
                Spacer(1f)
                SpendableBalanceButton(state)
            }

            Spacer(8.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(.425f)
                ) {
                    ZashiAssetCard(
                        state = state.token
                    )
                }
                Spacer(modifier = Modifier.weight(.025f))
                ZashiNumberTextField(
                    state = state.textField,
                    interactionSource = textFieldInteractionSource,
                    modifier =
                        Modifier
                            .weight(.55f)
                            .focusRequester(focusRequester),
                    textStyle =
                        ZashiTypography.header4.copy(
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End
                        ),
                    contentPadding =
                        if (state.textFieldPrefix == null) {
                            PaddingValues(horizontal = 12.dp, vertical = 4.6.dp)
                        } else {
                            PaddingValues(start = 8.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
                        },
                    placeholder =
                        if (!isTextFieldFocused) {
                            {
                                ZashiNumberTextFieldDefaults.Placeholder(
                                    modifier = Modifier.fillMaxWidth(),
                                    style = ZashiTypography.header4,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.End,
                                    contentAlignment = CenterEnd
                                )
                            }
                        } else {
                            null
                        },
                    leadingIcon =
                        if (state.textFieldPrefix is ImageResource.ByDrawable) {
                            {
                                Image(
                                    modifier = Modifier.size(24.dp),
                                    painter = painterResource(state.textFieldPrefix.resource),
                                    contentDescription = null,
                                    colorFilter =
                                        ColorFilter.tint(
                                            if (
                                                state.textField.innerState.innerTextFieldState.value
                                                    .getValue()
                                                    .isNotEmpty()
                                            ) {
                                                ZashiColors.Text.textPrimary
                                            } else {
                                                ZashiColors.Text.textTertiary
                                            }
                                        )
                                )
                            }
                        } else {
                            null
                        },
                )
            }
            Spacer(8.dp)
            Row(
                verticalAlignment = CenterVertically
            ) {
                SelectionContainer(
                    modifier = Modifier.weight(1f),
                ) {
                    ZashiAutoSizeText(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        contentAlignment = CenterEnd,
                        text = state.secondaryText.getValue(),
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.Medium,
                        color = ZashiColors.Text.textTertiary,
                        maxLines = 1
                    )
                }
                Spacer(4.dp)
                Image(
                    modifier =
                        if (state.isSwapChangeEnabled) {
                            Modifier
                                .clickable(
                                    onClick = state.onSwapChange,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                        } else {
                            Modifier
                        },
                    painter = painterResource(R.drawable.ic_swap_recipient),
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun SpendableBalanceButton(state: SwapAmountTextFieldState) {
    if (state.max == null) return

    Row(
        modifier =
            Modifier.clickable(
                onClick = state.max.onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        ZashiAutoSizeText(
            text = state.max.text.getValue(),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = if (state.isError) ZashiColors.Inputs.ErrorDefault.hint else ZashiColors.Text.textTertiary,
            maxLines = 1
        )
        if (state.max.isLoading) {
            Spacer(2.dp)
            LottieProgress()
        }
    }
}

@Immutable
data class SwapAmountTextFieldState(
    val title: StringResource,
    val max: ButtonState?,
    val error: StringResource?,
    val token: AssetCardState,
    val textFieldPrefix: ImageResource?,
    val textField: NumberTextFieldState,
    val secondaryText: StringResource,
    val isSwapChangeEnabled: Boolean = true,
    val onSwapChange: () -> Unit,
) {
    val isError = error != null || textField.innerState.isError
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            SwapAmountTextField(
                state =
                    SwapAmountTextFieldState(
                        title = stringRes("From"),
                        error = null,
                        token =
                            AssetCardState.Data(
                                token = stringRes("USDT"),
                                bigIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_token_zec),
                                smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_chain_zec),
                                onClick = null
                            ),
                        textFieldPrefix = imageRes(R.drawable.ic_send_zashi),
                        textField = NumberTextFieldState {},
                        secondaryText = stringResByDynamicCurrencyNumber(100, "USDT"),
                        max =
                            ButtonState(
                                stringResByDynamicCurrencyNumber(100, "$")
                            ),
                        onSwapChange = {},
                    )
            )
        }
    }

@PreviewScreens
@Composable
private fun ErrorPreview() =
    ZcashTheme {
        BlankSurface {
            SwapAmountTextField(
                state =
                    SwapAmountTextFieldState(
                        title = stringRes("Recipient gets"),
                        error = stringRes("Error"),
                        token =
                            AssetCardState.Data(
                                token = stringRes("USDT"),
                                bigIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_token_zec),
                                smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_chain_zec),
                                onClick = null
                            ),
                        textFieldPrefix = imageRes(R.drawable.ic_send_zashi),
                        textField = NumberTextFieldState {},
                        secondaryText = stringResByDynamicCurrencyNumber(100, "USDT"),
                        max =
                            ButtonState(
                                stringResByDynamicCurrencyNumber(100, "$")
                            ),
                        onSwapChange = {},
                    )
            )
        }
    }
