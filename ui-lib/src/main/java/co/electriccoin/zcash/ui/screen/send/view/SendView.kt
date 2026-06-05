@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.send.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.model.ZecSendExt
import cash.z.ecc.android.sdk.type.AddressType
import cash.z.ecc.sdk.type.ZcashCurrency
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiMainTopAppBarState
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppbar
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.AppAlertDialog
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiAutoSizeText
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldDefaults
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.RobotoMonoFontFamily
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.rememberDesiredFormatLocale
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringResByFiatDisplayName
import co.electriccoin.zcash.ui.screen.balances.BalanceWidget
import co.electriccoin.zcash.ui.screen.balances.BalanceWidgetState
import co.electriccoin.zcash.ui.screen.send.SendTag
import co.electriccoin.zcash.ui.screen.send.model.AmountState
import co.electriccoin.zcash.ui.screen.send.model.MemoState
import co.electriccoin.zcash.ui.screen.send.model.RecipientAddressState
import co.electriccoin.zcash.ui.screen.send.model.SendAddressBookState
import co.electriccoin.zcash.ui.screen.send.model.SendStage
import kotlinx.coroutines.launch

// TODO [#1260]: Cover Send screens UI with tests
// TODO [#1260]: https://github.com/Electric-Coin-Company/zashi-android/issues/1260
@Suppress("LongParameterList")
@Composable
fun Send(
    balanceWidgetState: BalanceWidgetState,
    sendStage: SendStage,
    onCreateZecSend: (ZecSend) -> Unit,
    onBack: () -> Unit,
    onQrScannerOpen: () -> Unit,
    hasCameraFeature: Boolean,
    recipientAddressState: RecipientAddressState,
    onRecipientAddressChange: (String) -> Unit,
    setAmountState: (AmountState) -> Unit,
    amountState: AmountState,
    setMemoState: (MemoState) -> Unit,
    memoState: MemoState,
    selectedAccount: WalletAccount?,
    exchangeRateState: ExchangeRateState,
    sendAddressBookState: SendAddressBookState,
    zashiMainTopAppBarState: ZashiMainTopAppBarState?,
) {
    if (selectedAccount == null) {
        return
    }

    BlankBgScaffold(topBar = {
        ZashiTopAppbar(
            title = null,
            state = zashiMainTopAppBarState,
            onBack = onBack
        )
    }) { paddingValues ->
        SendMainContent(
            balanceWidgetState = balanceWidgetState,
            selectedAccount = selectedAccount,
            exchangeRateState = exchangeRateState,
            onBack = onBack,
            onCreateZecSend = onCreateZecSend,
            sendStage = sendStage,
            onQrScannerOpen = onQrScannerOpen,
            recipientAddressState = recipientAddressState,
            onRecipientAddressChange = onRecipientAddressChange,
            hasCameraFeature = hasCameraFeature,
            amountState = amountState,
            setAmountState = setAmountState,
            memoState = memoState,
            setMemoState = setMemoState,
            sendState = sendAddressBookState,
            modifier = Modifier.scaffoldPadding(paddingValues)
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun SendMainContent(
    balanceWidgetState: BalanceWidgetState,
    selectedAccount: WalletAccount,
    exchangeRateState: ExchangeRateState,
    onBack: () -> Unit,
    onCreateZecSend: (ZecSend) -> Unit,
    sendStage: SendStage,
    onQrScannerOpen: () -> Unit,
    recipientAddressState: RecipientAddressState,
    onRecipientAddressChange: (String) -> Unit,
    hasCameraFeature: Boolean,
    amountState: AmountState,
    setAmountState: (AmountState) -> Unit,
    memoState: MemoState,
    setMemoState: (MemoState) -> Unit,
    sendState: SendAddressBookState,
    modifier: Modifier = Modifier,
) {
    // For now, we merge [SendStage.Form] and [SendStage.Proposing] into one stage. We could eventually display a
    // loader if calling the Proposal API takes longer than expected

    SendForm(
        balanceWidgetState = balanceWidgetState,
        selectedAccount = selectedAccount,
        recipientAddressState = recipientAddressState,
        exchangeRateState = exchangeRateState,
        onRecipientAddressChange = onRecipientAddressChange,
        amountState = amountState,
        setAmountState = setAmountState,
        memoState = memoState,
        setMemoState = setMemoState,
        onCreateZecSend = onCreateZecSend,
        onQrScannerOpen = onQrScannerOpen,
        hasCameraFeature = hasCameraFeature,
        sendState = sendState,
        modifier = modifier
    )

    if (sendStage is SendStage.SendFailure) {
        SendFailure(
            reason = sendStage.error,
            onConfirm = onBack
        )
    }
}

// TODO [#217]: Need to handle changing of Locale after user input, but before submitting the button.
// TODO [#217]: https://github.com/Electric-Coin-Company/zashi-android/issues/217

// TODO [#1257]: Send.Form TextFields not persisted on a configuration change when the underlying ViewPager is on the
//  Balances page
// TODO [#1257]: https://github.com/Electric-Coin-Company/zashi-android/issues/1257
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun SendForm(
    balanceWidgetState: BalanceWidgetState,
    selectedAccount: WalletAccount,
    recipientAddressState: RecipientAddressState,
    exchangeRateState: ExchangeRateState,
    onRecipientAddressChange: (String) -> Unit,
    amountState: AmountState,
    setAmountState: (AmountState) -> Unit,
    memoState: MemoState,
    setMemoState: (MemoState) -> Unit,
    onCreateZecSend: (ZecSend) -> Unit,
    onQrScannerOpen: () -> Unit,
    hasCameraFeature: Boolean,
    sendState: SendAddressBookState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(8.dp)

        BalanceWidget(
            state = balanceWidgetState
        )

        Spacer(24.dp)

        // TODO [#1256]: Consider Send.Form TextFields scrolling
        // TODO [#1256]: https://github.com/Electric-Coin-Company/zashi-android/issues/1256

        SendFormAddressTextField(
            hasCameraFeature = hasCameraFeature,
            onQrScannerOpen = onQrScannerOpen,
            recipientAddressState = recipientAddressState,
            setRecipientAddress = onRecipientAddressChange,
            sendAddressBookState = sendState
        )

        AnimatedVisibility(visible = sendState.isHintVisible) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                SendAddressBookHint(Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.size(ZcashTheme.dimens.spacingDefault))

        val isMemoFieldAvailable =
            recipientAddressState.address.isEmpty() ||
                recipientAddressState.type is AddressType.Invalid ||
                (
                    recipientAddressState.type is AddressType.Valid &&
                        recipientAddressState.type !is AddressType.Transparent &&
                        recipientAddressState.type !is AddressType.Tex
                )

        SendFormAmountTextField(
            amountState = amountState,
            imeAction =
                if (recipientAddressState.type == AddressType.Transparent || !isMemoFieldAvailable) {
                    ImeAction.Done
                } else {
                    ImeAction.Next
                },
            isTransparentOrTextRecipient =
                recipientAddressState.type?.let {
                    it == AddressType.Transparent || it == AddressType.Tex
                } ?: false,
            exchangeRateState = exchangeRateState,
            setAmountState = setAmountState,
            selectedAccount = selectedAccount
        )

        Spacer(Modifier.size(ZcashTheme.dimens.spacingDefault))

        SendFormMemoTextField(
            memoState = memoState,
            setMemoState = setMemoState,
            isMemoFieldAvailable = isMemoFieldAvailable,
        )

        Spacer(
            modifier =
                Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacing3xl))

        SendButton(
            amountState = amountState,
            memoState = memoState,
            onCreateZecSend = onCreateZecSend,
            recipientAddressState = recipientAddressState,
            selectedAccount = selectedAccount,
        )
    }
}

@Suppress("CyclomaticComplexMethod")
@Composable
fun SendButton(
    amountState: AmountState,
    memoState: MemoState,
    onCreateZecSend: (ZecSend) -> Unit,
    recipientAddressState: RecipientAddressState,
    selectedAccount: WalletAccount,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locale = rememberDesiredFormatLocale()

    // Common conditions continuously checked for validity
    val sendButtonEnabled =
        recipientAddressState.type !is AddressType.Invalid &&
            recipientAddressState.address.isNotEmpty() &&
            amountState is AmountState.Valid &&
            amountState.value.getValue().isNotBlank() &&
            selectedAccount.canSpend(amountState.zatoshi) &&
            // A valid memo is necessary only for non-transparent recipient
            (recipientAddressState.type == AddressType.Transparent || memoState is MemoState.Correct)

    ZashiButton(
        onClick = {
            scope.launch {
                // SDK side validations
                val zecSendValidation =
                    ZecSendExt.new(
                        locale = locale,
                        destinationString = recipientAddressState.address,
                        zecString = amountState.value.getString(context),
                        // Take memo for a valid non-transparent receiver only
                        memoString =
                            if (recipientAddressState.type == AddressType.Transparent) {
                                ""
                            } else {
                                memoState.text
                            },
                    )

                when (zecSendValidation) {
                    is ZecSendExt.ZecSendValidation.Valid -> {
                        onCreateZecSend(
                            zecSendValidation.zecSend.copy(
                                destination =
                                    when (recipientAddressState.type) {
                                        is AddressType.Invalid -> {
                                            WalletAddress.Unified.new(recipientAddressState.address)
                                        }

                                        AddressType.Shielded -> {
                                            WalletAddress.Unified.new(recipientAddressState.address)
                                        }

                                        AddressType.Tex -> {
                                            WalletAddress.Tex.new(recipientAddressState.address)
                                        }

                                        AddressType.Transparent -> {
                                            WalletAddress.Transparent.new(recipientAddressState.address)
                                        }

                                        AddressType.Unified -> {
                                            WalletAddress.Unified.new(recipientAddressState.address)
                                        }

                                        null -> {
                                            WalletAddress.Unified.new(recipientAddressState.address)
                                        }
                                    }
                            )
                        )
                    }

                    is ZecSendExt.ZecSendValidation.Invalid -> {
                        // We do not expect this validation to fail, so logging is enough here
                        // An error popup could be reasonable here as well
                        Twig.warn { "Send failed with: ${zecSendValidation.validationErrors}" }
                    }
                }
            }
        },
        text = stringResource(id = R.string.send_create),
        enabled = sendButtonEnabled,
        modifier =
            Modifier
                .testTag(SendTag.SEND_FORM_BUTTON)
                .fillMaxWidth()
    )
}

@Suppress("LongMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SendFormAddressTextField(
    sendAddressBookState: SendAddressBookState,
    hasCameraFeature: Boolean,
    onQrScannerOpen: () -> Unit,
    recipientAddressState: RecipientAddressState,
    setRecipientAddress: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier
                // Animate error show/hide
                .animateContentSize()
                // Scroll TextField above ime keyboard
                .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        Text(
            text = stringResource(id = R.string.send_address_label),
            color = ZashiColors.Inputs.Default.label,
            style = ZashiTypography.textMd
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingSmall))

        val recipientAddressValue = recipientAddressState.address
        val recipientAddressError =
            if (
                recipientAddressValue.isNotEmpty() &&
                recipientAddressState.type is AddressType.Invalid
            ) {
                stringResource(id = R.string.send_address_invalid)
            } else {
                null
            }

        ZashiTextField(
            singleLine = true,
            maxLines = 1,
            value = recipientAddressValue,
            onValueChange = {
                setRecipientAddress(it)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusEvent { focusState ->
                        if (focusState.isFocused) {
                            scope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
            error = recipientAddressError,
            textStyle = ZashiTypography.textMd.copy(fontFamily = RobotoMonoFontFamily),
            placeholder = {
                Text(
                    text = stringResource(id = R.string.send_address_hint),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Inputs.Default.text
                )
            },
            suffix =
                if (hasCameraFeature) {
                    {
                        Row(
                            verticalAlignment = Alignment.Top
                        ) {
                            Image(
                                modifier =
                                    Modifier
                                        .clickable(
                                            onClick = sendAddressBookState.onButtonClick,
                                            role = Role.Button,
                                            indication = ripple(radius = 4.dp),
                                            interactionSource = remember { MutableInteractionSource() }
                                        ).testTag(SendTag.SEND_ADDRESS_BOOK_BUTTON),
                                painter = painterResource(sendAddressBookState.mode.icon),
                                contentDescription = stringResource(R.string.send_address_book_content_description),
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Image(
                                modifier =
                                    Modifier
                                        .clickable(
                                            onClick = onQrScannerOpen,
                                            role = Role.Button,
                                            indication = ripple(radius = 4.dp),
                                            interactionSource = remember { MutableInteractionSource() }
                                        ).testTag(SendTag.SEND_SCAN_BUTTON),
                                painter = painterResource(R.drawable.qr_code_icon),
                                contentDescription = stringResource(R.string.send_scan_content_description),
                            )

                            // Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                } else {
                    null
                },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                ),
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SendFormAmountTextField(
    amountState: AmountState,
    imeAction: ImeAction,
    isTransparentOrTextRecipient: Boolean,
    exchangeRateState: ExchangeRateState,
    setAmountState: (AmountState) -> Unit,
    selectedAccount: WalletAccount,
) {
    val focusManager = LocalFocusManager.current

    val context = LocalContext.current

    val zcashCurrency = ZcashCurrency.getLocalizedName(context)

    val locale = rememberDesiredFormatLocale()

    val amountError =
        when (amountState) {
            is AmountState.Invalid -> {
                if (amountState.value.isEmpty()) {
                    null
                } else {
                    stringResource(id = R.string.send_amount_invalid)
                }
            }

            is AmountState.Valid -> {
                if (selectedAccount.spendableShieldedBalance < amountState.zatoshi) {
                    stringResource(id = R.string.send_amount_insufficient_balance)
                } else {
                    null
                }
            }
        }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    Column(
        modifier =
            Modifier
                // Animate error show/hide
                .animateContentSize()
                // Scroll TextField above ime keyboard
                .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        Text(
            text = stringResource(id = R.string.send_amount_label),
            color = ZashiColors.Inputs.Default.label,
            style = ZashiTypography.textMd
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingSmall))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            ZashiTextField(
                singleLine = true,
                maxLines = 1,
                value = amountState.value.getString(context),
                onValueChange = { newValue ->
                    setAmountState(
                        AmountState.newFromZec(
                            value = newValue,
                            fiatValue = amountState.fiatValue.getString(context),
                            isTransparentOrTextRecipient = isTransparentOrTextRecipient,
                            exchangeRateState = exchangeRateState,
                            locale = locale
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                innerModifier =
                    ZashiTextFieldDefaults
                        .innerModifier
                        .testTag(SendTag.SEND_AMOUNT_FIELD),
                error = amountError,
                placeholder = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.send_amount_hint,
                                zcashCurrency
                            ),
                        style = ZashiTypography.textMd,
                        color = ZashiColors.Inputs.Default.text
                    )
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = imeAction
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            focusManager.clearFocus(true)
                        },
                        onNext = {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    ),
                prefix = {
                    Image(
                        painter = painterResource(R.drawable.ic_send_zashi),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(color = ZashiColors.Inputs.Default.text),
                    )
                }
            )

            if (exchangeRateState is ExchangeRateState.Data) {
                Spacer(modifier = Modifier.width(12.dp))
                Image(
                    modifier = Modifier.padding(top = 12.dp),
                    painter = painterResource(id = R.drawable.ic_send_convert),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(color = ZcashTheme.colors.secondaryColor),
                )
                Spacer(modifier = Modifier.width(12.dp))
                ZashiTextField(
                    singleLine = true,
                    maxLines = 1,
                    isEnabled = !exchangeRateState.isStale && exchangeRateState.currencyConversion != null,
                    value = amountState.fiatValue.getString(context),
                    onValueChange = { newValue ->
                        setAmountState(
                            AmountState.newFromFiat(
                                value = amountState.value.getString(context),
                                fiatValue = newValue,
                                isTransparentOrTextRecipient = isTransparentOrTextRecipient,
                                exchangeRateState = exchangeRateState,
                                locale = locale
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        ZashiAutoSizeText(
                            text =
                                exchangeRateState.currencyConversion
                                    ?.fiatCurrency
                                    ?.let {
                                        stringResByFiatDisplayName(it).getValue()
                                    }.orEmpty(),
                            style = ZashiTypography.textMd,
                            color = LocalContentColor.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = imeAction
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                focusManager.clearFocus(true)
                            },
                            onNext = {
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                        ),
                    prefix = {
                        if (exchangeRateState.currencyConversion?.fiatCurrency != null) {
                            Text(
                                text = exchangeRateState.currencyConversion.fiatCurrency.symbol,
                                color =
                                    if (!exchangeRateState.isStale) {
                                        ZashiColors.Inputs.Default.text
                                    } else {
                                        ZashiColors.Inputs.Disabled.text
                                    },
                                style = ZashiTypography.textLg,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }
        }
    }
}

@Suppress("LongMethod")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SendFormMemoTextField(
    isMemoFieldAvailable: Boolean,
    memoState: MemoState,
    setMemoState: (MemoState) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    Column(
        modifier =
            Modifier
                // Animate error show/hide
                .animateContentSize()
                // Scroll TextField above ime keyboard
                .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        Text(
            text = stringResource(id = R.string.send_memo_label),
            color = ZashiColors.Inputs.Default.label,
            style = ZashiTypography.textMd
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingSmall))

        ZashiTextField(
            minLines = if (isMemoFieldAvailable) 4 else 1,
            isEnabled = isMemoFieldAvailable,
            value =
                if (isMemoFieldAvailable) {
                    memoState.text
                } else {
                    ""
                },
            error = if (memoState is MemoState.Correct) null else "",
            onValueChange = {
                setMemoState(MemoState.new(it))
            },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                    capitalization = KeyboardCapitalization.Sentences
                ),
            placeholder = {
                if (isMemoFieldAvailable) {
                    Text(
                        text = stringResource(id = R.string.send_memo_hint),
                        style = ZashiTypography.textMd,
                        color = ZashiColors.Inputs.Default.text
                    )
                } else {
                    Text(
                        text = stringResource(R.string.send_transparent_memo),
                        style = ZashiTypography.textSm,
                        color = ZashiColors.Utility.Gray.utilityGray700
                    )
                }
            },
            leadingIcon =
                if (isMemoFieldAvailable) {
                    null
                } else {
                    {
                        Image(
                            painter = painterResource(id = R.drawable.ic_confirmation_message_info),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(ZashiColors.Utility.Gray.utilityGray500)
                        )
                    }
                },
            colors =
                if (isMemoFieldAvailable) {
                    ZashiTextFieldDefaults.defaultColors()
                } else {
                    ZashiTextFieldDefaults.defaultColors(
                        disabledTextColor = ZashiColors.Inputs.Disabled.text,
                        disabledHintColor = ZashiColors.Inputs.Disabled.hint,
                        disabledBorderColor = Color.Unspecified,
                        disabledContainerColor = ZashiColors.Inputs.Disabled.bg,
                        disabledPlaceholderColor = ZashiColors.Inputs.Disabled.text,
                    )
                },
            modifier = Modifier.fillMaxWidth(),
        )

        if (isMemoFieldAvailable) {
            Text(
                text =
                    stringResource(
                        id = R.string.send_memo_bytes_counter,
                        Memo.MAX_MEMO_LENGTH_BYTES - memoState.byteSize,
                        Memo.MAX_MEMO_LENGTH_BYTES
                    ),
                color =
                    if (memoState is MemoState.Correct) {
                        ZashiColors.Inputs.Default.hint
                    } else {
                        ZashiColors.Inputs.Filled.required
                    },
                textAlign = TextAlign.End,
                style = ZashiTypography.textSm,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = ZcashTheme.dimens.spacingTiny)
            )
        }
    }
}

@Composable
@Preview("SendFailure")
private fun PreviewSendFailure() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            SendFailure(
                onConfirm = {},
                reason = "Insufficient balance"
            )
        }
    }
}

@Composable
private fun SendFailure(
    onConfirm: () -> Unit,
    reason: String,
    modifier: Modifier = Modifier
) {
    // TODO [#1276]: Once we ensure that the reason contains a localized message, we can leverage it for the UI prompt
    // TODO [#1276]: Consider adding support for a specific exception in AppAlertDialog
    // TODO [#1276]: https://github.com/Electric-Coin-Company/zashi-android/issues/1276

    AppAlertDialog(
        title = stringResource(id = R.string.send_dialog_error_title),
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(id = R.string.send_dialog_error_text),
                    color = ZcashTheme.colors.textPrimary,
                )

                Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingDefault))

                Text(
                    text = reason,
                    fontStyle = FontStyle.Italic,
                    color = ZcashTheme.colors.textPrimary,
                )
            }
        },
        confirmButtonText = stringResource(id = R.string.send_dialog_error_btn),
        onConfirmButtonClick = onConfirm,
        modifier = modifier
    )
}
