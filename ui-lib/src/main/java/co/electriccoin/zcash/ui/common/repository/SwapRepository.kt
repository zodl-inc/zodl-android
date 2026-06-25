package co.electriccoin.zcash.ui.common.repository

import androidx.annotation.VisibleForTesting
import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_ADDRESS
import co.electriccoin.zcash.ui.common.datasource.SwapDataSource
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_OUTPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.FLEX_INPUT
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.ZecSwapAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

interface SwapRepository {
    val assets: StateFlow<SwapAssetsData>

    val quote: StateFlow<SwapQuoteData?>

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

@Suppress("TooManyFunctions")
class SwapRepositoryImpl(
    private val swapDataSource: SwapDataSource
) : SwapRepository {
    /**
     * Scope the background refresh/quote jobs run on. A test seam: unit tests replace it with a
     * test dispatcher before invoking any method, so the fire-and-forget jobs run deterministically.
     */
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
        fun findZecSwapAsset(assets: List<SwapAsset>) = assets.find { asset -> asset is ZecSwapAsset }

        fun filterSwapAssets(assets: List<SwapAsset>) =
            assets
                .toMutableList()
                .apply {
                    removeIf {
                        val usdPrice = it.usdPrice
                        it is ZecSwapAsset || usdPrice == null || usdPrice == BigDecimal.ZERO
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
        requestQuoteJob =
            scope.launch {
                quote.update { SwapQuoteData.Loading }
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
        requestQuoteJob =
            scope.launch {
                quote.update { SwapQuoteData.Loading }
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
                    quote.update { SwapQuoteData.Success(quote = result) }
                } catch (e: Exception) {
                    quote.update { SwapQuoteData.Error(mode, e) }
                }
            }
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
