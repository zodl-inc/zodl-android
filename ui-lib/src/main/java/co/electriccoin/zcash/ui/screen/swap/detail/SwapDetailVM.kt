package co.electriccoin.zcash.ui.screen.swap.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapStatus.EXPIRED
import co.electriccoin.zcash.ui.common.model.SwapStatus.FAILED
import co.electriccoin.zcash.ui.common.model.SwapStatus.INCOMPLETE_DEPOSIT
import co.electriccoin.zcash.ui.common.model.SwapStatus.PENDING
import co.electriccoin.zcash.ui.common.model.SwapStatus.PROCESSING
import co.electriccoin.zcash.ui.common.model.SwapStatus.REFUNDED
import co.electriccoin.zcash.ui.common.model.SwapStatus.SUCCESS
import co.electriccoin.zcash.ui.common.model.ZecSwapAsset
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.GetORSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSwapMessageUseCase
import co.electriccoin.zcash.ui.common.usecase.SwapData
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.stringResByCurrencyNumber
import co.electriccoin.zcash.ui.design.util.stringResByDateTime
import co.electriccoin.zcash.ui.design.util.stringResByNumber
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
    getORSwapQuote: GetORSwapQuoteUseCase,
    private val args: SwapDetailArgs,
    private val navigationRouter: NavigationRouter,
    private val copyToClipboard: CopyToClipboardUseCase,
    private val mapper: CommonTransactionDetailMapper,
    private val getSwapMessage: GetSwapMessageUseCase,
) : ViewModel() {
    val state: StateFlow<SwapDetailState?> =
        getORSwapQuote
            .observe(args.depositAddress)
            .map { swapData ->
                SwapDetailState(
                    transactionHeader = createTransactionHeaderState(swapData),
                    quoteHeader =
                        mapper
                            .createTransactionDetailQuoteHeaderState(
                                swap = swapData.status,
                                originAsset = swapData.status?.quote?.originAsset,
                                destinationAsset = swapData.status?.quote?.destinationAsset
                            ),
                    status =
                        TransactionDetailSwapStatusRowState(
                            title = stringRes(R.string.transaction_detail_info_transaction_status),
                            status = swapData.status?.status,
                            mode = SWAP_INTO_ZEC
                        ),
                    depositTo =
                        TransactionDetailInfoRowState(
                            title = stringRes(R.string.swap_detail_row_deposit_to),
                            message = stringResByAddress(args.depositAddress),
                            trailingIcon = R.drawable.ic_transaction_detail_info_copy,
                            onClick = ::onCopyDepositAddressClick
                        ),
                    recipient = createRecipientState(swapData),
                    totalFees = createTotalFeesState(swapData),
                    maxSlippage = createSlippageState(swapData),
                    timestamp =
                        TransactionDetailInfoRowState(
                            title = stringRes(R.string.transaction_detail_info_timestamp),
                            message =
                                swapData.status
                                    ?.timestamp
                                    ?.atZone(ZoneId.systemDefault())
                                    ?.let {
                                        stringResByDateTime(
                                            zonedDateTime = it,
                                            useFullFormat = true
                                        )
                                    },
                        ),
                    message = getSwapMessage(swapData.data),
                    errorFooter = mapper.createTransactionDetailErrorFooter(swapData.error),
                    primaryButton = createPrimaryButtonState(swapData, swapData.error),
                    onBack = ::onBack
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private fun createTotalFeesState(swapData: SwapData): TransactionDetailInfoRowState =
        TransactionDetailInfoRowState(
            title = stringRes(R.string.transaction_detail_info_total_fees),
            message =
                if (swapData.status != null) {
                    val text =
                        stringResByCurrencyNumber(
                            amount = swapData.status.amountInFee,
                            ticker = swapData.status.quote.originAsset.tokenTicker
                        )
                    if (swapData.status.quote.destinationAsset is ZecSwapAsset) {
                        stringRes("~") + text
                    } else {
                        text
                    }
                } else {
                    null
                }
        )

    private fun createRecipientState(swapData: SwapData): TransactionDetailInfoRowState {
        val destinationAddress =
            swapData.status
                ?.quote
                ?.destinationAddress
                ?.address
        return TransactionDetailInfoRowState(
            title = stringRes(R.string.transaction_detail_info_recipient),
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
                    stringRes(R.string.transaction_detail_info_realized_slippage)
                } else {
                    stringRes(R.string.transaction_detail_info_max_slippage)
                },
            message =
                swapData.status?.maxSlippage?.let {
                    stringResByNumber(it, 0) + stringRes("%")
                },
        )

    private fun createPrimaryButtonState(
        swapData: SwapData,
        error: Exception?
    ): ButtonState? =
        if (swapData.error != null && swapData.status == null) {
            mapper.createTransactionDetailErrorButtonState(
                error = error,
                reloadHandle = swapData.handle
            )
        } else {
            null
        }

    private fun createTransactionHeaderState(swapData: SwapData): TransactionDetailHeaderState =
        TransactionDetailHeaderState(
            title =
                when (swapData.status?.status) {
                    EXPIRED -> stringRes(R.string.swap_detail_title_swap_expired)

                    INCOMPLETE_DEPOSIT,
                    PROCESSING,
                    PENDING -> stringRes(R.string.swap_detail_title_swap_pending)

                    SUCCESS -> stringRes(R.string.swap_detail_title_swap_completed)
                    REFUNDED -> stringRes(R.string.swap_detail_title_swap_refunded)
                    FAILED -> stringRes(R.string.swap_detail_title_swap_failed)
                    null -> null
                },
            amount =
                swapData.status
                    ?.amountOutFormatted
                    ?.let { stringResByNumber(it) },
            icons =
                listOf(
                    swapData.status
                        ?.quote
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
