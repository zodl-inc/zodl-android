package co.electriccoin.zcash.ui.common.model.near

import co.electriccoin.zcash.ui.common.model.DynamicSwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import co.electriccoin.zcash.ui.common.model.SwapStatus as ModelSwapStatus

/**
 * Covers the client-side EXPIRED synthesis in [NearSwapQuoteStatus.status]. The condition is: if the
 * server still reports PENDING_DEPOSIT but we are within 5 minutes of the quote's deadline, the client
 * returns EXPIRED — terminating the polling loop even before the server itself has acknowledged the
 * expiry. Other server statuses pass through untouched regardless of the deadline.
 */
class NearSwapQuoteStatusTest {
    @Test
    fun statusIsExpiredWhenPendingDepositAndDeadlinePassed() {
        val swap =
            nearSwapQuoteStatus(
                serverStatus = SwapStatus.PENDING_DEPOSIT,
                deadline = Clock.System.now() - 1.hours
            )
        assertEquals(ModelSwapStatus.EXPIRED, swap.status)
    }

    @Test
    fun statusIsExpiredWhenPendingDepositAndWithinFiveMinuteGuard() {
        // Deadline is still in the future but within the 5-minute look-ahead window — the client
        // pre-emptively returns EXPIRED rather than wait for the server to catch up.
        val swap =
            nearSwapQuoteStatus(
                serverStatus = SwapStatus.PENDING_DEPOSIT,
                deadline = Clock.System.now() + 3.minutes
            )
        assertEquals(ModelSwapStatus.EXPIRED, swap.status)
    }

    @Test
    fun statusIsPendingWhenPendingDepositAndDeadlineFarInFuture() {
        val swap =
            nearSwapQuoteStatus(
                serverStatus = SwapStatus.PENDING_DEPOSIT,
                deadline = Clock.System.now() + 1.hours
            )
        assertEquals(ModelSwapStatus.PENDING, swap.status)
    }

    @Test
    fun statusPreservedWhenSuccessEvenIfDeadlinePassed() {
        // Synthesis only kicks in for PENDING_DEPOSIT — a server-reported SUCCESS passes through
        // unchanged regardless of the deadline.
        val swap =
            nearSwapQuoteStatus(
                serverStatus = SwapStatus.SUCCESS,
                deadline = Clock.System.now() - 1.hours
            )
        assertEquals(ModelSwapStatus.SUCCESS, swap.status)
    }

    @Test
    fun statusPreservedWhenFailedEvenIfDeadlinePassed() {
        val swap =
            nearSwapQuoteStatus(
                serverStatus = SwapStatus.FAILED,
                deadline = Clock.System.now() - 1.hours
            )
        assertEquals(ModelSwapStatus.FAILED, swap.status)
    }

    @Test
    fun statusPreservedWhenRefundedEvenIfDeadlinePassed() {
        val swap =
            nearSwapQuoteStatus(
                serverStatus = SwapStatus.REFUNDED,
                deadline = Clock.System.now() - 1.hours
            )
        assertEquals(ModelSwapStatus.REFUNDED, swap.status)
    }

    private fun nearSwapQuoteStatus(serverStatus: SwapStatus, deadline: Instant) =
        NearSwapQuoteStatus(
            response =
                SwapStatusResponseDto(
                    quoteResponse = quoteResponse(deadline = deadline),
                    status = serverStatus,
                    updatedAt = ""
                ),
            origin = asset(assetId = ORIGIN_ID, token = "TKA", chain = "chaina", decimals = 8),
            destination = asset(assetId = DEST_ID, token = "TKB", chain = "chainb", decimals = 6),
            depositAddress = DynamicSwapAddress(DEPOSIT_ADDRESS),
            destinationAddress = DynamicSwapAddress(RECIPIENT_ADDRESS),
            refundAddress = DynamicSwapAddress(REFUND_ADDRESS),
        )

    private fun quoteResponse(deadline: Instant): QuoteResponseDto =
        QuoteResponseDto(
            timestamp = EPOCH,
            quoteRequest =
                QuoteRequest(
                    dry = false,
                    swapType = SwapType.EXACT_INPUT,
                    slippageTolerance = 100,
                    originAsset = ORIGIN_ID,
                    destinationAsset = DEST_ID,
                    amount = BigDecimal("100000000"),
                    refundTo = REFUND_ADDRESS,
                    recipient = RECIPIENT_ADDRESS,
                    deadline = deadline,
                    appFees = emptyList()
                ),
            quote =
                QuoteDetails(
                    depositAddress = DEPOSIT_ADDRESS,
                    amountIn = BigDecimal("100000000"),
                    amountInFormatted = BigDecimal("1"),
                    amountInUsd = BigDecimal("10"),
                    minAmountIn = BigDecimal("100000000"),
                    amountOut = BigDecimal("2000000"),
                    amountOutFormatted = BigDecimal("2"),
                    amountOutUsd = BigDecimal("10"),
                    minAmountOut = BigDecimal("2000000"),
                    deadline = deadline
                )
        )

    private fun asset(assetId: String, token: String, chain: String, decimals: Int): SwapAsset =
        NearSwapAsset(
            tokenTicker = token,
            tokenName = StringResource.ByString(token),
            tokenIcon = imageRes(token),
            usdPrice = null,
            assetId = assetId,
            decimals = decimals,
            blockchain =
                SwapBlockchain(
                    chainTicker = chain,
                    chainName = StringResource.ByString(chain),
                    chainIcon = imageRes(chain)
                )
        )

    private companion object {
        const val ORIGIN_ID = "tka.chaina"
        const val DEST_ID = "tkb.chainb"
        const val DEPOSIT_ADDRESS = "deposit-address"
        const val RECIPIENT_ADDRESS = "recipient-address"
        const val REFUND_ADDRESS = "refund-address"
        val EPOCH: Instant = Instant.fromEpochSeconds(0)
    }
}
