package co.electriccoin.zcash.ui.common.datasource

import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import io.ktor.client.plugins.ResponseException
import java.math.BigDecimal

interface SwapDataSource {
    @Throws(ResponseException::class)
    suspend fun getSupportedTokens(): List<SwapAsset>

    /**
     * Requests a quote from the swap provider.
     *
     * Throws [GiftCardAddressNotAllowedException], before anything is sent to the provider, when [refundAddress] or
     * [destinationAddress] is a gift card link.
     */
    @Throws(ResponseException::class, QuoteLowAmountException::class, GiftCardAddressNotAllowedException::class)
    suspend fun requestQuote(
        swapMode: SwapMode,
        amount: BigDecimal,
        refundAddress: String,
        originAsset: SwapAsset,
        destinationAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal,
        affiliateAddress: String
    ): SwapQuote

    @Throws(ResponseException::class)
    suspend fun submitDepositTransaction(txHash: String, depositAddress: String)

    @Throws(ResponseException::class, AssetNotFoundException::class)
    suspend fun checkSwapStatus(depositAddress: String, supportedTokens: List<SwapAsset>): SwapQuoteStatus
}

class QuoteLowAmountException(
    val asset: SwapAsset,
    val amount: BigDecimal?,
    val amountFormatted: BigDecimal?
) : Exception()

/**
 * A gift card link was given as a swap's recipient or refund address. The link carries the card's spending key in its
 * fragment, so it must never reach a swap provider. The message deliberately names neither the link nor any address.
 */
class GiftCardAddressNotAllowedException : Exception("A gift card link cannot be used as a swap address")

class AssetNotFoundException(
    tokenId: String
) : Exception("Token $tokenId not found")
