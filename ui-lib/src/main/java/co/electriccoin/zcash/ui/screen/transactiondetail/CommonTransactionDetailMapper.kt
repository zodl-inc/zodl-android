package co.electriccoin.zcash.ui.screen.transactiondetail

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.DynamicSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.ZcashShieldedSwapAddress
import co.electriccoin.zcash.ui.common.model.ZecSwapAsset
import co.electriccoin.zcash.ui.common.model.getQuoteTokenIcon
import co.electriccoin.zcash.ui.common.usecase.ReloadHandle
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.SwapQuoteHeaderState
import co.electriccoin.zcash.ui.design.component.SwapTokenAmountState
import co.electriccoin.zcash.ui.design.util.isServiceUnavailable
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDateTime
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import io.ktor.client.plugins.ResponseException
import java.time.Instant
import java.time.ZoneId

class CommonTransactionDetailMapper {
    fun createTransactionDetailTimestamp(timestamp: Instant?) =
        timestamp
            ?.atZone(ZoneId.systemDefault())
            ?.let {
                stringResByDateTime(
                    zonedDateTime = it,
                    useFullFormat = true
                )
            } ?: stringRes(R.string.transaction_detail_pending)

    fun createTransactionDetailErrorFooter(error: Exception?): ErrorFooter? {
        if (error == null) return null

        val isServiceUnavailableError = error is ResponseException && error.response.status.isServiceUnavailable()

        return ErrorFooter(
            title =
                if (isServiceUnavailableError) {
                    stringRes(co.electriccoin.zcash.ui.design.R.string.general_service_unavailable)
                } else {
                    stringRes(co.electriccoin.zcash.ui.design.R.string.general_unexpected_error)
                },
            subtitle =
                if (isServiceUnavailableError) {
                    stringRes(co.electriccoin.zcash.ui.design.R.string.general_please_try_again)
                } else {
                    stringRes(co.electriccoin.zcash.ui.design.R.string.general_check_connection)
                }
        )
    }

    fun createTransactionDetailErrorButtonState(error: Exception?, reloadHandle: ReloadHandle): ButtonState? {
        val isServiceUnavailableError = error is ResponseException && error.response.status.isServiceUnavailable()

        return if (isServiceUnavailableError) {
            null
        } else {
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_try_again),
                onClick = { reloadHandle.requestReload() },
                style = ButtonStyle.DESTRUCTIVE1
            )
        }
    }

    fun createTransactionDetailQuoteHeaderState(
        swap: SwapQuoteStatus?,
        originAsset: SwapAsset?,
        destinationAsset: SwapAsset?
    ): SwapQuoteHeaderState {
        if (swap == null || originAsset == null || destinationAsset == null) {
            return SwapQuoteHeaderState(null, null)
        }
        return SwapQuoteHeaderState(
            from =
                SwapTokenAmountState(
                    bigIcon = originAsset.getQuoteTokenIcon(),
                    smallIcon =
                        when (originAsset) {
                            is DynamicSwapAsset -> originAsset.chainIcon
                            is ZecSwapAsset -> originAsset.getQuoteChainIcon(isShielded = true)
                        },
                    amount = stringResByNumber(swap.amountInFormatted),
                    fiatAmount = stringResByDynamicCurrencyNumber(swap.amountInUsd, FiatCurrency.USD.symbol),
                    token = stringRes(originAsset.tokenTicker),
                    chain = originAsset.chainName
                ),
            to =
                SwapTokenAmountState(
                    bigIcon = destinationAsset.getQuoteTokenIcon(),
                    smallIcon =
                        when (destinationAsset) {
                            is DynamicSwapAsset -> destinationAsset.chainIcon
                            is ZecSwapAsset ->
                                destinationAsset.getQuoteChainIcon(
                                    isShielded = swap.quote.destinationAddress is ZcashShieldedSwapAddress
                                )
                        },
                    amount = stringResByNumber(swap.amountOutFormatted),
                    fiatAmount = stringResByDynamicCurrencyNumber(swap.amountOutUsd, FiatCurrency.USD.symbol),
                    token = stringRes(destinationAsset.tokenTicker),
                    chain = destinationAsset.chainName
                )
        )
    }
}
