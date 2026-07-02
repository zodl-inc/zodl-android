package co.electriccoin.zcash.ui.common.repository

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_ADDRESS
import co.electriccoin.zcash.ui.common.datasource.AssetNotFoundException
import co.electriccoin.zcash.ui.common.datasource.SwapDataSource
import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_OUTPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.FLEX_INPUT
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.isZCashAsset
import co.electriccoin.zcash.ui.common.model.near.requireMatchingAsset
import co.electriccoin.zcash.ui.common.model.near.requireQuoteMatchesUserAmount
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

interface SwapRepository {
    val assets: StateFlow<SwapAssetsData>

    /**
     * Per-provider quote result. `null` means no request is in flight for this provider; a non-null
     * value ([SwapQuoteData.Loading], [SwapQuoteData.Success] or [SwapQuoteData.Error]) means a
     * request is in progress or has settled. Declared as [Flow] (not [StateFlow]) so implementations
     * that aggregate several providers ([SwapAggregatorRepository]) cannot be read synchronously via
     * `.value` before their combined result has settled — always collect via [Flow] operators.
     */
    val quote: Flow<SwapQuoteData?>

    fun requestRefreshAssets()

    suspend fun requestRefreshAssetsOnce()

    fun requestExactInputQuote(
        amount: BigDecimal,
        address: String,
        refundAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal
    )

    fun requestExactOutputQuote(
        amount: BigDecimal,
        address: String,
        refundAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal
    )

    fun requestFlexInputIntoZec(
        amount: BigDecimal,
        refundAddress: String,
        destinationAddress: String,
        originAsset: SwapAsset,
        slippage: BigDecimal
    )

    @Throws(ResponseException::class)
    suspend fun submitDepositTransaction(txId: String, transactionProposal: SwapTransactionProposal)

    @Throws(
        ResponseException::class,
        AssetNotFoundException::class,
        SwapAssetsUnavailableException::class,
        IllegalArgumentException::class
    )
    suspend fun checkSwapStatus(swapMetadata: TransactionSwapMetadata): SwapQuoteStatus

    fun clear()

    fun clearQuote()
}

sealed interface SwapQuoteData {
    data class Success(
        val quote: SwapQuote
    ) : SwapQuoteData

    data class Error(
        val mode: SwapMode,
        val exception: Exception
    ) : SwapQuoteData

    data object Loading : SwapQuoteData
}

data class SwapAssetsData(
    val data: List<SwapAsset>? = null,
    val zecAsset: SwapAsset? = null,
    val isLoading: Boolean = false,
    val error: Exception? = null,
)

/** Thrown by [SwapRepository.checkSwapStatus] when the supported-asset list could not be loaded. */
class SwapAssetsUnavailableException : Exception("Swap assets are unavailable")

@Suppress("TooManyFunctions")
class SwapRepositoryImpl(
    private val swapDataSource: SwapDataSource
) : SwapRepository {
    /**
     * Scope the background refresh/quote jobs run on. A test seam: unit tests replace it with a
     * test dispatcher before invoking any method, so the fire-and-forget jobs run deterministically.
     */
    @set:RestrictTo(RestrictTo.Scope.TESTS)
    @VisibleForTesting
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val assets = MutableStateFlow(SwapAssetsData())

    override val quote = MutableStateFlow<SwapQuoteData?>(null)

    private var refreshJob: Job? = null

    private var requestQuoteJob: Job? = null

    override fun requestRefreshAssets() {
        refreshJob?.cancel()
        refreshJob =
            scope.launch {
                while (true) {
                    refreshAssetsInternal()
                    delay(30.seconds)
                }
            }
    }

    override suspend fun requestRefreshAssetsOnce() {
        scope.launch { refreshAssetsInternal() }.join()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun refreshAssetsInternal() {
        fun findZecSwapAsset(assets: List<SwapAsset>) = assets.find { asset -> asset.isZCashAsset }

        fun filterSwapAssets(assets: List<SwapAsset>) =
            assets
                .toMutableList()
                .apply {
                    removeIf {
                        val usdPrice = it.usdPrice
                        it.isZCashAsset || usdPrice == null || usdPrice == BigDecimal.ZERO
                    }
                }.toList()

        assets.update { it.copy(isLoading = true) }
        try {
            val tokens = swapDataSource.getSupportedTokens()
            val filtered = filterSwapAssets(tokens)
            val zecAsset = findZecSwapAsset(tokens)
            assets.update {
                it.copy(
                    data = filtered,
                    zecAsset = zecAsset,
                    error = null,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            assets.update { assets ->
                assets.copy(
                    isLoading = false,
                    error = e.takeIf { assets.data == null }
                )
            }
        }
    }

    override fun requestExactInputQuote(
        amount: BigDecimal,
        address: String,
        refundAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal
    ) {
        requestSwapFromZecQuote(
            amount = amount,
            address = address,
            mode = EXACT_INPUT,
            refundAddress = refundAddress,
            destinationAsset = destinationAsset,
            slippage = slippage
        )
    }

    override fun requestExactOutputQuote(
        amount: BigDecimal,
        address: String,
        refundAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal
    ) {
        requestSwapFromZecQuote(
            amount = amount,
            address = address,
            mode = EXACT_OUTPUT,
            refundAddress = refundAddress,
            destinationAsset = destinationAsset,
            slippage = slippage
        )
    }

    @Suppress("TooGenericExceptionCaught")
    override fun requestFlexInputIntoZec(
        amount: BigDecimal,
        refundAddress: String,
        destinationAddress: String,
        originAsset: SwapAsset,
        slippage: BigDecimal
    ) {
        requestQuoteJob?.cancel()
        quote.update { SwapQuoteData.Loading }
        requestQuoteJob =
            scope.launch {
                val destinationAsset = assets.value.zecAsset ?: return@launch
                try {
                    val result =
                        swapDataSource.requestQuote(
                            swapMode = FLEX_INPUT,
                            amount = amount,
                            refundAddress = refundAddress,
                            originAsset = originAsset,
                            destinationAddress = destinationAddress,
                            destinationAsset = destinationAsset,
                            slippage = slippage,
                            affiliateAddress = AFFILIATE_ADDRESS
                        )
                    requireQuoteMatchesUserAmount(
                        quoted = result.amountInFormatted,
                        requested = amount,
                        decimals = result.originAsset.decimals
                    )
                    requireSupportedSelectedAsset(
                        name = "originAsset",
                        supportedAssets = assets.value.data,
                        selectedAsset = originAsset,
                        actual = result.originAsset
                    )
                    requireExpectedAsset(
                        name = "destinationAsset",
                        expected = destinationAsset,
                        actual = result.destinationAsset
                    )
                    requireMatchingAddress(
                        name = "refundAddress",
                        expected = refundAddress,
                        actual = result.refundAddress.address
                    )
                    requireMatchingAddress(
                        name = "destinationAddress",
                        expected = destinationAddress,
                        actual = result.destinationAddress.address
                    )
                    quote.update { SwapQuoteData.Success(quote = result) }
                } catch (e: Exception) {
                    quote.update { SwapQuoteData.Error(FLEX_INPUT, e) }
                }
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun requestSwapFromZecQuote(
        amount: BigDecimal,
        address: String,
        mode: SwapMode,
        refundAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal
    ) {
        requestQuoteJob?.cancel()
        quote.update { SwapQuoteData.Loading }
        requestQuoteJob =
            scope.launch {
                val originAsset = assets.value.zecAsset ?: return@launch
                try {
                    val result =
                        swapDataSource.requestQuote(
                            swapMode = mode,
                            amount = amount,
                            refundAddress = refundAddress,
                            originAsset = originAsset,
                            destinationAddress = address,
                            destinationAsset = destinationAsset,
                            slippage = slippage,
                            affiliateAddress = AFFILIATE_ADDRESS
                        )
                    when (mode) {
                        EXACT_INPUT,
                        FLEX_INPUT -> {
                            requireQuoteMatchesUserAmount(
                                quoted = result.amountInFormatted,
                                requested = amount,
                                decimals = result.originAsset.decimals
                            )
                        }

                        EXACT_OUTPUT -> {
                            requireQuoteMatchesUserAmount(
                                quoted = result.amountOutFormatted,
                                requested = amount,
                                decimals = result.destinationAsset.decimals
                            )
                        }
                    }
                    requireExpectedAsset(
                        name = "originAsset",
                        expected = originAsset,
                        actual = result.originAsset
                    )
                    requireSupportedSelectedAsset(
                        name = "destinationAsset",
                        supportedAssets = assets.value.data,
                        selectedAsset = destinationAsset,
                        actual = result.destinationAsset
                    )
                    requireMatchingAddress(
                        name = "destinationAddress",
                        expected = address,
                        actual = result.destinationAddress.address
                    )
                    requireMatchingAddress(
                        name = "refundAddress",
                        expected = refundAddress,
                        actual = result.refundAddress.address
                    )
                    quote.update { SwapQuoteData.Success(quote = result) }
                } catch (e: Exception) {
                    quote.update { SwapQuoteData.Error(mode, e) }
                }
            }
    }

    override suspend fun submitDepositTransaction(txId: String, transactionProposal: SwapTransactionProposal) {
        swapDataSource.submitDepositTransaction(
            txHash = txId,
            depositAddress = transactionProposal.destination.address
        )
    }

    override suspend fun checkSwapStatus(swapMetadata: TransactionSwapMetadata): SwapQuoteStatus {
        val result =
            swapDataSource.checkSwapStatus(
                depositAddress = swapMetadata.depositAddress,
                supportedTokens = getSupportedTokensForStatus()
            )
        requireMatchingAsset(
            name = "origin",
            expectedTokenTicker = swapMetadata.origin.tokenTicker,
            expectedChainTicker = swapMetadata.origin.chainTicker,
            actual = result.originAsset
        )
        requireMatchingAsset(
            name = "destination",
            expectedTokenTicker = swapMetadata.destination.tokenTicker,
            expectedChainTicker = swapMetadata.destination.chainTicker,
            actual = result.destinationAsset
        )
        return result
    }

    /**
     * Resolves the supported-token list the status lookup needs to map asset ids back to [SwapAsset]s.
     * Uses the already-loaded [assets] when present, otherwise refreshes once on demand. The ZEC asset
     * (kept separately in [SwapAssetsData.zecAsset], filtered out of [SwapAssetsData.data]) is appended
     * so both sides of a swap resolve.
     */
    private suspend fun getSupportedTokensForStatus(): List<SwapAsset> {
        val loaded = assets.value
        if (loaded.data != null && loaded.zecAsset != null) {
            return loaded.data + loaded.zecAsset
        }
        requestRefreshAssetsOnce()
        val refreshed = assets.firstOrNull { !it.isLoading }
        if (refreshed?.data != null && refreshed.zecAsset != null) {
            return refreshed.data + refreshed.zecAsset
        }
        refreshed?.error?.let { throw it }
        throw SwapAssetsUnavailableException()
    }

    override fun clear() {
        if (assets.value.data == null) {
            assets.update { SwapAssetsData() } // delete the error if no data found
        }
        refreshJob?.cancel()
        refreshJob = null
        clearQuote()
    }

    override fun clearQuote() {
        requestQuoteJob?.cancel()
        requestQuoteJob = null
        quote.update { null }
    }
}

val DEFAULT_SLIPPAGE = BigDecimal("2")

/**
 * Asserts the quote's ZEC-side asset matches `expected` — an independent snapshot of the repository's
 * ZEC asset — a cross-check that the right ZEC asset was used. The user-selected side is validated more
 * strictly by [requireSupportedSelectedAsset].
 */
private fun requireExpectedAsset(name: String, expected: SwapAsset?, actual: SwapAsset) {
    if (expected == null) return
    requireMatchingAsset(
        name = name,
        expectedTokenTicker = expected.tokenTicker,
        expectedChainTicker = expected.chainTicker,
        actual = actual
    )
}

/**
 * Looks the user-selected asset up in the currently-supported assets by id (requiring it to still be
 * supported) and matches the quote against that canonical record — catching a stale/unknown selection
 * and a selection whose ticker/chain disagrees with the supported record.
 */
private fun requireSupportedSelectedAsset(
    name: String,
    supportedAssets: List<SwapAsset>?,
    selectedAsset: SwapAsset,
    actual: SwapAsset
) {
    val supported = supportedAssets?.firstOrNull { it.assetId == selectedAsset.assetId }
    requireNotNull(supported) {
        "Swap quote asset mismatch: $name=${selectedAsset.assetId} is not a currently-supported swap asset"
    }
    requireMatchingAsset(
        name = name,
        expectedTokenTicker = supported.tokenTicker,
        expectedChainTicker = supported.chainTicker,
        actual = actual
    )
}

private fun requireMatchingAddress(name: String, expected: String, actual: String) {
    require(expected == actual) {
        "Swap quote address mismatch: expected $name=$expected but quote returned $actual"
    }
}
