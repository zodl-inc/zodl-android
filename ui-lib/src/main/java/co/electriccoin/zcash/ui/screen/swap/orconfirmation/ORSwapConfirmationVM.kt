package co.electriccoin.zcash.ui.screen.swap.orconfirmation

import android.content.Context
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.SaveORSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareQRUseCase
import co.electriccoin.zcash.ui.design.component.BigIconButtonState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.design.util.styledStringResource
import co.electriccoin.zcash.ui.screen.swap.info.SwapInfoArgs
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

class ORSwapConfirmationVM(
    swapRepository: SwapRepository,
    private val cancelSwapQuote: CancelSwapQuoteUseCase,
    private val copyToClipboard: CopyToClipboardUseCase,
    private val navigationRouter: NavigationRouter,
    private val saveORSwap: SaveORSwapUseCase,
    private val shareQR: ShareQRUseCase,
    private val context: Context
) : ViewModel() {
    val state: StateFlow<ORSwapConfirmationState?> =
        swapRepository.quote
            .filterIsInstance<SwapQuoteData.Success>()
            .map { it.quote }
            .map { quote ->
                ORSwapConfirmationState(
                    onBack = ::onBack,
                    info =
                        IconButtonState(
                            icon = co.electriccoin.zcash.ui.design.R.drawable.ic_info,
                            onClick = ::onInfoClick,
                        ),
                    bigIcon = quote.originAsset.tokenIcon,
                    smallIcon = quote.originAsset.chainIcon,
                    amount = stringResByNumber(quote.amountInFormatted),
                    amountFiat = stringResByDynamicCurrencyNumber(quote.amountInUsd, FiatCurrency.USD.symbol),
                    onAmountClick = { onAmountClick(quote.amountInFormatted) },
                    qr = quote.depositAddress.address,
                    address = stringResByAddress(quote.depositAddress.address, true),
                    copyButton =
                        BigIconButtonState(
                            text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_copy),
                            icon = R.drawable.ic_copy,
                            onClick = { onCopyAddressClick(quote.depositAddress.address) }
                        ),
                    shareButton =
                        BigIconButtonState(
                            text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_share_qr),
                            icon = R.drawable.ic_qr_code_other,
                            onClick = {
                                onShareClick(
                                    qrData = quote.depositAddress.address,
                                    amount = quote.amountInFormatted,
                                    tokenTicker = quote.originAsset.tokenTicker,
                                    chainName = quote.originAsset.chainName
                                )
                            }
                        ),
                    footer =
                        styledStringResource(
                            R.string.swap_to_zec_footer,
                            styledStringResource(
                                resource = R.string.swap_to_zec_footer_bold,
                                fontWeight = FontWeight.Bold,
                                quote.originAsset.tokenTicker,
                                quote.originAsset.chainName
                            )
                        ),
                    primaryButton =
                        ButtonState(
                            text = stringRes(R.string.swap_confirmation_primary_button),
                            hapticFeedbackType = HapticFeedbackType.Confirm,
                            onClick = ::onSentFundsClick
                        ),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private var onSentFundsClickJob: Job? = null

    private fun onAmountClick(amountInFormatted: BigDecimal) = copyToClipboard(amountInFormatted.toPlainString())

    private fun onCopyAddressClick(depositAddress: String) = copyToClipboard(depositAddress)

    private fun onBack() {
        if (onSentFundsClickJob?.isActive == true) return
        cancelSwapQuote()
    }

    private fun onSentFundsClick() {
        if (onSentFundsClickJob?.isActive == true) return
        onSentFundsClickJob = viewModelScope.launch { saveORSwap() }
    }

    private fun onInfoClick() = navigationRouter.forward(SwapInfoArgs)

    private fun onShareClick(
        qrData: String,
        amount: BigDecimal,
        tokenTicker: String,
        chainName: StringResource
    ) = viewModelScope.launch {
        val shareText =
            stringRes(
                R.string.swap_to_zec_share_text,
                stringResByNumber(amount),
                tokenTicker.uppercase(),
                chainName,
                CURRENCY_TICKER
            ).getString(context)

        shareQR(
            qrData = qrData,
            shareText = shareText,
            sharePickerText = "Swap Deposit Address",
            filenamePrefix = "swap_deposit_address_"
        )
    }
}
