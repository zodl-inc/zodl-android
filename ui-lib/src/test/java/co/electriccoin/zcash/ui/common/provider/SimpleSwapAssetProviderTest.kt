package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.model.isSame
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SimpleSwapAssetProviderTest {
    private val provider =
        SimpleSwapAssetProviderImpl(
            tokenIconProvider =
                mockk {
                    every { getIcon(any()) } answers { imageRes(firstArg<String>()) }
                },
            tokenNameProvider =
                mockk {
                    every { getName(any()) } answers { stringRes(firstArg<String>()) }
                },
            blockchainProvider =
                mockk {
                    every { getBlockchain(any()) } answers {
                        val ticker = firstArg<String>()
                        SwapBlockchain(
                            chainTicker = ticker,
                            chainName = stringRes(ticker),
                            chainIcon = imageRes(ticker)
                        )
                    }
                },
        )

    @Test
    fun inclusionListContainsExpectedCoreAssets() {
        val result = provider.getCuratedSwapAssets()

        // Spot-check representative entries rather than mirroring the whole production list.
        listOf(
            "ZEC" to "zec",
            "USDC" to "eth",
            "BTC" to "btc",
            "wNEAR" to "near",
        ).forEach { (symbol, blockchain) ->
            assertTrue(result.any { it.isSame(symbol, blockchain) }, "$symbol@$blockchain should be included")
        }
        assertTrue(result.size >= 10, "expected a non-trivial curated list")
    }

    @Test
    fun inclusionListExcludesAssetNotInAllowList() {
        val result = provider.getCuratedSwapAssets()

        assertTrue(result.none { it.isSame("DOGE", "doge") })
    }

    @Test
    fun inclusionListExcludesRightSymbolOnWrongChain() {
        val result = provider.getCuratedSwapAssets()

        assertTrue(result.none { it.isSame("USDC", "tron") })
    }

    @Test
    fun inclusionListIsCaseInsensitiveForLookup() {
        val result = provider.getCuratedSwapAssets()

        assertTrue(result.any { it.isSame("zec", "ZEC") })
    }

    @Test
    fun inclusionListContainsRecentlyAddedAssets() {
        val result = provider.getCuratedSwapAssets()

        listOf(
            "DASH" to "dash",
            "BCH" to "bch",
            "ZEC" to "sol",
            "ZEC" to "near",
        ).forEach { (symbol, blockchain) ->
            assertTrue(result.any { it.isSame(symbol, blockchain) }, "$symbol@$blockchain should be included")
        }
    }

    // TokenIconProviderImpl and BlockchainProviderImpl resolve artwork by name at runtime and silently fall back
    // to a placeholder when it is missing, so a curated entry without a drawable only shows up visually. Assert
    // the drawables exist instead, mirroring the naming those providers use.
    @Test
    fun everyCuratedAssetHasTokenAndChainArtwork() {
        val missing =
            provider
                .getCuratedSwapAssets()
                .flatMap { asset ->
                    listOf(
                        "ic_token_" + asset.tokenTicker.removePrefix("$").lowercase(),
                        "ic_chain_" + asset.chainTicker.lowercase(),
                    )
                }.distinct()
                .filterNot { name ->
                    DRAWABLE_DIR.resolve("$name.png").exists() || DRAWABLE_DIR.resolve("$name.xml").exists()
                }

        assertTrue(missing.isEmpty(), "missing drawables in $DRAWABLE_DIR: $missing")
    }

    private companion object {
        private const val DRAWABLE_PATH = "ui-design-lib/src/main/res/ui/common/drawable"

        // Unit tests run with the module directory as the working directory, but walk up so the lookup also
        // works if a test runner starts from the repository root.
        val DRAWABLE_DIR: File =
            generateSequence(File("").absoluteFile) { it.parentFile }
                .map { it.resolve(DRAWABLE_PATH) }
                .firstOrNull { it.isDirectory }
                ?: error("Could not locate $DRAWABLE_PATH from ${File("").absolutePath}")
    }
}
