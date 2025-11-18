@file:Suppress("ktlint:standard:filename")

package co.electriccoin.zcash.ui.screen.send

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.model.toZecString
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.di.koinActivityViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarVM
import co.electriccoin.zcash.ui.common.compose.LocalActivity
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveClearSendUseCase
import co.electriccoin.zcash.ui.common.usecase.PrefillSendData
import co.electriccoin.zcash.ui.common.usecase.PrefillSendUseCase
import co.electriccoin.zcash.ui.common.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.util.StringResource.Companion.NUMBER_FORMAT_LOCALE
import co.electriccoin.zcash.ui.design.util.TickerLocation
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.balances.BalanceWidgetArgs
import co.electriccoin.zcash.ui.screen.balances.BalanceWidgetState
import co.electriccoin.zcash.ui.screen.balances.BalanceWidgetVM
import co.electriccoin.zcash.ui.screen.scan.ScanArgs
import co.electriccoin.zcash.ui.screen.scan.ScanFlow
import co.electriccoin.zcash.ui.screen.send.ext.Saver
import co.electriccoin.zcash.ui.screen.send.model.AmountState
import co.electriccoin.zcash.ui.screen.send.model.MemoState
import co.electriccoin.zcash.ui.screen.send.model.RecipientAddressState
import co.electriccoin.zcash.ui.screen.send.model.SendStage
import co.electriccoin.zcash.ui.screen.send.view.Send
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
internal fun WrapSend(args: Send) {
    val activity = LocalActivity.current

    val navigationRouter = koinInject<NavigationRouter>()

    val walletViewModel = koinActivityViewModel<WalletViewModel>()

    val balanceWidgetVM =
        koinViewModel<BalanceWidgetVM> {
            parametersOf(
                BalanceWidgetArgs(
                    isBalanceButtonEnabled = true,
                    isExchangeRateButtonEnabled = false,
                    showDust = true
                )
            )
        }

    val accountDataSource = koinInject<AccountDataSource>()

    val exchangeRateRepository = koinInject<ExchangeRateRepository>()

    val hasCameraFeature = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    val synchronizer = walletViewModel.synchronizer.collectAsStateWithLifecycle().value

    val selectedAccount = accountDataSource.selectedAccount.collectAsStateWithLifecycle(null).value

    val balanceState = balanceWidgetVM.state.collectAsStateWithLifecycle().value

    val exchangeRateState = exchangeRateRepository.state.collectAsStateWithLifecycle().value

    BackHandler { navigationRouter.back() }

    WrapSend(
        balanceWidgetState = balanceState,
        exchangeRateState = exchangeRateState,
        goToQrScanner = {
            navigationRouter.forward(
                ScanArgs(
                    ScanFlow.SEND,
                    isScanZip321Enabled = args.isScanZip321Enabled
                )
            )
        },
        goBack = { navigationRouter.back() },
        hasCameraFeature = hasCameraFeature,
        sendArguments = args,
        synchronizer = synchronizer,
        selectedAccount = selectedAccount
    )
}

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@VisibleForTesting
@Composable
internal fun WrapSend(
    balanceWidgetState: BalanceWidgetState,
    exchangeRateState: ExchangeRateState,
    goToQrScanner: () -> Unit,
    goBack: () -> Unit,
    hasCameraFeature: Boolean,
    sendArguments: Send,
    synchronizer: Synchronizer?,
    selectedAccount: WalletAccount?,
) {
    val scope = rememberCoroutineScope()

    val viewModel = koinViewModel<SendViewModel>()

    val sendAddressBookState by viewModel.sendAddressBookState.collectAsStateWithLifecycle()

    val topAppBarViewModel = koinActivityViewModel<ZashiTopAppBarVM>()

    val zashiMainTopAppBarState by topAppBarViewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val (sendStage, setSendStage) =
        rememberSaveable(stateSaver = SendStage.Saver) { mutableStateOf(SendStage.Form) }

    val (zecSend, setZecSend) = rememberSaveable(stateSaver = ZecSend.Saver) { mutableStateOf(null) }

    val recipientAddressState by viewModel.recipientAddressState.collectAsStateWithLifecycle()

    val observeClearSend = koinInject<ObserveClearSendUseCase>()
    val prefillSend = koinInject<PrefillSendUseCase>()

    // Applied once rather than on every recomposition: this used to run in the composition body, so
    // editing a recipient that arrived on the route re-ran it and put the route's address straight
    // back, making the field silently reject every keystroke.
    var isRouteRecipientApplied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isRouteRecipientApplied &&
            sendArguments.recipientAddress != null &&
            sendArguments.recipientAddressType != null
        ) {
            viewModel.onRecipientAddressChanged(
                RecipientAddressState.new(
                    sendArguments.recipientAddress,
                    when (sendArguments.recipientAddressType) {
                        cash.z.ecc.sdk.model.AddressType.UNIFIED -> AddressType.Unified
                        cash.z.ecc.sdk.model.AddressType.TRANSPARENT -> AddressType.Transparent
                        cash.z.ecc.sdk.model.AddressType.SAPLING -> AddressType.Shielded
                        cash.z.ecc.sdk.model.AddressType.TEX -> AddressType.Tex
                    }
                )
            )
            isRouteRecipientApplied = true
        }
    }

    // Amount computation:
    val (amountState, setAmountState) =
        rememberSaveable(stateSaver = AmountState.getSaver(context)) {
            // Default amount state
            mutableStateOf(
                AmountState.newFromZec(
                    value = zecSend?.amount?.toZecString(NUMBER_FORMAT_LOCALE) ?: "",
                    fiatValue = "",
                    isTransparentOrTextRecipient =
                        recipientAddressState.type?.let { it == AddressType.Transparent }
                            ?: false,
                    exchangeRateState = exchangeRateState,
                )
            )
        }
    // New amount state based on the recipient address type (e.g. shielded supports zero funds sending and
    // transparent not)
    LaunchedEffect(recipientAddressState, exchangeRateState) {
        setAmountState(
            if (amountState.value.getString(context).isNotBlank() ||
                amountState.fiatValue.getString(context).isBlank()
            ) {
                AmountState.newFromZec(
                    value = amountState.value.getString(context),
                    fiatValue = amountState.fiatValue.getString(context),
                    isTransparentOrTextRecipient =
                        recipientAddressState.type
                            ?.let { it == AddressType.Transparent } ?: false,
                    exchangeRateState = exchangeRateState,
                    lastFieldChangedByUser = amountState.lastFieldChangedByUser,
                )
            } else {
                AmountState.newFromFiat(
                    value = amountState.value.getString(context),
                    fiatValue = amountState.fiatValue.getString(context),
                    isTransparentOrTextRecipient =
                        recipientAddressState.type
                            ?.let { it == AddressType.Transparent } ?: false,
                    exchangeRateState = exchangeRateState,
                )
            }
        )
    }

    // Memo computation:
    val (memoState, setMemoState) =
        rememberSaveable(stateSaver = MemoState.Saver) {
            mutableStateOf(MemoState.new(zecSend?.memo?.value ?: ""))
        }

    LaunchedEffect(Unit) {
        observeClearSend().collect {
            setSendStage(SendStage.Form)
            setZecSend(null)
            viewModel.onRecipientAddressChanged(RecipientAddressState.new("", null))
            setAmountState(
                AmountState.newFromZec(
                    value = "",
                    fiatValue = "",
                    isTransparentOrTextRecipient = false,
                    exchangeRateState = exchangeRateState,
                )
            )
            setMemoState(MemoState.new(""))
        }
    }

    suspend fun applyRecipient(address: String): AddressType? {
        val type = synchronizer?.validateAddress(address)
        setSendStage(SendStage.Form)
        setZecSend(null)
        viewModel.onRecipientAddressChanged(
            RecipientAddressState.new(
                address = address,
                type = type
            )
        )
        return type
    }

    suspend fun applyPayment(
        address: String,
        amount: Zatoshi,
        fee: Zatoshi?,
        memo: String?
    ) {
        val type = applyRecipient(address)

        val value =
            when {
                fee == null -> amount
                fee > amount -> amount
                else -> amount - fee
            }

        setAmountState(
            AmountState.newFromZec(
                value = stringRes(value, TickerLocation.HIDDEN).getString(context),
                fiatValue = amountState.fiatValue.getString(context),
                isTransparentOrTextRecipient = type == AddressType.Transparent,
                exchangeRateState = exchangeRateState,
            )
        )
        setMemoState(MemoState.new(memo.orEmpty()))
    }

    LaunchedEffect(Unit) {
        prefillSend().collect {
            when (it) {
                is PrefillSendData.All -> {
                    applyPayment(
                        address = it.address.orEmpty(),
                        amount = it.amount,
                        fee = it.fee,
                        memo = it.memos?.firstOrNull()
                    )
                }

                is PrefillSendData.FromAddressScan -> {
                    applyRecipient(it.address)
                }
            }
        }
    }

    // An entry point that knows the whole payment carries it on the route instead of publishing it
    // to prefillSend, so that it survives this screen being remounted. Applied once, so returning
    // from a configuration change does not overwrite what the user has typed since.
    var isRoutePaymentApplied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isRoutePaymentApplied && (sendArguments.amount != null || sendArguments.memo != null)) {
            applyPayment(
                address = sendArguments.recipientAddress.orEmpty(),
                amount = Zatoshi(sendArguments.amount ?: 0L),
                fee = null,
                memo = sendArguments.memo
            )
            isRoutePaymentApplied = true
        }
    }

    val onBackAction = {
        when (sendStage) {
            SendStage.Form -> {
                goBack()
            }

            SendStage.Proposing -> {
                // no action - wait until the sending is done
            }

            is SendStage.SendFailure -> {
                setSendStage(SendStage.Form)
            }
        }
    }

    if (null == synchronizer || null == selectedAccount) {
        // TODO [#1146]: Consider moving CircularScreenProgressIndicator from Android layer to View layer
        // TODO [#1146]: Improve this by allowing screen composition and updating it after the data is available
        // TODO [#1146]: https://github.com/Electric-Coin-Company/zashi-android/issues/1146
        CircularScreenProgressIndicator()
    } else {
        Send(
            balanceWidgetState = balanceWidgetState,
            sendStage = sendStage,
            onCreateZecSend = { newZecSend ->
                viewModel.onCreateZecSendClick(
                    newZecSend = newZecSend,
                    amountState = amountState,
                    setSendStage = setSendStage
                )
            },
            onBack = onBackAction,
            onQrScannerOpen = goToQrScanner,
            hasCameraFeature = hasCameraFeature,
            recipientAddressState = recipientAddressState,
            onRecipientAddressChange = {
                scope.launch {
                    viewModel.onRecipientAddressChanged(
                        RecipientAddressState.new(
                            address = it,
                            // TODO [#342]: Verify Addresses without Synchronizer
                            // TODO [#342]: https://github.com/zcash/zcash-android-wallet-sdk/issues/342
                            type = synchronizer.validateAddress(it)
                        )
                    )
                }
            },
            setAmountState = setAmountState,
            amountState = amountState,
            setMemoState = setMemoState,
            memoState = memoState,
            selectedAccount = selectedAccount,
            exchangeRateState = exchangeRateState,
            sendAddressBookState = sendAddressBookState,
            zashiMainTopAppBarState = zashiMainTopAppBarState
        )
    }
}
