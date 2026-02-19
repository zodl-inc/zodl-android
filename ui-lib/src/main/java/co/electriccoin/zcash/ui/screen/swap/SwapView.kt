package co.electriccoin.zcash.ui.screen.swap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.AssetCardState
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ChipButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiAddressTextField
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiChipButton
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiImageButton
import co.electriccoin.zcash.ui.design.component.ZashiInfoText
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.listitem.SimpleListItemState
import co.electriccoin.zcash.ui.design.component.listitem.ZashiSimpleListItem
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.screen.swap.SwapState.AddressLocation.BOTTOM
import co.electriccoin.zcash.ui.screen.swap.SwapState.AddressLocation.TOP
import co.electriccoin.zcash.ui.screen.swap.ui.SwapAmountText
import co.electriccoin.zcash.ui.screen.swap.ui.SwapAmountTextField
import co.electriccoin.zcash.ui.screen.swap.ui.SwapAmountTextFieldState
import co.electriccoin.zcash.ui.screen.swap.ui.SwapAmountTextState

@Composable
internal fun SwapView(state: SwapState, onSideEffect: (amountFocusRequester: FocusRequester) -> Unit = { }) {
    val amountFocusRequester = remember { FocusRequester() }

    BlankBgScaffold(
        topBar = { TopAppBar(state) }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(it)
        ) {
            SwapAmountTextField(
                state = state.amountTextField,
                focusRequester = amountFocusRequester
            )

            if (state.addressLocation == TOP) {
                Spacer(10.dp)
                AddressTextField(state = state)
                Spacer(16.dp)
            } else {
                Spacer(16.dp)
            }

            SlippageSeparator(
                state = state
            )
            Spacer(14.dp)
            SwapAmountText(state = state.amountText)

            if (state.addressLocation == BOTTOM) {
                Spacer(10.dp)
                AddressTextField(state = state)
            }

            Spacer(22.dp)

            SlippageButton(
                state = state.slippage
            )

            state.infoItems.forEach { infoItem ->
                Spacer(16.dp)
                ZashiSimpleListItem(
                    state = infoItem
                )
            }
            Spacer(24.dp)
            Spacer(1f)
            if (state.errorFooter != null) {
                SwapErrorFooter(state.errorFooter)
                Spacer(32.dp)
            } else if (state.infoFooter != null) {
                ZashiInfoText(
                    text = state.infoFooter.getValue()
                )
                Spacer(24.dp)
            }
            if (state.primaryButton != null) {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.primaryButton
                )
            }
        }

        SideEffect {
            onSideEffect(amountFocusRequester)
        }
    }
}

@Composable
fun SwapErrorFooter(errorFooter: SwapErrorFooterState) {
    Column {
        Image(
            modifier =
                Modifier
                    .size(16.dp)
                    .align(Alignment.CenterHorizontally),
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ZashiColors.Text.textError)
        )
        Spacer(8.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = errorFooter.title.getValue(),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textError,
            textAlign = TextAlign.Center
        )
        Spacer(4.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = errorFooter.subtitle.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textError,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SlippageButton(state: ButtonState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = CenterVertically
    ) {
        Text(
            text = stringResource(R.string.swap_slippage_tolerance),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(1f)
        ZashiButton(
            state = state,
            contentPadding = PaddingValues(start = 10.dp, end = 12.dp),
            defaultPrimaryColors = ZashiButtonDefaults.tertiaryColors()
        )
    }
}

@Composable
private fun SlippageSeparator(
    state: SwapState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = CenterVertically
    ) {
        ZashiHorizontalDivider(
            modifier = Modifier.weight(1f),
            color = ZashiColors.Utility.Gray.utilityGray100
        )

        ZashiImageButton(state.changeModeButton)

        ZashiHorizontalDivider(
            modifier = Modifier.weight(1f),
            color = ZashiColors.Utility.Gray.utilityGray100
        )
    }
}

@Composable
private fun TopAppBar(state: SwapState) {
    ZashiSmallTopAppBar(
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.swap_title).uppercase(),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
            }
        },
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = state.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        regularActions = {
            ZashiIconButton(state.swapInfoButton)
            Spacer(20.dp)
        },
    )
}

@Composable
private fun ColumnScope.AddressTextField(state: SwapState) {
    Row(
        modifier =
            Modifier then
                if (state.onAddressClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        onClick = state.onAddressClick,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
                },
        verticalAlignment = CenterVertically
    ) {
        Text(
            text =
                when (state.addressLocation) {
                    TOP -> stringResource(R.string.swap_refund_address)
                    BOTTOM -> stringResource(co.electriccoin.zcash.ui.design.R.string.general_address)
                },
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium
        )
        if (state.onAddressClick != null) {
            Spacer(4.dp)
            Image(
                modifier = Modifier.size(16.dp),
                painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                contentDescription = null,
            )
        }
    }
    Spacer(6.dp)
    ZashiAddressTextField(
        state = state.address,
        modifier =
            Modifier
                .fillMaxWidth()
                .onKeyEvent {
                    if (state.addressContact != null && it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DEL) {
                        state.addressContact.onClick()
                        true
                    } else {
                        false
                    }
                },
        placeholder =
            if (state.addressContact == null) {
                {
                    Text(
                        text = state.addressPlaceholder.getValue(),
                        style = ZashiTypography.textMd,
                        color = ZashiColors.Inputs.Default.text
                    )
                }
            } else {
                null
            },
        prefix =
            if (state.addressContact == null) {
                null
            } else {
                {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .padding(top = 3.5.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        ZashiChipButton(
                            state.addressContact,
                            contentPadding = PaddingValues(start = 10.dp, top = 4.5.dp, end = 4.5.dp, bottom = 4.5.dp),
                            useTint = false,
                            shape = RoundedCornerShape(6.dp),
                            color = ZashiColors.Tags.surfacePrimary,
                            border = BorderStroke(1.dp, ZashiColors.Tags.surfaceStroke),
                            textStyle =
                                ZashiTypography.textSm.copy(
                                    color = ZashiColors.Text.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                        )
                    }
                }
            },
        suffix = {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                ZashiImageButton(
                    modifier = Modifier.size(36.dp),
                    state = state.addressBookButton
                )
                Spacer(4.dp)
                ZashiImageButton(
                    modifier = Modifier.size(36.dp),
                    state = state.qrScannerButton
                )
            }
        },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
    )
}

@PreviewScreens
@Composable
private fun Preview() {
    ZcashTheme {
        SwapView(
            state =
                SwapState(
                    swapInfoButton = IconButtonState(R.drawable.ic_help) {},
                    amountTextField =
                        SwapAmountTextFieldState(
                            title = stringRes("From"),
                            error = null,
                            token =
                                AssetCardState.Data(
                                    token = stringRes("USDT"),
                                    bigIcon = null,
                                    smallIcon = null,
                                    isEnabled = false,
                                    onClick = {}
                                ),
                            textFieldPrefix = imageRes(R.drawable.ic_send_zashi),
                            textField = NumberTextFieldState {},
                            secondaryText = stringResByDynamicCurrencyNumber(100, "USDT"),
                            max =
                                ButtonState(
                                    stringResByDynamicCurrencyNumber(100, "$")
                                ),
                            onSwapChange = {}
                        ),
                    slippage =
                        ButtonState(
                            stringRes("1%"),
                            trailingIcon = R.drawable.ic_swap_slippage
                        ),
                    amountText =
                        SwapAmountTextState(
                            token =
                                AssetCardState.Data(
                                    token = stringRes("ZEC"),
                                    bigIcon = null,
                                    smallIcon = null,
                                    isEnabled = false,
                                    onClick = {}
                                ),
                            title = stringRes("To"),
                            text = stringResByDynamicCurrencyNumber(101, "$"),
                            secondaryText = stringResByDynamicCurrencyNumber(2.47123, "ZEC"),
                            subtitle = null
                        ),
                    infoItems =
                        listOf(
                            SimpleListItemState(
                                title = stringRes("Rate"),
                                text = stringRes("1 ZEC = 51.74 USDC")
                            )
                        ),
                    addressContact =
                        ChipButtonState(
                            text = stringRes("Contact"),
                            onClick = {},
                            endIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chip_close
                        ),
                    addressBookButton =
                        IconButtonState(
                            icon = R.drawable.send_address_book,
                            onClick = {}
                        ),
                    addressLocation = BOTTOM,
                    onAddressClick = null,
                    address = TextFieldState(stringRes("")) {},
                    addressPlaceholder = stringRes(co.electriccoin.zcash.ui.design.R.string.general_enter_address),
                    qrScannerButton =
                        IconButtonState(
                            icon = R.drawable.qr_code_icon,
                            onClick = {}
                        ),
                    errorFooter = null,
                    primaryButton =
                        ButtonState(
                            stringRes("Get a quote")
                        ),
                    infoFooter =
                        stringRes(
                            "NEAR only supports swaps to a transparent address. " +
                                "Zashi will prompt you to shield your funds upon receipt."
                        ),
                    onBack = {},
                    changeModeButton = IconButtonState(R.drawable.ic_swap_change_mode) {}
                )
        )
    }
}

@PreviewScreens
@Composable
private fun UnexpectedErrorPreview() {
    ZcashTheme {
        SwapView(
            state =
                SwapState(
                    swapInfoButton = IconButtonState(R.drawable.ic_help) {},
                    amountTextField =
                        SwapAmountTextFieldState(
                            title = stringRes("From"),
                            error = null,
                            token =
                                AssetCardState.Data(
                                    token = stringRes("USDT"),
                                    bigIcon = null,
                                    smallIcon = null,
                                    isEnabled = false,
                                    onClick = {}
                                ),
                            textFieldPrefix = imageRes(R.drawable.ic_send_zashi),
                            textField = NumberTextFieldState {},
                            secondaryText = stringResByDynamicCurrencyNumber(100, "USDT"),
                            max =
                                ButtonState(
                                    stringResByDynamicCurrencyNumber(100, "$")
                                ),
                            onSwapChange = {}
                        ),
                    slippage =
                        ButtonState(
                            stringRes("1%"),
                            trailingIcon = R.drawable.ic_swap_slippage
                        ),
                    amountText =
                        SwapAmountTextState(
                            token =
                                AssetCardState.Data(
                                    token = stringRes("ZEC"),
                                    bigIcon = null,
                                    smallIcon = null,
                                    isEnabled = false,
                                    onClick = {}
                                ),
                            title = stringRes("To"),
                            text = stringResByDynamicCurrencyNumber(101, "$"),
                            secondaryText = stringResByDynamicCurrencyNumber(2.47123, "ZEC"),
                            subtitle = null
                        ),
                    infoItems =
                        listOf(
                            SimpleListItemState(
                                title = stringRes("Rate"),
                                text = stringRes("1 ZEC = 51.74 USDC")
                            )
                        ),
                    addressBookButton =
                        IconButtonState(
                            icon = R.drawable.send_address_book,
                            onClick = {}
                        ),
                    addressLocation = BOTTOM,
                    onAddressClick = null,
                    address = TextFieldState(stringRes("")) {},
                    addressPlaceholder = stringRes(co.electriccoin.zcash.ui.design.R.string.general_enter_address),
                    qrScannerButton =
                        IconButtonState(
                            icon = R.drawable.qr_code_icon,
                            onClick = {}
                        ),
                    errorFooter =
                        SwapErrorFooterState(
                            title = stringRes("Unexpected error"),
                            subtitle = stringRes("Please check your connection and try again."),
                        ),
                    primaryButton =
                        ButtonState(
                            stringRes("Try again"),
                            style = ButtonStyle.DESTRUCTIVE1
                        ),
                    infoFooter = null,
                    onBack = {},
                    changeModeButton = IconButtonState(R.drawable.ic_swap_change_mode) {}
                )
        )
    }
}

@PreviewScreens
@Composable
private fun ServiceUnavailableErrorPreview() {
    ZcashTheme {
        SwapView(
            state =
                SwapState(
                    swapInfoButton = IconButtonState(R.drawable.ic_help) {},
                    amountTextField =
                        SwapAmountTextFieldState(
                            title = stringRes("From"),
                            error = null,
                            token =
                                AssetCardState.Data(
                                    token = stringRes("USDT"),
                                    bigIcon = null,
                                    smallIcon = null,
                                    isEnabled = false,
                                    onClick = {}
                                ),
                            textFieldPrefix = imageRes(R.drawable.ic_send_zashi),
                            textField = NumberTextFieldState {},
                            secondaryText = stringResByDynamicCurrencyNumber(100, "USDT"),
                            max =
                                ButtonState(
                                    stringResByDynamicCurrencyNumber(100, "$")
                                ),
                            onSwapChange = {}
                        ),
                    slippage =
                        ButtonState(
                            stringRes("1%"),
                            trailingIcon = R.drawable.ic_swap_slippage
                        ),
                    amountText =
                        SwapAmountTextState(
                            token =
                                AssetCardState.Data(
                                    token = stringRes("ZEC"),
                                    bigIcon = null,
                                    smallIcon = null,
                                    isEnabled = false,
                                    onClick = {}
                                ),
                            title = stringRes("To"),
                            text = stringResByDynamicCurrencyNumber(101, "$"),
                            secondaryText = stringResByDynamicCurrencyNumber(2.47123, "ZEC"),
                            subtitle = null
                        ),
                    infoItems =
                        listOf(
                            SimpleListItemState(
                                title = stringRes("Rate"),
                                text = stringRes("1 ZEC = 51.74 USDC")
                            )
                        ),
                    addressBookButton =
                        IconButtonState(
                            icon = R.drawable.send_address_book,
                            onClick = {}
                        ),
                    addressLocation = BOTTOM,
                    onAddressClick = null,
                    address = TextFieldState(stringRes("")) {},
                    addressPlaceholder = stringRes(co.electriccoin.zcash.ui.design.R.string.general_enter_address),
                    qrScannerButton =
                        IconButtonState(
                            icon = R.drawable.qr_code_icon,
                            onClick = {}
                        ),
                    errorFooter =
                        SwapErrorFooterState(
                            title = stringRes("The service is unavailable"),
                            subtitle = stringRes("Please try again later."),
                        ),
                    primaryButton = null,
                    infoFooter = null,
                    onBack = {},
                    changeModeButton = IconButtonState(R.drawable.ic_swap_change_mode) {}
                )
        )
    }
}
