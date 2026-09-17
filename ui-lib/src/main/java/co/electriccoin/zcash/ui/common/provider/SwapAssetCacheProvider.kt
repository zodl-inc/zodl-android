package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.preference.model.entry.StringPreferenceDefault
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.near.NearSwapAsset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface SwapAssetCacheProvider {
    suspend fun get(): List<SwapAsset>

    suspend fun store(assets: List<SwapAsset>)
}

class SwapAssetCacheProviderImpl(
    private val standardPreferenceProvider: StandardPreferenceProvider,
    private val tokenIconProvider: TokenIconProvider,
    private val tokenNameProvider: TokenNameProvider,
    private val blockchainProvider: BlockchainProvider,
) : SwapAssetCacheProvider {
    private val preference = StringPreferenceDefault(PreferenceKey(CACHE_KEY), "")

    override suspend fun get(): List<SwapAsset> =
        runCatching {
            preference
                .getValue(standardPreferenceProvider())
                .takeIf { it.isNotEmpty() }
                ?.let { Json.decodeFromString<List<CachedSwapAsset>>(it) }
                .orEmpty()
                .map { it.toSwapAsset() }
        }.getOrDefault(emptyList())

    override suspend fun store(assets: List<SwapAsset>) {
        val cachedAssets =
            assets.map {
                CachedSwapAsset(
                    assetId = it.assetId,
                    tokenTicker = it.tokenTicker,
                    chainTicker = it.chainTicker,
                    contractAddress = it.contractAddress,
                    decimals = it.decimals,
                )
            }
        preference.putValue(standardPreferenceProvider(), Json.encodeToString(cachedAssets))
    }

    private fun CachedSwapAsset.toSwapAsset(): SwapAsset =
        NearSwapAsset(
            tokenTicker = tokenTicker,
            tokenName = tokenNameProvider.getName(tokenTicker),
            tokenIcon = tokenIconProvider.getIcon(tokenTicker),
            usdPrice = null,
            assetId = assetId,
            contractAddress = contractAddress,
            decimals = decimals,
            blockchain = blockchainProvider.getBlockchain(chainTicker),
        )

    @Serializable
    private data class CachedSwapAsset(
        val assetId: String,
        val tokenTicker: String,
        val chainTicker: String,
        val contractAddress: String? = null,
        val decimals: Int,
    )

    private companion object {
        const val CACHE_KEY = "swap_asset_metadata_cache_v1"
    }
}
