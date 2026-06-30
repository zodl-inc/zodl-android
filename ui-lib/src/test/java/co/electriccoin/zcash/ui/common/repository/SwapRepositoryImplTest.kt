package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.WalletAddress
import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_ADDRESS
import co.electriccoin.zcash.ui.common.datasource.SwapDataSource
import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.model.FakeSwapQuote
import co.electriccoin.zcash.ui.common.model.SimpleSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [SwapRepositoryImpl] owns the shared `assets`/`quote` state (the selected asset + slippage now
 * live in the ViewModels and are passed into the quote methods). The repository's coroutine scope is
 * injected with an [UnconfinedTestDispatcher] so the fire-and-forget refresh/quote jobs run eagerly
 * and deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapRepositoryImplTest {
    private val testScope = CoroutineScope(UnconfinedTestDispatcher())

    private val zec = SwapAssetTestFixture.zecAsset()
    private val btc = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")
    private val sol = SwapAssetTestFixture.asset(tokenTicker = "sol", chainTicker = "sol")
    private val btcOnEth = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "eth")

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    /** Builds the repository with its background scope swapped for the eager test scope. */
    private fun repository(dataSource: SwapDataSource): SwapRepositoryImpl =
        SwapRepositoryImpl(dataSource).apply { scope = testScope }

    @Test
    fun refreshDropsZecAndNonPricedAssetsAndExtractsZec() =
        runTest {
            val zeroPriced =
                SwapAssetTestFixture.asset(tokenTicker = "zer", chainTicker = "eth", usdPrice = BigDecimal.ZERO)
            val nullPriced =
                SwapAssetTestFixture.asset(tokenTicker = "nul", chainTicker = "eth", usdPrice = null)
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc, zeroPriced, nullPriced)
                }
            val repository = repository(dataSource)

            repository.requestRefreshAssetsOnce()

            val data = repository.assets.value
            assertEquals(listOf(btc), data.data)
            assertEquals(zec, data.zecAsset)
            assertFalse(data.isLoading)
            assertNull(data.error)
        }

    @Test
    fun refreshSetsErrorWhenNoExistingData() =
        runTest {
            val failure = RuntimeException("boom")
            val dataSource = mockk<SwapDataSource> { coEvery { getSupportedTokens() } throws failure }
            val repository = repository(dataSource)

            repository.requestRefreshAssetsOnce()

            val data = repository.assets.value
            assertEquals(failure, data.error)
            assertNull(data.data)
            assertFalse(data.isLoading)
        }

    @Test
    fun refreshErrorKeepsPreviouslyLoadedDataAndDoesNotSurfaceError() =
        runTest {
            var call = 0
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } answers {
                        if (call++ == 0) listOf(zec, btc) else error("boom")
                    }
                }
            val repository = repository(dataSource)

            repository.requestRefreshAssetsOnce() // success: data loaded
            repository.requestRefreshAssetsOnce() // failure: must not wipe data or surface an error

            val data = repository.assets.value
            assertEquals(listOf(btc), data.data)
            assertNull(data.error)
            assertFalse(data.isLoading)
        }

    @Test
    fun exactInputQuoteUsesZecOriginSelectedDestinationAndSlippage() =
        runTest {
            val quote = fakeQuote()
            val dataSource = dataSourceReturning(quote)
            val repository = loadedRepository(dataSource)

            repository.requestExactInputQuote(
                amount = BigDecimal("1"),
                address = "destination",
                refundAddress = "refund",
                destinationAsset = btc,
                slippage = BigDecimal("3")
            )

            val result = repository.quote.value
            assertIs<SwapQuoteData.Success>(result)
            assertEquals(quote, result.quote)
            coVerify(exactly = 1) {
                dataSource.requestQuote(
                    swapMode = SwapMode.EXACT_INPUT,
                    amount = BigDecimal("1"),
                    refundAddress = "refund",
                    originAsset = zec,
                    destinationAddress = "destination",
                    destinationAsset = btc,
                    slippage = BigDecimal("3"),
                    affiliateAddress = AFFILIATE_ADDRESS
                )
            }
        }

    @Test
    fun exactOutputQuoteRequestsExactOutputModeFromZec() =
        runTest {
            val dataSource = dataSourceReturning(fakeQuote(mode = SwapMode.EXACT_OUTPUT))
            val repository = loadedRepository(dataSource)

            repository.requestExactOutputQuote(
                amount = BigDecimal("1"),
                address = "destination",
                refundAddress = "refund",
                destinationAsset = btc,
                slippage = BigDecimal("2")
            )

            coVerify(exactly = 1) {
                dataSource.requestQuote(
                    swapMode = SwapMode.EXACT_OUTPUT,
                    amount = BigDecimal("1"),
                    refundAddress = "refund",
                    originAsset = zec,
                    destinationAddress = "destination",
                    destinationAsset = btc,
                    slippage = BigDecimal("2"),
                    affiliateAddress = AFFILIATE_ADDRESS
                )
            }
        }

    @Test
    fun flexInputQuoteUsesSelectedOriginAndZecDestination() =
        runTest {
            val dataSource = dataSourceReturning(flexQuote())
            val repository = loadedRepository(dataSource)

            repository.requestFlexInputIntoZec(
                amount = BigDecimal("2"),
                refundAddress = "refund",
                destinationAddress = "destination",
                originAsset = btc,
                slippage = BigDecimal("2")
            )

            assertIs<SwapQuoteData.Success>(repository.quote.value)
            coVerify(exactly = 1) {
                dataSource.requestQuote(
                    swapMode = SwapMode.FLEX_INPUT,
                    amount = BigDecimal("2"),
                    refundAddress = "refund",
                    originAsset = btc,
                    destinationAddress = "destination",
                    destinationAsset = zec,
                    slippage = BigDecimal("2"),
                    affiliateAddress = AFFILIATE_ADDRESS
                )
            }
        }

    @Test
    fun flexQuoteFailureProducesFlexErrorState() =
        runTest {
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc)
                    coEvery {
                        requestQuote(any(), any(), any(), any(), any(), any(), any(), any())
                    } throws RuntimeException("quote failed")
                }
            val repository = loadedRepository(dataSource)

            repository.requestFlexInputIntoZec(
                amount = BigDecimal("2"),
                refundAddress = "refund",
                destinationAddress = "destination",
                originAsset = btc,
                slippage = BigDecimal("2")
            )

            val result = repository.quote.value
            assertIs<SwapQuoteData.Error>(result)
            assertEquals(SwapMode.FLEX_INPUT, result.mode)
        }

    @Test
    fun exactInputQuoteFailureProducesExactInputErrorState() =
        runTest {
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc)
                    coEvery {
                        requestQuote(any(), any(), any(), any(), any(), any(), any(), any())
                    } throws RuntimeException("quote failed")
                }
            val repository = loadedRepository(dataSource)

            repository.requestExactInputQuote(
                amount = BigDecimal("1"),
                address = "destination",
                refundAddress = "refund",
                destinationAsset = btc,
                slippage = BigDecimal("2")
            )

            val result = repository.quote.value
            assertIs<SwapQuoteData.Error>(result)
            // The error must carry the request's own mode, not a hardcoded one.
            assertEquals(SwapMode.EXACT_INPUT, result.mode)
        }

    @Test
    fun quoteIsNoOpWhenAssetsNotLoaded() =
        runTest {
            // Without a prior refresh there is no ZEC asset, so the request short-circuits: the quote is
            // left in Loading and the data source is never queried.
            val dataSource = mockk<SwapDataSource>(relaxed = true)
            val repository = repository(dataSource)

            repository.requestExactInputQuote(
                amount = BigDecimal("1"),
                address = "destination",
                refundAddress = "refund",
                destinationAsset = btc,
                slippage = BigDecimal("2")
            )

            assertEquals(SwapQuoteData.Loading, repository.quote.value)
            coVerify(exactly = 0) { dataSource.requestQuote(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

    // region quote validation — the repo fails the quote closed when it is inconsistent with the request

    @Test
    fun exactInputQuoteRejectsAmountMismatch() =
        runTest {
            val repository = loadedRepository(dataSourceReturning(fakeQuote(amountInFormatted = BigDecimal("999"))))
            repository.requestExactInputQuote(BigDecimal("1"), "destination", "refund", btc, BigDecimal("2"))
            assertExactInputError(repository)
        }

    @Test
    fun exactInputQuoteRejectsNonZecOrigin() =
        runTest {
            val repository = loadedRepository(dataSourceReturning(fakeQuote(originAsset = btc)))
            repository.requestExactInputQuote(BigDecimal("1"), "destination", "refund", btc, BigDecimal("2"))
            assertExactInputError(repository)
        }

    @Test
    fun exactInputQuoteRejectsUnsupportedSelectedAsset() =
        runTest {
            // sol is not in the loaded supported list ([btc]); the selected destination must still be supported.
            val repository = loadedRepository(dataSourceReturning(fakeQuote(destinationAsset = sol)))
            repository.requestExactInputQuote(BigDecimal("1"), "destination", "refund", sol, BigDecimal("2"))
            assertExactInputError(repository)
        }

    @Test
    fun exactInputQuoteRejectsReturnedAssetMismatch() =
        runTest {
            // Selected btc is supported, but the quote echoes btc-on-eth: same ticker, different chain.
            val repository = loadedRepository(dataSourceReturning(fakeQuote(destinationAsset = btcOnEth)))
            repository.requestExactInputQuote(BigDecimal("1"), "destination", "refund", btc, BigDecimal("2"))
            assertExactInputError(repository)
        }

    @Test
    fun exactInputQuoteRejectsDestinationAddressMismatch() =
        runTest {
            val repository = loadedRepository(dataSourceReturning(fakeQuote(destinationAddress = "wrong")))
            repository.requestExactInputQuote(BigDecimal("1"), "destination", "refund", btc, BigDecimal("2"))
            assertExactInputError(repository)
        }

    @Test
    fun exactInputQuoteRejectsRefundAddressMismatch() =
        runTest {
            val repository = loadedRepository(dataSourceReturning(fakeQuote(refundAddress = "wrong")))
            repository.requestExactInputQuote(BigDecimal("1"), "destination", "refund", btc, BigDecimal("2"))
            assertExactInputError(repository)
        }

    @Test
    fun exactOutputQuoteRejectsAmountOutMismatch() =
        runTest {
            // EXACT_OUTPUT validates the requested amount against amountOutFormatted, not amountInFormatted.
            val repository =
                loadedRepository(
                    dataSourceReturning(fakeQuote(mode = SwapMode.EXACT_OUTPUT, amountOutFormatted = BigDecimal("999")))
                )
            repository.requestExactOutputQuote(BigDecimal("1"), "destination", "refund", btc, BigDecimal("2"))
            val result = repository.quote.value
            assertIs<SwapQuoteData.Error>(result)
            assertEquals(SwapMode.EXACT_OUTPUT, result.mode)
        }

    @Test
    fun flexQuoteRejectsAmountInMismatch() =
        runTest {
            val repository = loadedRepository(dataSourceReturning(flexQuote(amountInFormatted = BigDecimal("999"))))
            repository.requestFlexInputIntoZec(BigDecimal("2"), "refund", "destination", btc, BigDecimal("2"))
            assertFlexError(repository)
        }

    @Test
    fun flexQuoteRejectsNonZecDestination() =
        runTest {
            val repository = loadedRepository(dataSourceReturning(flexQuote(destinationAsset = btc)))
            repository.requestFlexInputIntoZec(BigDecimal("2"), "refund", "destination", btc, BigDecimal("2"))
            assertFlexError(repository)
        }

    @Test
    fun flexQuoteRejectsUnsupportedSelectedOrigin() =
        runTest {
            val repository = loadedRepository(dataSourceReturning(flexQuote(originAsset = sol)))
            repository.requestFlexInputIntoZec(BigDecimal("2"), "refund", "destination", sol, BigDecimal("2"))
            assertFlexError(repository)
        }

    @Test
    fun flexQuoteRejectsRefundAddressMismatch() =
        runTest {
            val repository = loadedRepository(dataSourceReturning(flexQuote(refundAddress = "wrong")))
            repository.requestFlexInputIntoZec(BigDecimal("2"), "refund", "destination", btc, BigDecimal("2"))
            assertFlexError(repository)
        }

    // endregion

    @Test
    fun clearResetsAssetsWhenNoDataAndClearsQuote() =
        runTest {
            val dataSource = mockk<SwapDataSource> { coEvery { getSupportedTokens() } throws RuntimeException("boom") }
            val repository = repository(dataSource)
            repository.requestRefreshAssetsOnce() // leaves an error and no data
            assertNotNull(repository.assets.value.error)

            repository.clear()

            assertEquals(SwapAssetsData(), repository.assets.value) // error cleared
            assertNull(repository.quote.value)
        }

    @Test
    fun clearKeepsLoadedAssetsButClearsQuote() =
        runTest {
            val dataSource = dataSourceReturning(fakeQuote())
            val repository = loadedRepository(dataSource)
            repository.requestExactInputQuote(BigDecimal("1"), "destination", "refund", btc, BigDecimal("2"))
            assertIs<SwapQuoteData.Success>(repository.quote.value)

            repository.clear()

            assertEquals(listOf(btc), repository.assets.value.data)
            assertEquals(zec, repository.assets.value.zecAsset)
            assertNull(repository.quote.value)
        }

    @Test
    fun clearQuoteResetsQuoteToNull() =
        runTest {
            val repository = loadedRepository(dataSourceReturning(fakeQuote()))
            repository.requestExactInputQuote(BigDecimal("1"), "destination", "refund", btc, BigDecimal("2"))
            assertIs<SwapQuoteData.Success>(repository.quote.value)

            repository.clearQuote()

            assertNull(repository.quote.value)
        }

    @Test
    fun continuousRefreshPopulatesAssets() {
        val repository = repository(dataSourceReturning(mockk()))

        repository.requestRefreshAssets() // first iteration runs eagerly; the 30s delay parks the rest

        assertEquals(listOf(btc), repository.assets.value.data)
    }

    @Test
    fun submitDepositTransactionForwardsTxIdAndProposalDepositAddress() =
        runTest {
            val dataSource = mockk<SwapDataSource>(relaxed = true)
            val repository = repository(dataSource)
            val walletAddress = mockk<WalletAddress> { every { address } returns "deposit-address" }
            val proposal = mockk<SwapTransactionProposal> { every { destination } returns walletAddress }

            repository.submitDepositTransaction(txId = "tx1", transactionProposal = proposal)

            coVerify(exactly = 1) {
                dataSource.submitDepositTransaction(txHash = "tx1", depositAddress = "deposit-address")
            }
        }

    @Test
    fun checkSwapStatusPassesLoadedSupportedTokensIncludingZec() =
        runTest {
            val status =
                mockk<SwapQuoteStatus> {
                    every { originAsset } returns btc
                    every { destinationAsset } returns zec
                }
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc)
                    coEvery { checkSwapStatus(any(), any()) } returns status
                }
            val repository = loadedRepository(dataSource)

            val result = repository.checkSwapStatus(swapMetadata())

            assertEquals(status, result)
            // The repo supplies the tokens itself: the priced list plus the separately-kept ZEC asset.
            coVerify(exactly = 1) {
                dataSource.checkSwapStatus(depositAddress = "deposit-address", supportedTokens = listOf(btc, zec))
            }
        }

    @Test
    fun checkSwapStatusRefreshesAssetsWhenNotYetLoaded() =
        runTest {
            val status =
                mockk<SwapQuoteStatus> {
                    every { originAsset } returns btc
                    every { destinationAsset } returns zec
                }
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc)
                    coEvery { checkSwapStatus(any(), any()) } returns status
                }
            val repository = repository(dataSource) // assets not loaded yet

            val result = repository.checkSwapStatus(swapMetadata())

            assertEquals(status, result)
            coVerify(exactly = 1) { dataSource.getSupportedTokens() } // refreshed once on demand
            coVerify(exactly = 1) {
                dataSource.checkSwapStatus(depositAddress = "deposit-address", supportedTokens = listOf(btc, zec))
            }
        }

    @Test
    fun checkSwapStatusFailsWithSwapAssetsUnavailableWhenNoZecAssetCanBeLoaded() =
        runTest {
            // The supported list has no ZEC asset, so the repo can't assemble both sides and fails closed.
            val dataSource = mockk<SwapDataSource> { coEvery { getSupportedTokens() } returns listOf(btc) }
            val repository = repository(dataSource)

            assertFailsWith<SwapAssetsUnavailableException> {
                repository.checkSwapStatus(swapMetadata())
            }
        }

    @Test
    fun checkSwapStatusRethrowsTheAssetRefreshErrorInsteadOfWrappingIt() =
        runTest {
            // When the refresh itself failed, the original error is surfaced — not SwapAssetsUnavailableException.
            val failure = RuntimeException("boom")
            val dataSource = mockk<SwapDataSource> { coEvery { getSupportedTokens() } throws failure }
            val repository = repository(dataSource)

            val thrown = assertFailsWith<RuntimeException> { repository.checkSwapStatus(swapMetadata()) }
            assertEquals("boom", thrown.message)
        }

    @Test
    fun checkSwapStatusRejectsServerAssetsThatDoNotMatchStoredMetadata() =
        runTest {
            // Stored origin is ETH but the server returns a BTC origin -> requireMatchingAsset fails closed.
            val status =
                mockk<SwapQuoteStatus> {
                    every { originAsset } returns btc
                    every { destinationAsset } returns zec
                }
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc)
                    coEvery { checkSwapStatus(any(), any()) } returns status
                }
            val repository = loadedRepository(dataSource)
            val metadata =
                swapMetadata(from = SwapAssetTestFixture.simpleAsset(tokenTicker = "eth", chainTicker = "eth"))

            assertFailsWith<IllegalArgumentException> { repository.checkSwapStatus(metadata) }
        }

    @Test
    fun checkSwapStatusAcceptsServerAssetsThatMatchStoredMetadata() =
        runTest {
            val status =
                mockk<SwapQuoteStatus> {
                    every { originAsset } returns btc
                    every { destinationAsset } returns zec
                }
            val dataSource =
                mockk<SwapDataSource> {
                    coEvery { getSupportedTokens() } returns listOf(zec, btc)
                    coEvery { checkSwapStatus(any(), any()) } returns status
                }
            val repository = loadedRepository(dataSource)

            assertEquals(status, repository.checkSwapStatus(swapMetadata()))
        }

    private fun swapMetadata(
        address: String = "deposit-address",
        from: SimpleSwapAsset = SwapAssetTestFixture.simpleAsset(tokenTicker = "btc", chainTicker = "btc"),
        to: SimpleSwapAsset = SwapAssetTestFixture.zecSimpleAsset()
    ): TransactionSwapMetadata =
        mockk {
            every { depositAddress } returns address
            every { origin } returns from
            every { destination } returns to
        }

    @Suppress("LongParameterList")
    private fun fakeQuote(
        originAsset: SwapAsset = zec,
        destinationAsset: SwapAsset = btc,
        mode: SwapMode = SwapMode.EXACT_INPUT,
        amountIn: BigDecimal = BigDecimal("100"),
        amountInFormatted: BigDecimal = BigDecimal("1"),
        amountOutFormatted: BigDecimal = BigDecimal("1"),
        depositAddress: String = "deposit",
        destinationAddress: String = "destination",
        refundAddress: String = "refund"
    ): SwapQuote =
        FakeSwapQuote(
            originAsset = originAsset,
            destinationAsset = destinationAsset,
            mode = mode,
            amountIn = amountIn,
            amountInFormatted = amountInFormatted,
            amountOutFormatted = amountOutFormatted,
            depositAddress = depositAddress,
            destinationAddress = destinationAddress,
            refundAddress = refundAddress
        )

    /** A valid flex-input quote (selected origin -> ZEC) unless overridden to trip a specific check. */
    private fun flexQuote(
        originAsset: SwapAsset = btc,
        destinationAsset: SwapAsset = zec,
        amountInFormatted: BigDecimal = BigDecimal("2"),
        refundAddress: String = "refund",
        destinationAddress: String = "destination"
    ): SwapQuote =
        fakeQuote(
            originAsset = originAsset,
            destinationAsset = destinationAsset,
            mode = SwapMode.FLEX_INPUT,
            amountInFormatted = amountInFormatted,
            refundAddress = refundAddress,
            destinationAddress = destinationAddress
        )

    private fun assertExactInputError(repository: SwapRepositoryImpl) {
        val result = repository.quote.value
        assertIs<SwapQuoteData.Error>(result)
        assertEquals(SwapMode.EXACT_INPUT, result.mode)
    }

    private fun assertFlexError(repository: SwapRepositoryImpl) {
        val result = repository.quote.value
        assertIs<SwapQuoteData.Error>(result)
        assertEquals(SwapMode.FLEX_INPUT, result.mode)
    }

    private fun dataSourceReturning(quote: SwapQuote) =
        mockk<SwapDataSource> {
            coEvery { getSupportedTokens() } returns listOf(zec, btc)
            coEvery { requestQuote(any(), any(), any(), any(), any(), any(), any(), any()) } returns quote
        }

    /** Builds a repository on the test scope and loads its assets (so a ZEC asset is available). */
    private suspend fun loadedRepository(dataSource: SwapDataSource): SwapRepositoryImpl =
        repository(dataSource).also { it.requestRefreshAssetsOnce() }
}
