package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.model.CompositeSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapProvider
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.assetFor
import co.electriccoin.zcash.ui.common.model.near.NearSwapAsset
import co.electriccoin.zcash.ui.common.model.swapkit.MayaSwapAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Aggregates several provider-specific [SwapRepository]s (NEAR + Maya) behind the same interface. Assets that
 * resolve to the same (chain, ticker) across providers are merged into a single [CompositeSwapAsset]; a quote
 * request fans out to every provider whose composite has a sub-asset for the pair; the better "You get" is
 * auto-selected and [selectProvider] lets the UI override it. See `docs/SwapKit Spec (Maya DEX).md` §9.
 */
interface SwapAggregatorRepository : SwapRepository {
    /** Per-provider quote results, one entry per provider that was queried; null until the first request. */
    val quotes: Flow<List<SwapQuoteData>?>

    /** Override the auto-selection, switching [quote] to the result produced by [provider]. */
    fun selectProvider(provider: SwapProvider)
}

@Suppress("TooManyFunctions")
class SwapAggregatorRepositoryImpl(
    private val swapRepositories: Map<SwapProvider, SwapRepository>,
    /**
     * Backs [assets]/[quotes]/[quote]'s `stateIn` sharing. Overridable only so unit tests can inject
     * an eager test dispatcher at *construction* time — the `stateIn` calls below run during property
     * initialization, so mutating this after construction (the [SwapRepositoryImpl] pattern) would be
     * too late to affect them.
     */
    internal val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : SwapAggregatorRepository {
    private val selectedProvider = MutableStateFlow<SwapProvider?>(null)

    override val assets: StateFlow<SwapAssetsData> =
        combine(swapRepositories.values.map { it.assets }) { snapshots -> mergeAssets(snapshots.toList()) }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = mergeAssets(swapRepositories.values.map { it.assets.value })
            )

    /** Per-provider results for providers participating in the current round; `null` if none are. */
    override val quotes: Flow<List<SwapQuoteData>?> =
        combine(swapRepositories.values.map { it.quote }) { quotes ->
            quotes.filterNotNull().ifEmpty { null }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    /**
     * Settles only once every participating provider is terminal (Success or Error) — never picks a
     * winner while a sibling provider is still [SwapQuoteData.Loading]. See [selectQuote].
     */
    override val quote: Flow<SwapQuoteData?> =
        combine(quotes, selectedProvider) { active, selected ->
            when {
                active == null -> null
                active.any { it is SwapQuoteData.Loading } -> SwapQuoteData.Loading
                else -> selectQuote(active, selected)
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    override fun selectProvider(provider: SwapProvider) = selectedProvider.update { provider }

    override fun requestRefreshAssets() = swapRepositories.values.forEach { it.requestRefreshAssets() }

    override suspend fun requestRefreshAssetsOnce() =
        coroutineScope {
            swapRepositories.values.map { async { it.requestRefreshAssetsOnce() } }.awaitAll()
            Unit
        }

    override fun requestExactInputQuote(
        amount: BigDecimal,
        address: String,
        refundAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal
    ) {
        scope.launch {
            forEachProvider(destinationAsset) { repository, subAsset ->
                repository.requestExactInputQuote(amount, address, refundAddress, subAsset, slippage)
            }
        }
    }

    override fun requestExactOutputQuote(
        amount: BigDecimal,
        address: String,
        refundAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal
    ) {
        // Maya is exact-input only — EXACT_OUTPUT routes to NEAR exclusively; clear every other
        // provider so a stale sub-quote from a prior round can't leak into this round's aggregate.
        swapRepositories.forEach { (provider, repository) ->
            if (provider != SwapProvider.NEAR) repository.clearQuote()
        }
        val repository = swapRepositories[SwapProvider.NEAR] ?: return
        val subAsset = subAssetFor(SwapProvider.NEAR, destinationAsset) ?: return
        repository.requestExactOutputQuote(amount, address, refundAddress, subAsset, slippage)
    }

    override fun requestFlexInputIntoZec(
        amount: BigDecimal,
        refundAddress: String,
        destinationAddress: String,
        originAsset: SwapAsset,
        slippage: BigDecimal
    ) {
        scope.launch {
            forEachProvider(originAsset) { repository, subAsset ->
                repository.requestFlexInputIntoZec(amount, refundAddress, destinationAddress, subAsset, slippage)
            }
        }
    }

    override suspend fun submitDepositTransaction(txId: String, transactionProposal: SwapTransactionProposal) {
        val repository =
            swapRepositories[transactionProposal.quote.provider]
                ?: throw IllegalArgumentException("No repository for provider ${transactionProposal.quote.provider}")
        repository.submitDepositTransaction(txId, transactionProposal)
    }

    override suspend fun checkSwapStatus(swapMetadata: TransactionSwapMetadata): SwapQuoteStatus {
        val provider = SwapProvider.from(swapMetadata.provider)
        val repository =
            swapRepositories[provider]
                ?: throw IllegalArgumentException("No repository for provider $provider")
        return repository.checkSwapStatus(swapMetadata)
    }

    override fun clear() {
        selectedProvider.update { null }
        swapRepositories.values.forEach { it.clear() }
    }

    override fun clearQuote() {
        selectedProvider.update { null }
        swapRepositories.values.forEach { it.clearQuote() }
    }

    /**
     * Fans a quote request out to every provider that supports [asset] — in parallel, since each
     * `request` call is a non-suspending fire-and-forget dispatch that returns as soon as its own
     * background coroutine is launched. Providers that don't support [asset] are cleared rather
     * than left alone, so a stale sub-quote from a previous round can't be mistaken for part of
     * this round once it settles (see [quote]).
     */
    private suspend fun forEachProvider(asset: SwapAsset, request: (SwapRepository, SwapAsset) -> Unit) {
        coroutineScope {
            swapRepositories.map { (provider, repository) ->
                async {
                    val subAsset = subAssetFor(provider, asset)
                    if (subAsset != null) request(repository, subAsset) else repository.clearQuote()
                }
            }.awaitAll()
        }
    }

    /** The provider-specific sub-asset to send to [provider]'s repository, or null if it can't serve the pair. */
    private fun subAssetFor(provider: SwapProvider, asset: SwapAsset): SwapAsset? =
        when (asset) {
            is CompositeSwapAsset -> asset.assetFor(provider)
            is NearSwapAsset -> asset.takeIf { provider == SwapProvider.NEAR }
            is MayaSwapAsset -> asset.takeIf { provider == SwapProvider.MAYA }
            else -> null
        }

    /** Picks a winner among fully-settled (non-[SwapQuoteData.Loading]) per-provider results. */
    private fun selectQuote(quotes: List<SwapQuoteData>, selected: SwapProvider?): SwapQuoteData? {
        val successes = quotes.filterIsInstance<SwapQuoteData.Success>()
        return if (successes.isNotEmpty()) {
            // Default selection rule (MOB-1396): the higher "You get" (received) amount.
            selected?.let { provider -> successes.firstOrNull { it.quote.provider == provider } }
                ?: successes.maxByOrNull { it.quote.amountOutFormatted }
        } else {
            quotes.firstOrNull { it is SwapQuoteData.Error }
        }
    }

    private fun mergeAssets(snapshots: List<SwapAssetsData>): SwapAssetsData {
        val merged =
            snapshots
                .flatMap { it.data.orEmpty() }
                .groupBy { mergeKey(it) }
                .map { (_, group) -> CompositeSwapAsset(group) }
        val zecAssets = snapshots.mapNotNull { it.zecAsset }
        val anyData = snapshots.any { it.data != null }
        return SwapAssetsData(
            data = merged,
            zecAsset = if (zecAssets.isEmpty()) null else CompositeSwapAsset(zecAssets),
            isLoading = snapshots.any { it.isLoading },
            error = if (anyData) null else snapshots.firstNotNullOfOrNull { it.error }
        )
    }

    private fun mergeKey(asset: SwapAsset): String =
        "${asset.chainTicker.lowercase()}:${asset.tokenTicker.lowercase()}"
}
