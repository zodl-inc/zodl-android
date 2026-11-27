package co.electriccoin.zcash.ui.common.model.near

import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchException
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchType
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for the pure NEAR swap quote validators introduced for the swap-security hardening
 * (MOB-1340: Z2 slippage fail-closed, Z3 asset substitution, Z4 amount consistency).
 */
class NearSwapQuoteValidationTest {
    // region requireConsistent — Z4: signed raw amount must equal the displayed formatted amount

    @Test
    fun requireConsistent_passesWhenRawMatchesFormattedShiftedByDecimals() {
        // 1.0 token at 8 decimals == 100_000_000 base units
        requireConsistent(
            name = "amountIn",
            type = SwapQuoteMismatchType.INPUT_AMOUNT,
            raw = BigDecimal("100000000"),
            formatted = BigDecimal("1"),
            decimals = 8
        )
    }

    @Test
    fun requireConsistent_passesRegardlessOfFormattedScale() {
        requireConsistent(
            name = "amountIn",
            type = SwapQuoteMismatchType.INPUT_AMOUNT,
            raw = BigDecimal("100000000"),
            formatted = BigDecimal("1.00"),
            decimals = 8
        )
    }

    @Test
    fun requireConsistent_throwsWhenRawDoesNotMatchFormatted() {
        // Distinct subtype (MOB-1371) so the data source can emit a sanitized monitoring signal; still an
        // IllegalArgumentException so the generic quote-rejection handling is unchanged.
        val e =
            assertFailsWith<SwapQuoteMismatchException.AmountInconsistency> {
                // formatted=2 -> expects 200_000_000 base units, raw says 100_000_000
                requireConsistent(
                    name = "amountIn",
                    type = SwapQuoteMismatchType.INPUT_AMOUNT,
                    raw = BigDecimal("100000000"),
                    formatted = BigDecimal("2"),
                    decimals = 8
                )
            }
        assertEquals("amountIn", e.field)
        assertEquals(8, e.decimals)
        assertEquals(SwapQuoteMismatchType.INPUT_AMOUNT, e.type)
    }

    @Test
    fun requireConsistent_reportsTheOutputAmountMismatchTypeForAmountOut() {
        val e =
            assertFailsWith<SwapQuoteMismatchException.AmountInconsistency> {
                requireConsistent(
                    name = "amountOut",
                    type = SwapQuoteMismatchType.OUTPUT_AMOUNT,
                    raw = BigDecimal("100000000"),
                    formatted = BigDecimal("2"),
                    decimals = 8
                )
            }
        assertEquals(SwapQuoteMismatchType.OUTPUT_AMOUNT, e.type)
    }

    @Test
    fun requireConsistent_noOpWhenEitherValueNull() {
        requireConsistent(
            name = "amountIn",
            type = SwapQuoteMismatchType.INPUT_AMOUNT,
            raw = null,
            formatted = BigDecimal("1"),
            decimals = 8
        )
        requireConsistent(
            name = "amountIn",
            type = SwapQuoteMismatchType.INPUT_AMOUNT,
            raw = BigDecimal("100000000"),
            formatted = null,
            decimals = 8
        )
    }

    // endregion

    // region requireWithinSlippage — Z2: server's worst-case guarantee must respect requested slippage

    @Test
    fun requireWithinSlippage_outputFloating_passesAtAndAboveFloor() {
        // 10% slippage, amountOut=100 -> floor=90. minAmountIn is the fixed side (== amountIn) and ignored here.
        requireWithinSlippage(SwapType.EXACT_INPUT, A_THOUSAND, HUNDRED, A_THOUSAND, BigDecimal("90"), BPS_10_PCT)
        requireWithinSlippage(SwapType.EXACT_INPUT, A_THOUSAND, HUNDRED, A_THOUSAND, BigDecimal("95"), BPS_10_PCT)
        requireWithinSlippage(SwapType.FLEX_INPUT, A_THOUSAND, HUNDRED, A_THOUSAND, BigDecimal("90"), BPS_10_PCT)
    }

    @Test
    fun requireWithinSlippage_outputFloating_throwsBelowFloor() {
        val e =
            assertFailsWith<SwapQuoteMismatchException> {
                // floor=90, server only guarantees 89
                requireWithinSlippage(
                    SwapType.EXACT_INPUT,
                    A_THOUSAND,
                    BigDecimal("100"),
                    A_THOUSAND,
                    BigDecimal("89"),
                    BPS_10_PCT
                )
            }
        assertEquals(SwapQuoteMismatchType.SLIPPAGE_EXCEEDED, e.type)
    }

    @Test
    fun requireWithinSlippage_inputFloating_passesAtAndAboveFloor() {
        // 10% slippage, amountIn=100 -> floor=90. minAmountIn is the least input that completes the swap and may
        // sit anywhere from the floor up to amountIn. minAmountOut is the fixed side (== amountOut) and ignored.
        requireWithinSlippage(SwapType.EXACT_OUTPUT, HUNDRED, A_THOUSAND, BigDecimal("90"), A_THOUSAND, BPS_10_PCT)
        requireWithinSlippage(SwapType.EXACT_OUTPUT, HUNDRED, A_THOUSAND, BigDecimal("95"), A_THOUSAND, BPS_10_PCT)
        requireWithinSlippage(SwapType.EXACT_OUTPUT, HUNDRED, A_THOUSAND, BigDecimal("100"), A_THOUSAND, BPS_10_PCT)
    }

    @Test
    fun requireWithinSlippage_inputFloating_throwsBelowFloor() {
        val e =
            assertFailsWith<SwapQuoteMismatchException> {
                // floor=90, the server's minimum is 89: amountIn carries a wider buffer than the slippage asked for
                requireWithinSlippage(
                    SwapType.EXACT_OUTPUT,
                    BigDecimal("100"),
                    A_THOUSAND,
                    BigDecimal("89"),
                    A_THOUSAND,
                    BPS_10_PCT
                )
            }
        assertEquals(SwapQuoteMismatchType.SLIPPAGE_EXCEEDED, e.type)
    }

    @Test
    fun requireWithinSlippage_inputFloating_throwsWhenMinAmountInIsAboveAmountIn() {
        // A deposit of amountIn=100 would be below the 101 the server needs and be refunded: an inconsistent quote.
        val e =
            assertFailsWith<SwapQuoteMismatchException> {
                requireWithinSlippage(
                    SwapType.EXACT_OUTPUT,
                    BigDecimal("100"),
                    A_THOUSAND,
                    BigDecimal("101"),
                    A_THOUSAND,
                    BPS_10_PCT
                )
            }
        assertEquals(SwapQuoteMismatchType.SLIPPAGE_EXCEEDED, e.type)
    }

    @Test
    fun requireWithinSlippage_noOpWhenSwapTypeNull() {
        requireWithinSlippage(null, BigDecimal("100"), BigDecimal("100"), BigDecimal("1"), BigDecimal("1"), BPS_10_PCT)
    }

    @Test
    fun requireWithinSlippage_outputFloating_acceptsServerIntegerTruncatedFloor() {
        // Real NEAR 1Click response from a $10 ZEC → USDC quote at 30% slippage. amountOut=9897372
        // × 0.70 = 6928160.4; the server truncates DOWN to 6928160. The check passes because the
        // wallet's floor is computed in integer base units (rounded DOWN) the same way.
        requireWithinSlippage(
            swapType = SwapType.EXACT_INPUT,
            amountIn = BigDecimal("2245828"),
            amountOut = BigDecimal("9897372"),
            minAmountIn = BigDecimal("2245828"),
            minAmountOut = BigDecimal("6928160"),
            slippageToleranceBps = 3000
        )
    }

    @Test
    fun requireWithinSlippage_outputFloating_stillRejectsBelowIntegerFloor() {
        // One base unit below the rounded-down integer floor is genuinely out of tolerance.
        assertFailsWith<IllegalArgumentException> {
            requireWithinSlippage(
                swapType = SwapType.EXACT_INPUT,
                amountIn = BigDecimal("2245828"),
                amountOut = BigDecimal("9897372"),
                minAmountIn = BigDecimal("2245828"),
                minAmountOut = BigDecimal("6928159"),
                slippageToleranceBps = 3000
            )
        }
    }

    @Test
    fun requireWithinSlippage_inputFloating_acceptsServerIntegerTruncatedFloor() {
        // EXACT_OUTPUT: amountIn=9897372 × 0.70 = 6928160.4; the floor is rounded DOWN to 6928160, so a server
        // minimum of 6928160 passes.
        requireWithinSlippage(
            swapType = SwapType.EXACT_OUTPUT,
            amountIn = BigDecimal("9897372"),
            amountOut = BigDecimal("2245828"),
            minAmountIn = BigDecimal("6928160"),
            minAmountOut = BigDecimal("2245828"),
            slippageToleranceBps = 3000
        )
    }

    @Test
    fun requireWithinSlippage_inputFloating_stillRejectsBelowIntegerFloor() {
        // One base unit below the rounded-down integer floor is genuinely out of tolerance.
        assertFailsWith<IllegalArgumentException> {
            requireWithinSlippage(
                swapType = SwapType.EXACT_OUTPUT,
                amountIn = BigDecimal("9897372"),
                amountOut = BigDecimal("2245828"),
                minAmountIn = BigDecimal("6928159"),
                minAmountOut = BigDecimal("2245828"),
                slippageToleranceBps = 3000
            )
        }
    }

    @Test
    fun requireWithinSlippage_inputFloating_acceptsAPayQuoteSeenOnIos() {
        // A Pay (EXACT_OUTPUT) quote seen on iOS at 2% slippage: amountIn=57557 zat, minAmountIn=56405.
        // 57557 × 0.98 = 56405.86, rounded DOWN to 56405, so the server's minimum is exactly on the floor.
        requireWithinSlippage(
            swapType = SwapType.EXACT_OUTPUT,
            amountIn = BigDecimal("57557"),
            amountOut = BigDecimal("2245828"),
            minAmountIn = BigDecimal("56405"),
            minAmountOut = BigDecimal("2245828"),
            slippageToleranceBps = 200
        )
    }

    // endregion

    // region requireMatchingAsset — Z3: returned asset must match the expected asset (ticker + chain)

    @Test
    fun requireMatchingAsset_passesOnSameTickerAndChainCaseInsensitive() {
        requireMatchingAsset(
            name = "originAsset",
            type = SwapQuoteMismatchType.ORIGIN_ASSET,
            expectedTokenTicker = "btc",
            expectedChainTicker = "bitcoin",
            actual = asset(token = "BTC", chain = "BITCOIN")
        )
    }

    @Test
    fun requireMatchingAsset_throwsOnDifferentToken() {
        val e =
            assertFailsWith<SwapQuoteMismatchException> {
                requireMatchingAsset(
                    name = "originAsset",
                    type = SwapQuoteMismatchType.ORIGIN_ASSET,
                    expectedTokenTicker = "BTC",
                    expectedChainTicker = "Bitcoin",
                    actual = asset(token = "ETH", chain = "Bitcoin")
                )
            }
        assertEquals(SwapQuoteMismatchType.ORIGIN_ASSET, e.type)
    }

    @Test
    fun requireMatchingAsset_throwsOnDifferentChain() {
        val e =
            assertFailsWith<SwapQuoteMismatchException> {
                requireMatchingAsset(
                    name = "destinationAsset",
                    type = SwapQuoteMismatchType.DESTINATION_ASSET,
                    expectedTokenTicker = "USDC",
                    expectedChainTicker = "Ethereum",
                    actual = asset(token = "USDC", chain = "Polygon")
                )
            }
        assertEquals(SwapQuoteMismatchType.DESTINATION_ASSET, e.type)
    }

    // endregion

    // region requireQuoteMatchesUserAmount — quote must echo the user-requested amount at asset precision

    @Test
    fun requireQuoteMatchesUserAmount_passesOnExactMatchRegardlessOfScale() {
        requireQuoteMatchesUserAmount(quoted = BigDecimal("1.00000000"), requested = BigDecimal("1"), decimals = 8)
        requireQuoteMatchesUserAmount(quoted = BigDecimal("1"), requested = BigDecimal("1.00"), decimals = 8)
    }

    @Test
    fun requireQuoteMatchesUserAmount_truncatesRequestedBeyondDecimalsBeforeComparing() {
        // User enters more precision than the 8-decimal asset can represent; the excess is dropped (DOWN),
        // so a quote of 1.12345678 matches a request of 1.123456789.
        requireQuoteMatchesUserAmount(
            quoted = BigDecimal("1.12345678"),
            requested = BigDecimal("1.123456789"),
            decimals = 8
        )
    }

    @Test
    fun requireQuoteMatchesUserAmount_throwsWhenQuoteUsesUntruncatedPrecision() {
        // The quote must match the truncated request (1.12345678), not the raw entry (1.123456789).
        assertFailsWith<IllegalArgumentException> {
            requireQuoteMatchesUserAmount(
                quoted = BigDecimal("1.123456789"),
                requested = BigDecimal("1.123456789"),
                decimals = 8
            )
        }
    }

    @Test
    fun requireQuoteMatchesUserAmount_throwsWhenAmountsDiffer() {
        val e =
            assertFailsWith<SwapQuoteMismatchException> {
                requireQuoteMatchesUserAmount(quoted = BigDecimal("2"), requested = BigDecimal("1"), decimals = 8)
            }
        assertEquals(SwapQuoteMismatchType.REQUESTED_AMOUNT, e.type)
    }

    // endregion

    private fun blockchain(chain: String) =
        SwapBlockchain(chainTicker = chain, chainName = StringResource.ByString(chain), chainIcon = imageRes(chain))

    private fun asset(token: String, chain: String) =
        NearSwapAsset(
            tokenTicker = token,
            tokenName = StringResource.ByString(token),
            tokenIcon = imageRes(token),
            usdPrice = null,
            assetId = "$token.$chain",
            decimals = 8,
            blockchain = blockchain(chain)
        )

    private companion object {
        val A_THOUSAND: BigDecimal = BigDecimal("1000")
        val HUNDRED: BigDecimal = BigDecimal("100")
        const val BPS_10_PCT = 1000
    }
}
