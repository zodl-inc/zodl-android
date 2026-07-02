package co.electriccoin.zcash.ui.common.datasource

import co.electriccoin.zcash.ui.common.model.DynamicSwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.common.model.swapkit.MayaSwapAsset
import co.electriccoin.zcash.ui.common.model.swapkit.MayaSwapQuote
import co.electriccoin.zcash.ui.common.model.swapkit.MayaSwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitQuoteRequestDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitSwapRequestDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitTokenDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitTrackRequestDto
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.provider.SwapkitApiProvider
import co.electriccoin.zcash.ui.common.provider.TokenIconProvider
import co.electriccoin.zcash.ui.common.provider.TokenNameProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.MathContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * SwapKit / Maya Protocol implementation of [SwapDataSource]. Bound under the `SWAP_PROVIDER_MAYA` qualifier.
 *
 * Phase-1 (MOB-1396 spike) scope — see `docs/SwapKit Spec (Maya DEX).md`:
 * - [getSupportedTokens] = `/tokens?provider=MAYACHAIN` + `POST /price`, fetched fresh on every call
 *   (no local caching).
 * - [requestQuote] = `/v3/quote` then `/v3/swap` (no PSBT/balance work) to obtain `targetAddress` + `memo`.
 * - [submitDepositTransaction] = no-op (Maya is vault-watching; there is no submit endpoint).
 * - [checkSwapStatus] = `POST /track` keyed by `depositAddress` (NEAR-shaped key; `hash`+`chainId` is Phase 2).
 */
@Suppress("TooManyFunctions")
class MayaSwapDataSource(
    private val swapkitApiProvider: SwapkitApiProvider,
    private val tokenIconProvider: TokenIconProvider,
    private val tokenNameProvider: TokenNameProvider,
    private val blockchainProvider: BlockchainProvider,
    private val addressResolver: SwapAddressResolver,
) : SwapDataSource {
    override suspend fun getSupportedTokens(): List<SwapAsset> =
        withContext(Dispatchers.Default) {
            // No caching: fetch the token universe and prices fresh on every call.
            val tokens =
                swapkitApiProvider
                    .getSupportedTokens(MAYACHAIN_NAMESPACE)
                    .tokens
                    .filterNot { it.identifier.startsWith("MAYA.") }
                    .distinctBy { it.identifier }
            val prices = loadPrices(tokens.map { it.identifier })
            tokens.map { buildSwapAsset(it, prices[it.identifier]) }
        }

    private suspend fun loadPrices(identifiers: List<String>): Map<String, BigDecimal> =
        if (identifiers.isEmpty()) {
            emptyMap()
        } else {
            swapkitApiProvider
                .getPrices(identifiers)
                .mapNotNull { dto -> dto.priceUsd?.let { dto.identifier to it } }
                .toMap()
        }

    private fun buildSwapAsset(dto: SwapkitTokenDto, price: BigDecimal?): SwapAsset {
        val chainTicker = dto.identifier.substringBefore('.')
        val tokenTicker = dto.ticker ?: dto.symbol ?: dto.identifier.substringAfter('.').substringBefore('-')
        return MayaSwapAsset(
            tokenTicker = tokenTicker,
            tokenName = tokenNameProvider.getName(tokenTicker),
            tokenIcon = tokenIconProvider.getIcon(tokenTicker),
            blockchain = blockchainProvider.getBlockchain(chainTicker),
            usdPrice = price,
            assetId = dto.identifier,
            decimals = dto.decimals,
        )
    }

    override suspend fun requestQuote(
        swapMode: SwapMode,
        amount: BigDecimal,
        refundAddress: String,
        originAsset: SwapAsset,
        destinationAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal,
        affiliateAddress: String
    ): SwapQuote {
        require(swapMode != SwapMode.EXACT_OUTPUT) { "Maya does not support EXACT_OUTPUT swaps" }

        val quoteResponse =
            swapkitApiProvider.requestQuote(
                SwapkitQuoteRequestDto(
                    sellAsset = originAsset.assetId,
                    buyAsset = destinationAsset.assetId,
                    sellAmount = amount.toPlainString(),
                    slippage = slippage.toDouble(),
                    providers = listOf(MAYACHAIN_PROVIDER),
                    // The route binds these at quote time; without them /v3/swap fails with invalidRoute.
                    sourceAddress = refundAddress,
                    destinationAddress = destinationAddress,
                )
            )

        val route =
            quoteResponse.routes.firstOrNull { MAYACHAIN_PROVIDER in it.providers }
                ?: quoteResponse.routes.firstOrNull()
                ?: throw QuoteLowAmountException(asset = originAsset, amount = null, amountFormatted = null)

        val swapResponse =
            swapkitApiProvider.buildSwap(
                SwapkitSwapRequestDto(
                    routeId = route.routeId,
                    sourceAddress = refundAddress,
                    destinationAddress = destinationAddress,
                )
            )

        val metaPrices =
            route.meta
                ?.assets
                ?.mapNotNull { asset -> asset.price?.let { asset.asset to it } }
                ?.toMap()
                .orEmpty()

        val now = Clock.System.now()
        return MayaSwapQuote(
            originAsset = originAsset,
            destinationAsset = destinationAsset,
            depositAddress = getDepositAddress(swapResponse.targetAddress, originAsset),
            destinationAddress = getDestinationAddress(destinationAddress, originAsset),
            refundAddress = getRefundAddress(refundAddress, originAsset),
            amountInFormatted = route.sellAmount,
            amountOutFormatted = route.expectedBuyAmount,
            slippage = slippage,
            timestamp = now,
            // Maya's real "do not broadcast after" deadline from /v3/swap; fall back to the
            // live-measured route TTL if absent.
            deadline = deadlineFrom(swapResponse.expiration) ?: (now + DEFAULT_QUOTE_TTL),
            originUsdPrice = metaPrices[originAsset.assetId] ?: originAsset.usdPrice ?: BigDecimal.ZERO,
            destinationUsdPrice = metaPrices[destinationAsset.assetId] ?: destinationAsset.usdPrice ?: BigDecimal.ZERO,
            affiliateFeeBps = route.meta?.affiliateFee?.toInt() ?: 0,
            memo = swapResponse.memo,
        )
    }

    /** No-op: Maya detects the vault deposit on-chain automatically; there is no submit endpoint. */
    override suspend fun submitDepositTransaction(txHash: String, depositAddress: String) {
        // Intentionally empty — see KDoc.
    }

    override suspend fun checkSwapStatus(depositAddress: String, supportedTokens: List<SwapAsset>): SwapQuoteStatus {
        val response = swapkitApiProvider.track(SwapkitTrackRequestDto(depositAddress = depositAddress))
        val originAsset =
            supportedTokens.find { it.assetId == response.fromAsset }
                ?: throw AssetNotFoundException(response.fromAsset ?: "unknown")
        val destinationAsset =
            supportedTokens.find { it.assetId == response.toAsset }
                ?: throw AssetNotFoundException(response.toAsset ?: "unknown")

        val status = mapStatus(response.status)
        val amountIn = response.fromAmount ?: BigDecimal.ZERO
        val amountOut = response.toAmount ?: BigDecimal.ZERO

        return MayaSwapQuoteStatus(
            originAsset = originAsset,
            destinationAsset = destinationAsset,
            depositAddress = getDepositAddress(depositAddress, originAsset),
            destinationAddress =
                response.toAddress
                    ?.let { getDestinationAddress(it, originAsset) }
                    ?: DynamicSwapAddress(depositAddress),
            status = status,
            amountInFormatted = amountIn,
            amountInUsd = amountIn.multiply(originAsset.usdPrice ?: BigDecimal.ZERO, MathContext.DECIMAL128),
            amountOutFormatted = amountOut,
            amountOutUsd = amountOut.multiply(destinationAsset.usdPrice ?: BigDecimal.ZERO, MathContext.DECIMAL128),
            amountInFee = BigDecimal.ZERO,
            maxSlippage = BigDecimal.ZERO,
            depositedAmountFormatted = response.fromAmount,
            refundedFormatted = if (status == SwapStatus.REFUNDED) response.toAmount else null,
            timestamp = java.time.Instant.now(),
            deadline = Clock.System.now() + DEFAULT_QUOTE_TTL,
        )
    }

    private fun mapStatus(status: String?): SwapStatus =
        when (status?.lowercase()) {
            "completed" -> SwapStatus.SUCCESS
            "refunded" -> SwapStatus.REFUNDED
            "failed" -> SwapStatus.FAILED
            "swapping" -> SwapStatus.PROCESSING
            else -> SwapStatus.PENDING
        }

    @Suppress("MagicNumber")
    private fun deadlineFrom(expiration: BigDecimal?): Instant? {
        val epoch = expiration?.toLong() ?: return null
        // Heuristic: unix seconds (~1.7e9) vs milliseconds (~1.7e12).
        return if (epoch > 1_000_000_000_000L) Instant.fromEpochMilliseconds(epoch) else Instant.fromEpochSeconds(epoch)
    }

    private suspend fun getDepositAddress(target: String, originAsset: SwapAsset): SwapAddress =
        addressResolver.depositAddress(target, originAsset)

    private suspend fun getDestinationAddress(recipient: String, originAsset: SwapAsset): SwapAddress =
        addressResolver.destinationAddress(recipient, originAsset)

    private suspend fun getRefundAddress(refund: String, originAsset: SwapAsset): SwapAddress =
        addressResolver.refundAddress(refund, originAsset)
}

/** `/tokens?provider=` namespace for Maya assets. NOT a valid quote provider id — see [MAYACHAIN_PROVIDER]. */
private const val MAYACHAIN_NAMESPACE = "MAYACHAIN"

/** Quote/route provider id. `MAYACHAIN` returns `noRoutesFound`; the streaming variant is the routable one. */
private const val MAYACHAIN_PROVIDER = "MAYACHAIN_STREAMING"

/** Fallback quote deadline when `/v3/swap` doesn't echo an expiration — the live-measured route TTL. */
private val DEFAULT_QUOTE_TTL = 75.minutes
