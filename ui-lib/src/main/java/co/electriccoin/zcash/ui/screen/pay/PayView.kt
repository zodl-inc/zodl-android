package co.electriccoin.zcash.ui.screen.pay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.AssetCardState
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ChipButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiAddressTextField
import co.electriccoin.zcash.ui.design.component.ZashiAssetCard
import co.electriccoin.zcash.ui.design.component.ZashiAutoSizeText
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiChipButton
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiImageButton
import co.electriccoin.zcash.ui.design.component.ZashiInfoText
import co.electriccoin.zcash.ui.design.component.ZashiNumberTextField
import co.electriccoin.zcash.ui.design.component.ZashiNumberTextFieldDefaults
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicNumber
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.design.util.styledStringResource
import co.electriccoin.zcash.ui.fixture.BalanceStateFixture
import co.electriccoin.zcash.ui.fixture.ZashiMainTopAppBarStateFixture
import co.electriccoin.zcash.ui.screen.balances.BalanceWidget
import co.electriccoin.zcash.ui.screen.balances.BalanceWidgetState
import co.electriccoin.zcash.ui.screen.send.view.SendAddressBookHint
import co.electriccoin.zcash.ui.screen.swap.SlippageButton
import co.electriccoin.zcash.ui.screen.swap.SwapErrorFooter
import co.electriccoin.zcash.ui.screen.swap.SwapErrorFooterState

@Composable
internal fun PayView(
    state: PayState,
    balanceState: BalanceWidgetState,
    appBarState: ZashiMainTopAppBarState
) {
    BlankBgScaffold(
        topBar = { TopAppBar(state, appBarState) }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(it)
        ) {
            Spacer(8.dp)
            BalanceWidget(
                modifier = Modifier.align(CenterHorizontally),
                state = balanceState
            )
            Spacer(40.dp)
            Text(
                stringResource(R.string.send_address_label),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary
            )
            Spacer(12.dp)
            ZashiAssetCard(state.asset)
            Spacer(28.dp)
            AddressTextField(
                state = state
            )
            AnimatedVisibility(visible = state.isABHintVisible) {
                Column {
                    Spacer(8.dp)
                    SendAddressBookHint(Modifier.fillMaxWidth())
                }
            }
            Spacer(22.dp)
            Text(
                stringResource(R.string.send_amount_label),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary
            )
            Spacer(10.dp)
            AmountTextFields(state)
            if (state.amountError != null && state.amountError.getValue().isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.amountError.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Inputs.ErrorDefault.hint
                )
            }
            Spacer(28.dp)
            ZecAmountText(state)
            Spacer(12.dp)
            SlippageButton(state.slippage)
            Spacer(1f)
            Spacer(12.dp)
            if (state.errorFooter != null) {
                SwapErrorFooter(state.errorFooter)
                Spacer(32.dp)
            } else if (state.infoFooter != null) {
                ZashiInfoText(text = state.infoFooter.getValue())
                Spacer(24.dp)
            }
            if (state.primaryButton != null) {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.primaryButton
                )
            }
        }
    }
}

@Composable
private fun ZecAmountText(state: PayState) {
    Row(
        verticalAlignment = CenterVertically
    ) {
        Box {
            Image(
                modifier = Modifier.size(24.dp),
                painter = painterResource(R.drawable.ic_zec_round_full),
                contentDescription = null
            )
            Image(
                modifier =
                    Modifier
                        .size(14.dp)
                        .align(BottomEnd)
                        .offset(4.dp, 3.dp),
                painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_zec_shielded),
                contentDescription = null,
            )
        }
        Spacer(8.dp)
        Text(
            text = stringResource(cash.z.ecc.sdk.ext.R.string.zcash_token_zec),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(1f)
        Text(
            text = state.zecAmount.getValue(),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AmountTextFields(state: PayState) {
    Row(
        verticalAlignment = CenterVertically
    ) {
        val amountInteractionSource = remember { MutableInteractionSource() }
        val isAmountFocused by amountInteractionSource.collectIsFocusedAsState()

        ZashiNumberTextField(
            modifier = Modifier.weight(1f),
            state = state.amount,
            interactionSource = amountInteractionSource,
            placeholder =
                if (!isAmountFocused) {
                    {
                        val assetTicker = (state.asset as? AssetCardState.Data)?.token
                        val placeholderText = assetTicker ?: stringResByDynamicNumber(0)
                        ZashiNumberTextFieldDefaults.Placeholder(
                            modifier = Modifier.fillMaxWidth(),
                            style =
                                ZashiTypography.textMd.copy(
                                    color = ZashiColors.Inputs.Default.text
                                ),
                            fontWeight = FontWeight.Normal,
                            text = placeholderText.getValue()
                        )
                    }
                } else {
                    null
                },
        )

        Spacer(12.dp)
        Image(
            painter = painterResource(R.drawable.ic_send_convert),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color = ZcashTheme.colors.secondaryColor),
        )
        Spacer(12.dp)

        ZashiNumberTextField(
            modifier = Modifier.weight(1f),
            state = state.amountFiat,
            placeholder = {
                ZashiNumberTextFieldDefaults.Placeholder(
                    modifier = Modifier.fillMaxWidth(),
                    style =
                        ZashiTypography.textMd.copy(
                            color = ZashiColors.Inputs.Default.text
                        ),
                    fontWeight = FontWeight.Normal,
                    text = stringResource(R.string.send_usd_amount_hint)
                )
            },
            prefix = {
                Image(
                    painter = painterResource(R.drawable.ic_send_usd),
                    contentDescription = null,
                    colorFilter =
                        ColorFilter.tint(
                            if (
                                state.amountFiat.innerState.innerTextFieldState.value
                                    .getValue()
                                    .isNotEmpty()
                            ) {
                                ZashiColors.Inputs.Filled.text
                            } else {
                                ZashiColors.Inputs.Filled.iconMain
                            }
                        )
                )
            }
        )
    }
}

@Composable
private fun TopAppBar(state: PayState, appBarState: ZashiMainTopAppBarState) {
    ZashiSmallTopAppBar(
        content = {
            ZashiAutoSizeText(
                text = "CROSSPAY",
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
                maxLines = 1
            )
        },
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = state.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        regularActions = {
            ZashiIconButton(
                state = appBarState.balanceVisibilityButton,
                modifier = Modifier.size(40.dp)
            )
            Spacer(4.dp)
            ZashiIconButton(state = state.info)
            Spacer(20.dp)
        },
    )
}

@Composable
private fun AddressTextField(
    state: PayState,
    modifier: Modifier = Modifier
) {
    ZashiAddressTextField(
        state = state.address,
        modifier =
            modifier
                .fillMaxWidth()
                .onKeyEvent {
                    if (state.abContact != null && it.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DEL) {
                        state.abContact.onClick()
                        true
                    } else {
                        false
                    }
                },
        placeholder =
            if (state.abContact == null) {
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
            if (state.abContact == null) {
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
                            state.abContact,
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
                    state = state.abButton
                )
                Spacer(4.dp)
                ZashiImageButton(
                    modifier = Modifier.size(36.dp),
                    state = state.qrButton
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
        PayView(
            appBarState = ZashiMainTopAppBarStateFixture.new(),
            balanceState = BalanceStateFixture.new(),
            state =
                PayState(
                    asset =
                        AssetCardState.Data(
                            token = stringRes("USDT"),
                            bigIcon = null,
                            smallIcon = null,
                            isEnabled = true,
                            onClick = {}
                        ),
                    amount = NumberTextFieldState {},
                    amountFiat = NumberTextFieldState {},
                    info = IconButtonState(R.drawable.ic_help) {},
                    onBack = {},
                    abButton =
                        IconButtonState(
                            icon = R.drawable.send_address_book,
                            onClick = {}
                        ),
                    address = TextFieldState(stringRes("")) {},
                    abContact =
                        ChipButtonState(
                            text = stringRes("Contact"),
                            onClick = {},
                            endIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chip_close
                        ),
                    qrButton =
                        IconButtonState(
                            icon = R.drawable.qr_code_icon,
                            onClick = {}
                        ),
                    zecAmount = styledStringResource(stringResByNumber(1)),
                    slippage =
                        ButtonState(
                            stringRes("1%"),
                            trailingIcon = R.drawable.ic_swap_slippage
                        ),
                    errorFooter = null,
                    primaryButton =
                        ButtonState(
                            stringRes("Primary"),
                        ),
                    isABHintVisible = true,
                    addressPlaceholder = stringRes(co.electriccoin.zcash.ui.design.R.string.general_enter_address),
                    infoFooter = stringRes("Info footer")
                )
        )
    }
}

@PreviewScreens
@Composable
private fun ErrorPreview() {
    ZcashTheme {
        PayView(
            appBarState = ZashiMainTopAppBarStateFixture.new(),
            balanceState = BalanceStateFixture.new(),
            state =
                PayState(
                    asset =
                        AssetCardState.Data(
                            token = stringRes("USDT"),
                            bigIcon = null,
                            smallIcon = null,
                            isEnabled = true,
                            onClick = {}
                        ),
                    amount = NumberTextFieldState {},
                    amountFiat = NumberTextFieldState {},
                    info = IconButtonState(R.drawable.ic_help) {},
                    onBack = {},
                    abButton =
                        IconButtonState(
                            icon = R.drawable.send_address_book,
                            onClick = {}
                        ),
                    address = TextFieldState(stringRes("")) {},
                    abContact =
                        ChipButtonState(
                            text = stringRes("Contact"),
                            onClick = {},
                            endIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chip_close
                        ),
                    qrButton =
                        IconButtonState(
                            icon = R.drawable.qr_code_icon,
                            onClick = {}
                        ),
                    zecAmount = styledStringResource(stringResByNumber(1)),
                    slippage =
                        ButtonState(
                            stringRes("1%"),
                            trailingIcon = R.drawable.ic_swap_slippage
                        ),
                    errorFooter =
                        SwapErrorFooterState(
                            title = stringRes("Unexpected error"),
                            subtitle = stringRes("Please check your connection and try again."),
                        ),
                    primaryButton = null,
                    isABHintVisible = true,
                    addressPlaceholder = stringRes(co.electriccoin.zcash.ui.design.R.string.general_enter_address),
                    infoFooter = stringRes("Info footer")
                )
        )
    }
}
