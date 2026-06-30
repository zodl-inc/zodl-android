package co.electriccoin.zcash.ui.common.model

import co.electriccoin.zcash.ui.common.model.near.NearSwapAsset
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import java.math.BigDecimal

/**
 * Test-only builders for swap assets. Icons use an arbitrary drawable id; nothing renders in unit
 * tests, so only the textual/identity fields matter.
 */
object SwapAssetTestFixture {
    fun blockchain(chainTicker: String = "btc"): SwapBlockchain =
        SwapBlockchain(
            chainTicker = chainTicker,
            chainName = stringRes(chainTicker.uppercase()),
            chainIcon = imageRes(0)
        )

    fun asset(
        tokenTicker: String = "btc",
        chainTicker: String = "btc",
        usdPrice: BigDecimal? = BigDecimal("100000"),
        decimals: Int = 8
    ): SwapAsset =
        NearSwapAsset(
            tokenTicker = tokenTicker,
            tokenName = stringRes(tokenTicker.uppercase()),
            tokenIcon = imageRes(0),
            usdPrice = usdPrice,
            assetId = "$tokenTicker-$chainTicker",
            decimals = decimals,
            blockchain = blockchain(chainTicker)
        )

    fun zecAsset(): SwapAsset =
        NearSwapAsset(
            tokenTicker = "zec",
            tokenName = stringRes("ZEC"),
            tokenIcon = imageRes(0),
            usdPrice = BigDecimal("30"),
            assetId = "zec-zec",
            decimals = 8,
            blockchain = blockchain("zec")
        )

    fun simpleAsset(
        tokenTicker: String = "btc",
        chainTicker: String = "btc"
    ): SimpleSwapAsset =
        DynamicSimpleSwapAsset(
            tokenTicker = tokenTicker,
            tokenName = stringRes(tokenTicker.uppercase()),
            tokenIcon = imageRes(0),
            blockchain = blockchain(chainTicker)
        )

    fun zecSimpleAsset(): SimpleSwapAsset =
        ZecSimpleSwapAsset(
            tokenTicker = "zec",
            tokenName = stringRes("ZEC"),
            tokenIcon = imageRes(0),
            blockchain = blockchain("zec")
        )

    fun assetsData(
        data: List<SwapAsset>? = listOf(asset()),
        zecAsset: SwapAsset? = zecAsset(),
        isLoading: Boolean = false,
        error: Exception? = null
    ): SwapAssetsData =
        SwapAssetsData(
            data = data,
            zecAsset = zecAsset,
            isLoading = isLoading,
            error = error
        )
}
