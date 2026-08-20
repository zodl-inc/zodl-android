package co.electriccoin.zcash.ui.screen.transactiondetail

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.ZcashShieldedSwapAddress
import co.electriccoin.zcash.ui.common.model.isZCashAsset
import co.electriccoin.zcash.ui.common.usecase.ReloadHandle
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.SwapQuoteHeaderState
import co.electriccoin.zcash.ui.design.component.SwapTokenAmountState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDateTime
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.util.isServiceUnavailable
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
            } ?: stringRes(R.string.transactionHistory_pending)

    fun createTransactionDetailErrorFooter(error: Exception?): ErrorFooter? {
        if (error == null) return null

        val isServiceUnavailableError = error is ResponseException && error.response.status.isServiceUnavailable()

        return ErrorFooter(
            title =
                if (isServiceUnavailableError) {
                    stringRes(co.electriccoin.zcash.ui.design.R.string.swapAndPay_failure_laterTitle)
                } else {
                    stringRes(co.electriccoin.zcash.ui.design.R.string.swapAndPay_failure_retryTitle)
                },
            subtitle =
                if (isServiceUnavailableError) {
                    stringRes(co.electriccoin.zcash.ui.design.R.string.swapAndPay_failure_laterDesc)
                } else {
                    stringRes(co.electriccoin.zcash.ui.design.R.string.swapAndPay_failure_retryDesc)
                }
        )
    }

    fun createTransactionDetailErrorButtonState(error: Exception?, reloadHandle: ReloadHandle): ButtonState? {
        val isServiceUnavailableError = error is ResponseException && error.response.status.isServiceUnavailable()

        return if (isServiceUnavailableError) {
            null
        } else {
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.disconnectHWWallet_tryAgain),
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
                    bigIcon = originAsset.swapQuoteTokenIcon(),
                    smallIcon = originAsset.swapQuoteChainIcon(isShielded = true),
                    amount = stringResByNumber(swap.amountInFormatted),
                    fiatAmount = stringResByDynamicCurrencyNumber(swap.amountInUsd, FiatCurrency.USD.symbol),
                    token = stringRes(originAsset.tokenTicker),
                    chain = originAsset.chainName
                ),
            to =
                SwapTokenAmountState(
                    bigIcon = destinationAsset.swapQuoteTokenIcon(),
                    smallIcon =
                        destinationAsset.swapQuoteChainIcon(
                            isShielded = swap.destinationAddress is ZcashShieldedSwapAddress
                        ),
                    amount = stringResByNumber(swap.amountOutFormatted),
                    fiatAmount = stringResByDynamicCurrencyNumber(swap.amountOutUsd, FiatCurrency.USD.symbol),
                    token = stringRes(destinationAsset.tokenTicker),
                    chain = destinationAsset.chainName
                )
        )
    }
}

/**
 * The "big" token icon for a swap quote row: ZEC assets use the dedicated round ZEC artwork; every
 * other asset uses its own [SwapAsset.tokenIcon].
 */
internal fun SwapAsset.swapQuoteTokenIcon(): ImageResource =
    if (isZCashAsset) imageRes(R.drawable.ic_zec_round_full) else tokenIcon

/**
 * The "small" chain icon for a swap quote row: ZEC assets use the shielded vs transparent ZEC chain
 * artwork depending on [isShielded]; every other asset uses its own [SwapAsset.chainIcon].
 */
internal fun SwapAsset.swapQuoteChainIcon(isShielded: Boolean): ImageResource =
    if (isZCashAsset) {
        imageRes(
            if (isShielded) {
                co.electriccoin.zcash.ui.design.R.drawable.ic_zec_shielded
            } else {
                co.electriccoin.zcash.ui.design.R.drawable.ic_zec_unshielded
            }
        )
    } else {
        chainIcon
    }
