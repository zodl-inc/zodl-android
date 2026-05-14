package co.electriccoin.zcash.ui.screen.request.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.FiatCurrencyConversion
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import cash.z.ecc.sdk.extension.ZcashDecimalFormatSymbols
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.GetZcashCurrencyProvider
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareQRUseCase
import co.electriccoin.zcash.ui.common.usecase.Zip321BuildUriUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.getPreferredLocale
import co.electriccoin.zcash.ui.screen.qrcode.ext.fromReceiveAddressType
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressType
import co.electriccoin.zcash.ui.screen.request.ext.toBigDecimalLocalized
import co.electriccoin.zcash.ui.screen.request.model.AmountState
import co.electriccoin.zcash.ui.screen.request.model.MemoState
import co.electriccoin.zcash.ui.screen.request.model.OnAmount
import co.electriccoin.zcash.ui.screen.request.model.QrCodeState
import co.electriccoin.zcash.ui.screen.request.model.Request
import co.electriccoin.zcash.ui.screen.request.model.RequestCurrency
import co.electriccoin.zcash.ui.screen.request.model.RequestStage
import co.electriccoin.zcash.ui.screen.request.model.RequestState
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.MathContext

@Suppress("TooManyFunctions")
class RequestVM(
    private val addressTypeOrdinal: Int,
    private val application: Application,
    exchangeRateRepository: ExchangeRateRepository,
    getZcashCurrency: GetZcashCurrencyProvider,
    zip321BuildUriUseCase: Zip321BuildUriUseCase,
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val shareQR: ShareQRUseCase,
) : ViewModel() {
    companion object {
        private const val MAX_ZCASH_SUPPLY = 21_000_000
        private const val DEFAULT_MEMO = ""
        private const val DEFAULT_URI = ""
    }

    private val decimalFormatSymbols: ZcashDecimalFormatSymbols
        get() = ZcashDecimalFormatSymbols(application.resources.configuration.getPreferredLocale())

    private val decimal: String
        get() = Regex.escape(decimalFormatSymbols.decimalSeparator.toString())

    private val grouping: String
        get() = Regex.escape(decimalFormatSymbols.groupingSeparator.toString())

    private val defaultAmount = application.getString(R.string.request_amount_empty)

    internal val request =
        MutableStateFlow(
            Request(
                amountState =
                    AmountState(
                        amount = defaultAmount,
                        currency = RequestCurrency.ZEC,
                        isValid = null
                    ),
                memoState = MemoState.Valid(DEFAULT_MEMO, 0, defaultAmount),
                qrCodeState = QrCodeState(DEFAULT_URI, defaultAmount, DEFAULT_MEMO),
            )
        )

    private val stage = MutableStateFlow(RequestStage.AMOUNT)

    internal val state =
        combine(
            request,
            stage,
            exchangeRateRepository.state,
            observeSelectedWalletAccount.require()
        ) { request, currentStage, exchangeRateUsd, account ->
            val walletAddress =
                account.fromReceiveAddressType(ReceiveAddressType.fromOrdinal(addressTypeOrdinal))
                    ?: return@combine RequestState.Loading

            when (currentStage) {
                RequestStage.AMOUNT -> {
                    RequestState.Amount(
                        request = request,
                        exchangeRateState = exchangeRateUsd,
                        zcashCurrency = getZcashCurrency(),
                        onAmount = { onAmount(resolveExchangeRateValue(exchangeRateUsd), it) },
                        onSwitch = { onSwitch(resolveExchangeRateValue(exchangeRateUsd), it) },
                        onBack = { onBack() },
                    ) { onNextClick(walletAddress, zip321BuildUriUseCase, exchangeRateUsd) }
                }

                RequestStage.MEMO -> {
                    RequestState.Memo(
                        icon =
                            when (account) {
                                is KeystoneAccount -> co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone
                                is ZashiAccount -> R.drawable.ic_zec_round_full
                            },
                        walletAddress = walletAddress,
                        request = request,
                        onMemo = { onMemo(it) },
                        onDone = { onMemoDone(walletAddress.address, zip321BuildUriUseCase) },
                        onBack = ::onBack,
                        zcashCurrency = getZcashCurrency(),
                    )
                }

                RequestStage.QR_CODE -> {
                    RequestState.QrCode(
                        icon =
                            if (walletAddress is WalletAddress.Transparent) {
                                R.drawable.ic_zec_qr_transparent
                            } else {
                                R.drawable.ic_zec_qr_shielded
                            },
                        walletAddress = walletAddress,
                        request = request,
                        onQrCodeShare = { _, _, uri ->
                            viewModelScope.launch {
                                shareQR(
                                    qrData = uri,
                                    shareText =
                                        application.getString(
                                            R.string.request_qr_code_share_chooser_text,
                                            CURRENCY_TICKER
                                        ),
                                    sharePickerText =
                                        application.getString(
                                            R.string.request_qr_code_share_chooser_title,
                                            CURRENCY_TICKER
                                        ),
                                    filenamePrefix = TEMP_FILE_NAME_PREFIX,
                                    centerIcon =
                                        if (walletAddress is WalletAddress.Transparent) {
                                            R.drawable.ic_zec_qr_transparent
                                        } else {
                                            R.drawable.ic_zec_qr_shielded
                                        }
                                )
                            }
                        },
                        onBack = ::onBack,
                        onClose = ::onClose,
                        zcashCurrency = getZcashCurrency(),
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = RequestState.Loading
        )

    private fun onNextClick(
        walletAddress: WalletAddress,
        zip321BuildUriUseCase: Zip321BuildUriUseCase,
        exchangeRateUsd: ExchangeRateState
    ) {
        when (walletAddress) {
            is WalletAddress.Transparent -> {
                onAmountAndMemoDone(
                    walletAddress.address,
                    zip321BuildUriUseCase,
                    resolveExchangeRateValue(exchangeRateUsd)
                )
            }

            is WalletAddress.Unified, is WalletAddress.Sapling -> {
                onAmountDone(resolveExchangeRateValue(exchangeRateUsd))
            }

            else -> {
                error("Unexpected address type")
            }
        }
    }

    private fun resolveExchangeRateValue(exchangeRateUsd: ExchangeRateState): FiatCurrencyConversion? =
        when (exchangeRateUsd) {
            is ExchangeRateState.Data -> {
                if (exchangeRateUsd.currencyConversion == null) {
                    Twig.warn { "Currency conversion is currently not available" }
                    null
                } else {
                    exchangeRateUsd.currencyConversion
                }
            }

            else -> {
                // Should not happen as the conversion rate related use cases should not be available
                Twig.error { "Unexpected screen state" }
                null
            }
        }

    private fun onAmount(conversion: FiatCurrencyConversion?, onAmount: OnAmount) {
        request.update {
            val newState =
                when (onAmount) {
                    is OnAmount.Number -> {
                        if (it.amountState.amount == defaultAmount) {
                            // Special case with current value only zero
                            validateAmountState(conversion, onAmount.number.toString())
                        } else {
                            // Adding new number to the result string
                            validateAmountState(
                                conversion,
                                it.amountState.amount + onAmount.number
                            )
                        }
                    }

                    is OnAmount.Delete -> {
                        if (it.amountState.amount.length == 1) {
                            // Deleting up to the last character
                            AmountState(
                                amount = defaultAmount,
                                currency = it.amountState.currency,
                                isValid = null
                            )
                        } else {
                            validateAmountState(
                                conversion,
                                it.amountState.amount.dropLast(1)
                            )
                        }
                    }

                    is OnAmount.Separator -> {
                        if (it.amountState.amount.contains(onAmount.separator)) {
                            // Separator already present
                            validateAmountState(conversion, it.amountState.amount)
                        } else {
                            validateAmountState(
                                conversion,
                                it.amountState.amount + onAmount.separator
                            )
                        }
                    }
                }

            it.copy(amountState = newState)
        }
    }

    // Validates only zeros and decimal separator
    private val defaultAmountValidationRegex = "^[${defaultAmount}$decimal]*$".toRegex()

    // Validates only numbers the properly use grouping and decimal separators
    // Note that this regex aligns with the one from ZcashSDK (sdk-incubator-lib/src/main/res/values/strings-regex.xml)
    // It only adds check for 0-8 digits after the decimal separator at maximum
    @Suppress("MaxLineLength", "ktlint:standard:max-line-length")
    private val allowedNumberFormatValidationRegex =
        "^([0-9]*([0-9]+([$grouping]\$|[$grouping][0-9]+))*([$decimal]\$|[$decimal][0-9]{0,8})?)?\$".toRegex()

    private fun validateAmountState(
        conversion: FiatCurrencyConversion?,
        resultAmount: String,
    ): AmountState {
        val newAmount =
            if (resultAmount.contains(defaultAmountValidationRegex)) {
                AmountState(
                    // Check for the max decimals in the default (i.e. 0.000) number, too
                    amount =
                        if (!resultAmount.contains(allowedNumberFormatValidationRegex)) {
                            request.value.amountState.amount
                        } else {
                            resultAmount
                        },
                    currency = request.value.amountState.currency,
                    isValid = null
                )
            } else if (!resultAmount.contains(allowedNumberFormatValidationRegex)) {
                AmountState(
                    amount = request.value.amountState.amount,
                    currency = request.value.amountState.currency,
                    isValid = true
                )
            } else {
                AmountState(
                    amount = resultAmount,
                    currency = request.value.amountState.currency,
                    isValid = true
                )
            }

        // Check for max Zcash supply
        return newAmount.amount
            .toBigDecimalLocalized(
                application.resources.configuration.getPreferredLocale()
            )?.let { currentValue ->
                val zecValue =
                    if (newAmount.currency == RequestCurrency.FIAT && conversion != null) {
                        currentValue.divide(conversion.priceOfZec.toBigDecimal(), MathContext.DECIMAL128)
                    } else {
                        currentValue
                    }
                if (zecValue > MAX_ZCASH_SUPPLY.toBigDecimal()) {
                    newAmount.copy(amount = request.value.amountState.amount)
                } else {
                    newAmount
                }
            } ?: newAmount
    }

    internal fun onBack() {
        when (stage.value) {
            RequestStage.AMOUNT -> {
                navigationRouter.back()
            }

            RequestStage.MEMO -> {
                stage.update { RequestStage.AMOUNT }
            }

            RequestStage.QR_CODE -> {
                when (ReceiveAddressType.fromOrdinal(addressTypeOrdinal)) {
                    ReceiveAddressType.Transparent -> stage.update { RequestStage.AMOUNT }
                    ReceiveAddressType.Unified, ReceiveAddressType.Sapling -> stage.update { RequestStage.MEMO }
                }
            }
        }
    }

    private fun onClose() = navigationRouter.back()

    private fun onAmountDone(conversion: FiatCurrencyConversion?) {
        request.update {
            val memoAmount =
                when (it.amountState.currency) {
                    RequestCurrency.FIAT -> {
                        if (conversion != null) {
                            it.amountState.toZecStringFloored(
                                conversion,
                                application
                            )
                        } else {
                            Twig.error { "Unexpected screen state" }
                            it.amountState.amount
                        }
                    }

                    RequestCurrency.ZEC -> {
                        it.amountState.amount
                    }
                }

            it.copy(memoState = MemoState.new(DEFAULT_MEMO, memoAmount))
        }
        stage.update { RequestStage.MEMO }
    }

    private fun onMemoDone(address: String, zip321BuildUriUseCase: Zip321BuildUriUseCase) {
        request.update {
            it.copy(
                qrCodeState =
                    QrCodeState(
                        requestUri =
                            createZip321Uri(
                                address = address,
                                amount = it.memoState.zecAmount,
                                memo = it.memoState.text,
                                zip321BuildUriUseCase = zip321BuildUriUseCase
                            ),
                        zecAmount = it.memoState.zecAmount,
                        memo = it.memoState.text,
                    )
            )
        }
        stage.update { RequestStage.QR_CODE }
    }

    private fun onAmountAndMemoDone(
        address: String,
        zip321BuildUriUseCase: Zip321BuildUriUseCase,
        conversion: FiatCurrencyConversion?
    ) {
        request.update {
            val qrCodeAmount =
                when (it.amountState.currency) {
                    RequestCurrency.FIAT -> {
                        if (conversion != null) {
                            it.amountState.toZecStringFloored(
                                conversion,
                                application
                            )
                        } else {
                            Twig.error { "Unexpected screen state" }
                            it.amountState.amount
                        }
                    }

                    RequestCurrency.ZEC -> {
                        it.amountState.amount
                    }
                }
            it.copy(
                qrCodeState =
                    QrCodeState(
                        requestUri =
                            createZip321Uri(
                                address = address,
                                amount = qrCodeAmount,
                                memo = DEFAULT_MEMO,
                                zip321BuildUriUseCase = zip321BuildUriUseCase
                            ),
                        zecAmount = qrCodeAmount,
                        memo = DEFAULT_MEMO,
                    )
            )
        }

        stage.update { RequestStage.QR_CODE }
    }

    private fun onSwitch(
        conversion: FiatCurrencyConversion?,
        onSwitchTo: RequestCurrency
    ) = viewModelScope.launch {
        if (conversion == null) return@launch

        request.update {
            val newAmount =
                when (onSwitchTo) {
                    RequestCurrency.FIAT -> {
                        it.amountState.toFiatString(
                            context = application.applicationContext,
                            conversion = conversion
                        )
                    }

                    RequestCurrency.ZEC -> {
                        it.amountState.toZecString(
                            conversion,
                            application
                        )
                    }
                }

            it.copy(
                amountState =
                    if (newAmount.contains(defaultAmountValidationRegex)) {
                        it.amountState.copy(amount = defaultAmount, currency = onSwitchTo)
                    } else {
                        it.amountState.copy(amount = newAmount, currency = onSwitchTo)
                    }
            )
        }
    }

    private fun onMemo(memoState: MemoState) = request.update { it.copy(memoState = memoState) }

    private fun createZip321Uri(
        address: String,
        amount: String,
        memo: String,
        zip321BuildUriUseCase: Zip321BuildUriUseCase,
    ): String {
        val locale = application.resources.configuration.getPreferredLocale()
        val amountNumber = amount.toBigDecimalLocalized(locale)
        return if (amountNumber == null) {
            Twig.error { "Unexpected amount state" }
            DEFAULT_URI
        } else {
            zip321BuildUriUseCase.invoke(
                address = address,
                amount = amountNumber,
                memo = memo
            )
        }
    }
}

private const val TEMP_FILE_NAME_PREFIX = "zip_321_request_qr_" // NON-NLS
