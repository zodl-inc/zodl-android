package co.electriccoin.zcash.ui.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Locks CrossPayRequest.resolveAsset's handling of an EVM chain id that is present but not in
 * EVM_CHAINS (e.g. a chain the app doesn't support yet), as distinct from a chain id that is
 * absent altogether. Before this test's regression fix, both cases fell back to whatever asset
 * the user already had selected, so a request for an unsupported chain would silently resolve to
 * the wrong chain's asset instead of being rejected (MOB-1751 review finding).
 */
class CrossPayRequestTest {
    private val ethOnArbitrum = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "arb")
    private val ethOnMainnet = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")
    private val solNative = SwapAssetTestFixture.asset(tokenTicker = "sol", chainTicker = "sol")
    private val btcNative = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")
    private val usdcOnBase =
        SwapAssetTestFixture.asset(
            tokenTicker = "usdc",
            chainTicker = "base",
            contractAddress = "0xusdc"
        )
    private val usdcOnArbitrum =
        SwapAssetTestFixture.asset(
            tokenTicker = "usdc",
            chainTicker = "arb",
            contractAddress = "0xusdc"
        )
    private val usdcOnSolana =
        SwapAssetTestFixture.asset(
            tokenTicker = "usdc",
            chainTicker = "sol",
            contractAddress = "SolMint111"
        )
    private val assets = listOf(ethOnArbitrum, ethOnMainnet)

    private fun evmNativeRequest(chainId: String?) =
        CrossPayRequest(
            address = "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359",
            amount = null,
            assetReference = CrossPayRequest.AssetReference.EvmNative(chainId)
        )

    @Test
    fun unsupportedChainIdIsRejectedRatherThanFallingBackToCurrentSelection() {
        val resolved = evmNativeRequest(chainId = "250").resolveAsset(assets, current = ethOnArbitrum)

        assertNull(resolved)
    }

    @Test
    fun missingChainIdFallsBackToCurrentSelection() {
        val resolved = evmNativeRequest(chainId = null).resolveAsset(assets, current = ethOnArbitrum)

        assertEquals(ethOnArbitrum, resolved)
    }

    @Test
    fun supportedChainIdResolvesToThatChainRegardlessOfCurrentSelection() {
        val resolved = evmNativeRequest(chainId = "1").resolveAsset(assets, current = ethOnArbitrum)

        assertEquals(ethOnMainnet, resolved)
    }

    @Test
    fun missingChainIdDoesNotFallBackToANonEvmCurrentSelection() {
        // Regression test: a SOL asset selected as `current` used to pass through unconditionally
        // (nativeTokens' permissive default matches any chain string against itself), silently
        // pairing a native-EVM request with a completely unrelated non-EVM asset.
        val resolved = evmNativeRequest(chainId = null).resolveAsset(listOf(solNative), current = solNative)

        assertNull(resolved)
    }

    @Test
    fun nativeBitcoinRequestResolvesToTheBitcoinAsset() {
        val request =
            CrossPayRequest(
                address = "1FsSia9rv4NeEwvJ2GvXrX7LyxYspbN2mo",
                amount = null,
                assetReference = CrossPayRequest.AssetReference.Native("btc")
            )

        assertEquals(btcNative, request.resolveAsset(listOf(btcNative, solNative), current = null))
    }

    private fun contractRequest(
        chain: String?,
        chainId: String?,
        address: String = "0xusdc"
    ) = CrossPayRequest(
        address = "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359",
        amount = null,
        assetReference = CrossPayRequest.AssetReference.Contract(chain, chainId, address)
    )

    @Test
    fun contractRequestWithoutChainIdResolvesToCurrentAssetChain() {
        // Same fallback fix as EvmNative, applied to ERC-20/contract requests: a contract address
        // shared across chains (a common CREATE2 deployment pattern) must resolve to the chain the
        // user actually has selected, not to any chain holding a matching address.
        val resolved =
            contractRequest(chain = null, chainId = null)
                .resolveAsset(listOf(usdcOnBase, usdcOnArbitrum), current = usdcOnArbitrum)

        assertEquals(usdcOnArbitrum, resolved)
    }

    @Test
    fun contractRequestWithoutChainIdDoesNotFallBackToANonEvmCurrentSelection() {
        val resolved =
            contractRequest(chain = null, chainId = null)
                .resolveAsset(listOf(usdcOnBase, usdcOnArbitrum, usdcOnSolana), current = usdcOnSolana)

        assertNull(resolved)
    }

    @Test
    fun contractRequestWithExplicitNonEvmChainResolvesDirectly() {
        // Solana SPL-token requests always carry an explicit `chain = "sol"`, never a chainId --
        // that value is already trusted from the parser and must not need an EVM current-asset
        // fallback the way a chainId-less ERC-20 request does.
        val resolved =
            contractRequest(chain = "sol", chainId = null, address = "SolMint111")
                .resolveAsset(listOf(usdcOnBase, usdcOnSolana), current = null)

        assertEquals(usdcOnSolana, resolved)
    }
}
