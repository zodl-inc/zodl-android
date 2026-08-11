package co.electriccoin.zcash.ui.screen.swap.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.mapper.SwapSupportMapper
import co.electriccoin.zcash.ui.common.model.SwapStatus.EXPIRED
import co.electriccoin.zcash.ui.common.model.SwapStatus.FAILED
import co.electriccoin.zcash.ui.common.model.SwapStatus.INCOMPLETE_DEPOSIT
import co.electriccoin.zcash.ui.common.model.SwapStatus.PENDING
import co.electriccoin.zcash.ui.common.model.SwapStatus.PROCESSING
import co.electriccoin.zcash.ui.common.model.SwapStatus.REFUNDED
import co.electriccoin.zcash.ui.common.model.SwapStatus.SUCCESS
import co.electriccoin.zcash.ui.common.model.isZCashAsset
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.GetReloadableSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.SwapData
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.stringResByCurrencyNumber
import co.electriccoin.zcash.ui.design.util.stringResByDateTime
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.swap.detail.support.SwapSupportArgs
import co.electriccoin.zcash.ui.screen.transactiondetail.CommonTransactionDetailMapper
import co.electriccoin.zcash.ui.screen.transactiondetail.TransactionDetailHeaderState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailInfoRowState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailSwapStatusRowState
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailSwapStatusRowState.Mode.SWAP_INTO_ZEC
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

class SwapDetailVM(
    getReloadableSwapQuote: GetReloadableSwapQuoteUseCase,
    private val args: SwapDetailArgs,
    private val navigationRouter: NavigationRouter,
    private val copyToClipboard: CopyToClipboardUseCase,
    private val mapper: CommonTransactionDetailMapper,
    private val getSwapMessage: SwapSupportMapper,
) : ViewModel() {
    val state: StateFlow<SwapDetailState?> =
        getReloadableSwapQuote
            .observe(args.depositAddress)
            .map { swapData ->
                SwapDetailState(
                    transactionHeader = createTransactionHeaderState(swapData),
                    quoteHeader =
                        mapper
                            .createTransactionDetailQuoteHeaderState(
                                swap = swapData.status,
                                originAsset = swapData.status?.originAsset,
                                destinationAsset = swapData.status?.destinationAsset
                            ),
                    status =
                        TransactionDetailSwapStatusRowState(
                            title = stringRes(R.string.swapAndPay_status),
                            status = swapData.status?.status,
                            mode = SWAP_INTO_ZEC
                        ),
                    depositTo =
                        TransactionDetailInfoRowState(
                            title = stringRes(R.string.swapToZec_depositTo),
                            message = stringResByAddress(args.depositAddress),
                            trailingIcon = R.drawable.ic_transaction_detail_info_copy,
                            onClick = ::onCopyDepositAddressClick
                        ),
                    recipient = createRecipientState(swapData),
                    totalFees = createTotalFeesState(swapData),
                    maxSlippage = createSlippageState(swapData),
                    timestamp =
                        TransactionDetailInfoRowState(
                            title = stringRes(R.string.transactionHistory_timestamp),
                            message =
                                swapData.status
                                    ?.timestamp
                                    ?.atZone(ZoneId.systemDefault())
                                    ?.let {
                                        stringResByDateTime(
                                            zonedDateTime = it,
                                            useFullFormat = true
                                        )
                                    }?.withStyle(),
                        ),
                    message = getSwapMessage.getMessage(swapData.status),
                    errorFooter = mapper.createTransactionDetailErrorFooter(swapData.error),
                    infoFooter =
                        stringRes(R.string.deposits_info)
                            .takeIf { swapData.status?.status == PENDING },
                    primaryButton = createPrimaryButtonState(swapData, swapData.error),
                    onBack = ::onBack,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private fun onContactSupport(depositAddress: String) {
        navigationRouter.forward(SwapSupportArgs(depositAddress))
    }

    private fun createTotalFeesState(swapData: SwapData): TransactionDetailInfoRowState =
        TransactionDetailInfoRowState(
            title = stringRes(R.string.swapAndPay_totalFees),
            message =
                if (swapData.status != null) {
                    val text =
                        stringResByCurrencyNumber(
                            amount = swapData.status.amountInFee,
                            ticker = swapData.status.originAsset.tokenTicker
                        )
                    if (swapData.status.destinationAsset.isZCashAsset) {
                        stringRes("~") + text
                    } else {
                        text
                    }
                } else {
                    null
                }?.withStyle()
        )

    private fun createRecipientState(swapData: SwapData): TransactionDetailInfoRowState {
        val destinationAddress =
            swapData.status
                ?.destinationAddress
                ?.address
        return TransactionDetailInfoRowState(
            title = stringRes(R.string.swapAndPay_recipient),
            message = destinationAddress?.let { stringResByAddress(it) },
            trailingIcon = R.drawable.ic_transaction_detail_info_copy,
            onClick =
                if (destinationAddress != null) {
                    { onCopyRecipientAddressClick(destinationAddress) }
                } else {
                    null
                }
        )
    }

    private fun createSlippageState(swapData: SwapData): TransactionDetailInfoRowState =
        TransactionDetailInfoRowState(
            title =
                if (swapData.status?.isSlippageRealized == true) {
                    stringRes(R.string.swapAndPay_executedSlippage)
                } else {
                    stringRes(R.string.swapAndPay_maxSlippageTitle)
                },
            message =
                swapData.status
                    ?.maxSlippage
                    ?.let {
                        stringResByNumber(it, 0) + stringRes("%")
                    }?.withStyle(),
        )

    private fun createPrimaryButtonState(
        swapData: SwapData,
        error: Exception?
    ): ButtonState? {
        val supportButton =
            getSwapMessage.getButton(swapData.status) {
                onContactSupport(it)
            }
        return when {
            supportButton != null -> {
                supportButton
            }

            swapData.error != null && swapData.status == null -> {
                return mapper.createTransactionDetailErrorButtonState(
                    error = error,
                    reloadHandle = swapData.handle
                )
            }

            else -> {
                null
            }
        }
    }

    private fun createTransactionHeaderState(swapData: SwapData): TransactionDetailHeaderState =
        TransactionDetailHeaderState(
            title =
                when (swapData.status?.status) {
                    EXPIRED -> stringRes(R.string.swapStatus_swapExpired)
                    PENDING -> stringRes(R.string.swapAndPay_status_pendingDeposit)
                    INCOMPLETE_DEPOSIT -> stringRes(R.string.swapStatus_swapIncomplete)
                    PROCESSING -> stringRes(R.string.swapToZec_swapProcessing)
                    SUCCESS -> stringRes(R.string.swapStatus_swapped)
                    REFUNDED -> stringRes(R.string.swapStatus_swapRefunded)
                    FAILED -> stringRes(R.string.swapStatus_swapFailed)
                    null -> null
                },
            amount =
                swapData.status
                    ?.amountOutFormatted
                    ?.let { stringResByNumber(it) },
            icons =
                listOf(
                    swapData.status
                        ?.originAsset
                        ?.tokenIcon ?: loadingImageRes(),
                    imageRes(R.drawable.ic_transaction_received),
                    imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_token_zec),
                ),
        )

    private fun onBack() = navigationRouter.back()

    private fun onCopyDepositAddressClick() = copyToClipboard(args.depositAddress)

    private fun onCopyRecipientAddressClick(recipient: String) = copyToClipboard(recipient)
}
