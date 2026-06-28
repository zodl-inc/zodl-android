package co.electriccoin.zcash.ui.common.model.near

import co.electriccoin.zcash.ui.common.model.DynamicSwapAddress
import co.electriccoin.zcash.ui.common.model.GenericSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Integration-level tests over the full [NearSwapQuote] construction. The individual validators are unit
 * tested in [NearSwapQuoteValidationTest]; this locks how `init` *wires* them — in particular that amount
 * consistency uses the matching side's decimals (origin for amountIn, destination for amountOut), which
 * the deliberately-different decimals (8 vs 6) below would break if swapped.
 */
class NearSwapQuoteTest {
    @Test
    fun validQuoteIsAccepted() {
        val quote = nearSwapQuote(quoteResponse())

        assertEquals(SwapMode.EXACT_INPUT, quote.mode)
        assertEquals(BigDecimal("1"), quote.amountInFormatted)
        assertEquals(BigDecimal("2"), quote.amountOutFormatted)
    }

    @Test
    fun rejectsOriginAssetSubstitution() {
        assertFailsWith<IllegalArgumentException> {
            nearSwapQuote(quoteResponse(originAssetId = "$ORIGIN_ID.tampered"))
        }
    }

    @Test
    fun rejectsDestinationAssetSubstitution() {
        assertFailsWith<IllegalArgumentException> {
            nearSwapQuote(quoteResponse(destinationAssetId = "$DEST_ID.tampered"))
        }
    }

    @Test
    fun rejectsNonPositiveAmountInFormatted() {
        assertFailsWith<IllegalArgumentException> {
            nearSwapQuote(quoteResponse(amountIn = BigDecimal.ZERO, amountInFormatted = BigDecimal.ZERO))
        }
    }

    @Test
    fun rejectsInconsistentAmountInAtOriginDecimals() {
        assertFailsWith<IllegalArgumentException> {
            // amountInFormatted=1 at 8 decimals expects 100_000_000; server says 999
            nearSwapQuote(quoteResponse(amountIn = BigDecimal("999")))
        }
    }

    @Test
    fun rejectsSlippageBelowTolerance() {
        assertFailsWith<IllegalArgumentException> {
            // EXACT_INPUT -> output floats. 1% slippage on amountOut=2_000_000 -> floor 1_980_000.
            nearSwapQuote(quoteResponse(slippageBps = 100, minAmountOut = BigDecimal("1900000")))
        }
    }

    @Test
    fun acceptsSlippageWithinTolerance() {
        val quote = nearSwapQuote(quoteResponse(slippageBps = 100, minAmountOut = BigDecimal("1990000")))

        assertEquals(SwapMode.EXACT_INPUT, quote.mode)
    }

    @Test
    fun rejectsServerWidenedSlippageTolerance() {
        // Z7: client requested 100 bps (1%) but the server echoed a wider 10000 bps (100%) to collapse the
        // slippage floor to zero. The client-vs-echo equality check must fail closed before the floor math.
        assertFailsWith<IllegalArgumentException> {
            nearSwapQuote(
                quoteResponse(slippageBps = 10000, minAmountOut = BigDecimal("1990000")),
                expectedSlippageToleranceBps = 100
            )
        }
    }

    @Test
    fun acceptsMatchingClientAndServerSlippageTolerance() {
        val quote =
            nearSwapQuote(
                quoteResponse(slippageBps = 100, minAmountOut = BigDecimal("1990000")),
                expectedSlippageToleranceBps = 100
            )

        assertEquals(SwapMode.EXACT_INPUT, quote.mode)
    }

    @Suppress("LongParameterList")
    private fun quoteResponse(
        swapType: SwapType = SwapType.EXACT_INPUT,
        originAssetId: String = ORIGIN_ID,
        destinationAssetId: String = DEST_ID,
        slippageBps: Int = 100,
        amountIn: BigDecimal = BigDecimal("100000000"),
        amountInFormatted: BigDecimal = BigDecimal("1"),
        amountInUsd: BigDecimal = BigDecimal("10"),
        minAmountIn: BigDecimal = amountIn,
        amountOut: BigDecimal = BigDecimal("2000000"),
        amountOutFormatted: BigDecimal = BigDecimal("2"),
        amountOutUsd: BigDecimal = BigDecimal("10"),
        minAmountOut: BigDecimal = amountOut
    ): QuoteResponseDto =
        QuoteResponseDto(
            timestamp = EPOCH,
            quoteRequest =
                QuoteRequest(
                    dry = false,
                    swapType = swapType,
                    slippageTolerance = slippageBps,
                    originAsset = originAssetId,
                    destinationAsset = destinationAssetId,
                    amount = amountIn,
                    refundTo = REFUND_ADDRESS,
                    recipient = RECIPIENT_ADDRESS,
                    deadline = EPOCH,
                    appFees = emptyList()
                ),
            quote =
                QuoteDetails(
                    depositAddress = DEPOSIT_ADDRESS,
                    amountIn = amountIn,
                    amountInFormatted = amountInFormatted,
                    amountInUsd = amountInUsd,
                    minAmountIn = minAmountIn,
                    amountOut = amountOut,
                    amountOutFormatted = amountOutFormatted,
                    amountOutUsd = amountOutUsd,
                    minAmountOut = minAmountOut,
                    deadline = EPOCH
                )
        )

    private fun nearSwapQuote(response: QuoteResponseDto, expectedSlippageToleranceBps: Int? = null) =
        NearSwapQuote(
            response = response,
            originAsset = asset(assetId = ORIGIN_ID, token = "TKA", chain = "chaina", decimals = 8),
            destinationAsset = asset(assetId = DEST_ID, token = "TKB", chain = "chainb", decimals = 6),
            depositAddress = DynamicSwapAddress(DEPOSIT_ADDRESS),
            destinationAddress = DynamicSwapAddress(RECIPIENT_ADDRESS),
            refundAddress = DynamicSwapAddress(REFUND_ADDRESS),
            expectedSlippageToleranceBps = expectedSlippageToleranceBps
        )

    private fun asset(assetId: String, token: String, chain: String, decimals: Int): SwapAsset =
        GenericSwapAsset(
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
        val EPOCH = kotlin.time.Instant.fromEpochSeconds(0)
    }
}
