package co.electriccoin.zcash.ui.common.model.near

import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.extension.ZERO
import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_FEE_BPS
import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.ZecSwapAsset
import co.electriccoin.zcash.ui.common.model.isSame
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.time.Instant

/**
 * @param expectedSlippageToleranceBps The slippage tolerance (in basis points) the client actually requested,
 * snapshotted before the call. The fail-closed slippage check below MUST use this rather than the server-echoed
 * `response.quoteRequest.slippageTolerance`: a malicious server could echo a wider tolerance (e.g.
 * 10000 = 100%) so the slippage floor collapses to zero and the check always passes. When non-null
 * the echo is additionally required to match it exactly. Null only for the status-display path,
 * where the original client request is no longer available and there is nothing left to authorize.
 */
data class NearSwapQuote(
    val response: QuoteResponseDto,
    override val originAsset: SwapAsset,
    override val destinationAsset: SwapAsset,
    override val depositAddress: SwapAddress,
    override val destinationAddress: SwapAddress,
    override val refundAddress: SwapAddress,
    val expectedSlippageToleranceBps: Int? = null,
) : SwapQuote {
    init {
        require(response.quoteRequest.originAsset == originAsset.assetId) {
            "Swap quote asset mismatch: requested originAsset=${originAsset.assetId} " +
                "but server returned ${response.quoteRequest.originAsset}"
        }
        require(response.quoteRequest.destinationAsset == destinationAsset.assetId) {
            "Swap quote asset mismatch: requested destinationAsset=${destinationAsset.assetId} " +
                "but server returned ${response.quoteRequest.destinationAsset}"
        }
        // Guards zecExchangeRate (= amountInUsd / amountInFormatted) and the fee math below it against a
        // divide-by-zero. A quote with a non-positive input amount is invalid anyway; fail closed with a
        // clear message rather than letting an ArithmeticException surface from the property initializers.
        require(response.quote.amountInFormatted.signum() > 0) {
            "Swap quote has non-positive amountInFormatted=${response.quote.amountInFormatted}"
        }
        requireConsistent(
            name = "amountIn",
            raw = response.quote.amountIn,
            formatted = response.quote.amountInFormatted,
            decimals = originAsset.decimals
        )
        requireConsistent(
            name = "amountOut",
            raw = response.quote.amountOut,
            formatted = response.quote.amountOutFormatted,
            decimals = destinationAsset.decimals
        )
        // The slippage tolerance must come from the client, not from the server's echoed request. When we
        // know what we asked for, require the echo to match it exactly so the displayed `slippage` (derived
        // below from the echo) is also trustworthy; then feed the client value into the fail-closed check.
        if (expectedSlippageToleranceBps != null) {
            require(response.quoteRequest.slippageTolerance == expectedSlippageToleranceBps) {
                "Swap slippage tolerance mismatch: requested $expectedSlippageToleranceBps bps " +
                    "but server returned ${response.quoteRequest.slippageTolerance}"
            }
        }
        requireWithinSlippage(
            swapType = response.quoteRequest.swapType,
            amountIn = response.quote.amountIn,
            amountOut = response.quote.amountOut,
            minAmountIn = response.quote.minAmountIn,
            minAmountOut = response.quote.minAmountOut,
            slippageToleranceBps = expectedSlippageToleranceBps ?: response.quoteRequest.slippageTolerance
        )
    }

    override val slippage: BigDecimal =
        BigDecimal(response.quoteRequest.slippageTolerance)
            .divide(BigDecimal("100", MathContext.DECIMAL128))

    override val provider = "near"

    override val mode: SwapMode =
        when (response.quoteRequest.swapType) {
            SwapType.FLEX_INPUT -> SwapMode.FLEX_INPUT
            SwapType.EXACT_INPUT -> SwapMode.EXACT_INPUT
            SwapType.EXACT_OUTPUT -> SwapMode.EXACT_OUTPUT
            null -> SwapMode.EXACT_INPUT
        }

    override val zecExchangeRate: BigDecimal =
        response.quote.amountInUsd
            .divide(response.quote.amountInFormatted, MathContext.DECIMAL128)

    override val amountIn: BigDecimal = response.quote.amountIn
    override val amountInFormatted: BigDecimal = response.quote.amountInFormatted
    override val amountInUsd: BigDecimal = response.quote.amountInUsd

    override val amountOut: BigDecimal = response.quote.amountOut
    override val amountOutUsd: BigDecimal = response.quote.amountOutUsd
    override val amountOutFormatted: BigDecimal = response.quote.amountOutFormatted

    override val affiliateFee: BigDecimal =
        response.quote.amountInFormatted
            .multiply(
                BigDecimal(AFFILIATE_FEE_BPS).divide(BigDecimal("10000"), MathContext.DECIMAL128),
                MathContext.DECIMAL128
            )

    override val affiliateFeeZatoshi: Zatoshi =
        if (originAsset is ZecSwapAsset) {
            response.quote.amountInFormatted
                .coerceAtLeast(BigDecimal(0))
                .multiply(
                    BigDecimal(AFFILIATE_FEE_BPS).divide(BigDecimal("10000"), MathContext.DECIMAL128),
                    MathContext.DECIMAL128
                ).convertZecToZatoshi()
        } else {
            response.quote.amountOutUsd
                .coerceAtLeast(BigDecimal(0))
                .multiply(
                    BigDecimal(AFFILIATE_FEE_BPS).divide(BigDecimal("10000"), MathContext.DECIMAL128),
                    MathContext.DECIMAL128
                ).divide(zecExchangeRate, MathContext.DECIMAL128)
                .convertZecToZatoshi()
        }

    override val affiliateFeeUsd: BigDecimal =
        response.quote.amountInUsd
            .coerceAtLeast(BigDecimal(0))
            .multiply(
                BigDecimal(AFFILIATE_FEE_BPS).divide(BigDecimal("10000"), MathContext.DECIMAL128),
                MathContext.DECIMAL128
            )

    override val timestamp: Instant = response.timestamp

    override val deadline: Instant = response.quote.deadline

    override fun getTotal(proposal: Proposal?) = amountInFormatted + (getZecFee(proposal) ?: BigDecimal.ZERO)

    override fun getTotalUsd(proposal: Proposal?) = amountInUsd + getZecFeeUsd(proposal)

    override fun getTotalFeesUsd(proposal: Proposal?) = affiliateFeeUsd + getZecFeeUsd(proposal)

    override fun getTotalFeesZatoshi(proposal: Proposal?): Zatoshi =
        (proposal?.totalFeeRequired() ?: Zatoshi.ZERO) + affiliateFeeZatoshi

    private fun getZecFee(proposal: Proposal?): BigDecimal? = proposal?.totalFeeRequired()?.convertZatoshiToZec()

    private fun getZecFeeUsd(proposal: Proposal?): BigDecimal =
        zecExchangeRate.multiply(getZecFee(proposal) ?: BigDecimal.ZERO, MathContext.DECIMAL128)
}

internal fun requireConsistent(name: String, raw: BigDecimal?, formatted: BigDecimal?, decimals: Int) {
    if (raw == null || formatted == null) return
    if (raw.compareTo(formatted.movePointRight(decimals)) != 0) {
        throw SwapAmountInconsistencyException(
            field = name,
            decimals = decimals,
            message =
                "Swap amount inconsistency: $name ($raw) does not match " +
                    "${name}Formatted ($formatted) at $decimals decimals"
        )
    }
}

/**
 * Thrown by [requireConsistent] when the server's raw base-unit amount does not equal the exact decimal
 * expansion of its displayed `*Formatted` value.
 *
 * The exact-equality posture is intentional and must NOT be relaxed to a tolerance: it is the "trust the
 * quote 0% or 100%" stance (MOB-1371). It is kept as a distinct [IllegalArgumentException] subtype — so it
 * still flows through the generic quote-rejection handling unchanged — that carries only the non-sensitive
 * [field] / [decimals]. The data source uses those to emit a sanitized crash-monitoring signal (never the
 * amounts), so that if the 1Click API ever starts returning rounded display values, the resulting
 * rejections surface as an observable "quotes blocked" signal instead of silent breakage for users.
 */
class SwapAmountInconsistencyException(
    val field: String,
    val decimals: Int,
    message: String
) : IllegalArgumentException(message)

/**
 * Asserts the quote echoes back the amount the user actually requested for the user-fixed side of the
 * swap. The requested amount is truncated (RoundingMode.DOWN) to the asset's decimal precision before
 * comparing, because that is the precision the request is sent at — anything beyond `decimals` cannot be
 * represented on-chain and is intentionally dropped. So e.g. a user entry of 1.123456789 on an 8-decimal
 * asset is accepted against a quote of 1.12345678.
 */
internal fun requireQuoteMatchesUserAmount(quoted: BigDecimal, requested: BigDecimal, decimals: Int) {
    val truncated = requested.setScale(decimals, RoundingMode.DOWN)
    require(quoted.compareTo(truncated) == 0) {
        "Swap quote does not match user-requested amount: " +
            "quote=$quoted, requested=$truncated (at $decimals decimals)"
    }
}

internal fun requireMatchingAsset(
    name: String,
    expectedTokenTicker: String,
    expectedChainTicker: String,
    actual: SwapAsset
) {
    require(actual.isSame(expectedTokenTicker, expectedChainTicker)) {
        "Swap asset mismatch: expected $name=$expectedTokenTicker/$expectedChainTicker " +
            "but server returned ${actual.tokenTicker}/${actual.chainTicker}"
    }
}

/**
 * Fail-closed slippage check on the server-determined (floating) side of the quote. The user-fixed side
 * is already pinned by requireQuoteMatchesUserAmount in the use case; this asserts the server's own
 * worst-case guarantee (minAmountOut / minAmountIn) does not exceed the slippage the user requested.
 *
 * Both bounds are required fields of the 1Click quote response (see QuoteDetails), so the check is always
 * enforced: a server that omits them fails deserialization rather than reaching this point with a null
 * bound that would silently skip the assertion.
 */
internal fun requireWithinSlippage(
    swapType: SwapType?,
    amountIn: BigDecimal,
    amountOut: BigDecimal,
    minAmountIn: BigDecimal,
    minAmountOut: BigDecimal,
    slippageToleranceBps: Int
) {
    // Both bounds are computed in integer base units to match the server's integer arithmetic.
    // Using full-precision (DECIMAL128) arithmetic here would leave a fractional remainder that
    // gets compared against the server's already-truncated integer guarantee, rejecting valid
    // quotes by a single base unit (e.g. amountOut=9897372, bps=3000 → real floor=6928160.4,
    // server returns 6928160 — would throw if we didn't round).
    val bps = BigDecimal(slippageToleranceBps)
    val basisPointsDenominator = BigDecimal("10000")
    when (swapType) {
        // Output floats: the guaranteed minimum out must not be worse than amountOut * (1 - slippage).
        // Round DOWN — the server's worst-case minAmountOut is truncated DOWN for its own safety, so the
        // wallet's floor must do the same to accept the boundary case.
        SwapType.EXACT_INPUT, SwapType.FLEX_INPUT -> {
            val floor =
                amountOut
                    .multiply(basisPointsDenominator.subtract(bps))
                    .divide(basisPointsDenominator, 0, RoundingMode.DOWN)
            require(minAmountOut >= floor) {
                "Swap slippage exceeded: server-guaranteed minAmountOut=$minAmountOut is below the slippage " +
                    "floor=$floor (amountOut=$amountOut, slippageBps=$slippageToleranceBps)"
            }
        }

        // Input floats: the guaranteed input must not exceed amountIn * (1 + slippage).
        // Note: the server names this field `minAmountIn`, but for EXACT_OUTPUT it is the worst-case
        // *maximum* input you may pay, hence `maxInputGuarantee`. Round UP — the server's worst-case
        // maxInput is truncated UP for its own safety, so the wallet's ceiling must do the same.
        SwapType.EXACT_OUTPUT -> {
            val maxInputGuarantee = minAmountIn
            val ceiling =
                amountIn
                    .multiply(basisPointsDenominator.add(bps))
                    .divide(basisPointsDenominator, 0, RoundingMode.UP)
            require(maxInputGuarantee <= ceiling) {
                "Swap slippage exceeded: server-guaranteed minAmountIn=$maxInputGuarantee is above the " +
                    "slippage ceiling=$ceiling (amountIn=$amountIn, slippageBps=$slippageToleranceBps)"
            }
        }

        null -> {}
    }
}
