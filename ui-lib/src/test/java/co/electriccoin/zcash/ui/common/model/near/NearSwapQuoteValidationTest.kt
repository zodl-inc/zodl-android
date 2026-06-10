package co.electriccoin.zcash.ui.common.model.near

import co.electriccoin.zcash.ui.common.model.DynamicSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import java.math.BigDecimal
import kotlin.test.Test
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
        requireConsistent(name = "amountIn", raw = BigDecimal("100000000"), formatted = BigDecimal("1"), decimals = 8)
    }

    @Test
    fun requireConsistent_passesRegardlessOfFormattedScale() {
        requireConsistent(
            name = "amountIn",
            raw = BigDecimal("100000000"),
            formatted = BigDecimal("1.00"),
            decimals = 8
        )
    }

    @Test
    fun requireConsistent_throwsWhenRawDoesNotMatchFormatted() {
        assertFailsWith<IllegalArgumentException> {
            // formatted=2 -> expects 200_000_000 base units, raw says 100_000_000
            requireConsistent(
                name = "amountIn",
                raw = BigDecimal("100000000"),
                formatted = BigDecimal("2"),
                decimals = 8
            )
        }
    }

    @Test
    fun requireConsistent_noOpWhenEitherValueNull() {
        requireConsistent(name = "amountIn", raw = null, formatted = BigDecimal("1"), decimals = 8)
        requireConsistent(name = "amountIn", raw = BigDecimal("100000000"), formatted = null, decimals = 8)
    }

    // endregion

    // region requireWithinSlippage — Z2: server's worst-case guarantee must respect requested slippage

    @Test
    fun requireWithinSlippage_outputFloating_passesAtAndAboveFloor() {
        // 10% slippage, amountOut=100 -> floor=90
        requireWithinSlippage(SwapType.EXACT_INPUT, A_THOUSAND, BigDecimal("100"), null, BigDecimal("90"), BPS_10_PCT)
        requireWithinSlippage(SwapType.EXACT_INPUT, A_THOUSAND, BigDecimal("100"), null, BigDecimal("95"), BPS_10_PCT)
        requireWithinSlippage(SwapType.FLEX_INPUT, A_THOUSAND, BigDecimal("100"), null, BigDecimal("90"), BPS_10_PCT)
    }

    @Test
    fun requireWithinSlippage_outputFloating_throwsBelowFloor() {
        assertFailsWith<IllegalArgumentException> {
            // floor=90, server only guarantees 89
            requireWithinSlippage(
                SwapType.EXACT_INPUT,
                A_THOUSAND,
                BigDecimal("100"),
                null,
                BigDecimal("89"),
                BPS_10_PCT
            )
        }
    }

    @Test
    fun requireWithinSlippage_inputFloating_passesAtAndBelowCeiling() {
        // 10% slippage, amountIn=100 -> ceiling=110
        requireWithinSlippage(SwapType.EXACT_OUTPUT, BigDecimal("100"), A_THOUSAND, BigDecimal("110"), null, BPS_10_PCT)
        requireWithinSlippage(SwapType.EXACT_OUTPUT, BigDecimal("100"), A_THOUSAND, BigDecimal("105"), null, BPS_10_PCT)
    }

    @Test
    fun requireWithinSlippage_inputFloating_throwsAboveCeiling() {
        assertFailsWith<IllegalArgumentException> {
            // ceiling=110, server demands at least 111
            requireWithinSlippage(
                SwapType.EXACT_OUTPUT,
                BigDecimal("100"),
                A_THOUSAND,
                BigDecimal("111"),
                null,
                BPS_10_PCT
            )
        }
    }

    @Test
    fun requireWithinSlippage_noOpWhenBoundAbsent() {
        // Server omitted min* — defense-in-depth only, must not reject (see QuoteDetails nullability note).
        requireWithinSlippage(SwapType.EXACT_INPUT, A_THOUSAND, BigDecimal("100"), null, null, BPS_10_PCT)
        requireWithinSlippage(SwapType.EXACT_OUTPUT, BigDecimal("100"), A_THOUSAND, null, null, BPS_10_PCT)
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
            minAmountIn = null,
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
                minAmountIn = null,
                minAmountOut = BigDecimal("6928159"),
                slippageToleranceBps = 3000
            )
        }
    }

    @Test
    fun requireWithinSlippage_inputFloating_acceptsServerIntegerRoundedCeiling() {
        // Symmetric case for EXACT_OUTPUT: amountIn=9897372 × 1.30 = 12866583.6; the server rounds
        // UP to 12866584 to keep its guarantee conservative. The wallet's ceiling rounds UP too.
        requireWithinSlippage(
            swapType = SwapType.EXACT_OUTPUT,
            amountIn = BigDecimal("9897372"),
            amountOut = BigDecimal("2245828"),
            minAmountIn = BigDecimal("12866584"),
            minAmountOut = null,
            slippageToleranceBps = 3000
        )
    }

    @Test
    fun requireWithinSlippage_inputFloating_stillRejectsAboveIntegerCeiling() {
        // One base unit above the rounded-up integer ceiling is genuinely out of tolerance.
        assertFailsWith<IllegalArgumentException> {
            requireWithinSlippage(
                swapType = SwapType.EXACT_OUTPUT,
                amountIn = BigDecimal("9897372"),
                amountOut = BigDecimal("2245828"),
                minAmountIn = BigDecimal("12866585"),
                minAmountOut = null,
                slippageToleranceBps = 3000
            )
        }
    }

    // endregion

    // region requireMatchingAsset — Z3: returned asset must match the expected asset (ticker + chain)

    @Test
    fun requireMatchingAsset_passesOnSameTickerAndChainCaseInsensitive() {
        requireMatchingAsset(
            name = "originAsset",
            expectedTokenTicker = "btc",
            expectedChainTicker = "bitcoin",
            actual = asset(token = "BTC", chain = "BITCOIN")
        )
    }

    @Test
    fun requireMatchingAsset_throwsOnDifferentToken() {
        assertFailsWith<IllegalArgumentException> {
            requireMatchingAsset(
                name = "originAsset",
                expectedTokenTicker = "BTC",
                expectedChainTicker = "Bitcoin",
                actual = asset(token = "ETH", chain = "Bitcoin")
            )
        }
    }

    @Test
    fun requireMatchingAsset_throwsOnDifferentChain() {
        assertFailsWith<IllegalArgumentException> {
            requireMatchingAsset(
                name = "destinationAsset",
                expectedTokenTicker = "USDC",
                expectedChainTicker = "Ethereum",
                actual = asset(token = "USDC", chain = "Polygon")
            )
        }
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
        assertFailsWith<IllegalArgumentException> {
            requireQuoteMatchesUserAmount(quoted = BigDecimal("2"), requested = BigDecimal("1"), decimals = 8)
        }
    }

    // endregion

    private fun blockchain(chain: String) =
        SwapBlockchain(chainTicker = chain, chainName = StringResource.ByString(chain), chainIcon = imageRes(chain))

    private fun asset(token: String, chain: String) =
        DynamicSwapAsset(
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
        const val BPS_10_PCT = 1000
    }
}
