package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.model.FakeSwapQuote
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapProvider
import co.electriccoin.zcash.ui.common.model.SwapQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * [SwapAggregatorRepositoryImpl.quote] must only settle once every provider participating in the
 * current round is terminal (Success or Error) — never pick a winner while a sibling is still
 * [SwapQuoteData.Loading]. [FakeSwapRepository] stands in for the per-provider [SwapRepository]s so
 * each provider's quote can be driven independently and deterministically (an
 * [UnconfinedTestDispatcher]-backed scope, injected at construction — see
 * [SwapAggregatorRepositoryImpl]'s `scope` KDoc for why it must be a constructor param here rather
 * than the mutable-var seam [SwapRepositoryImpl] uses).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapAggregatorRepositoryImplTest {
    private val testScope = CoroutineScope(UnconfinedTestDispatcher())

    private val near = FakeSwapRepository()
    private val maya = FakeSwapRepository()

    private val aggregator =
        SwapAggregatorRepositoryImpl(
            swapRepositories = mapOf(SwapProvider.NEAR to near, SwapProvider.MAYA to maya),
            scope = testScope
        )

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun quoteIsNullWhenNoProviderIsActive() =
        runTest {
            assertNull(aggregator.quote.first())
        }

    @Test
    fun quoteStaysLoadingWhileAnyActiveProviderIsStillLoadingEvenAfterAnotherSucceeded() =
        runTest {
            near.quoteFlow.value = SwapQuoteData.Loading
            maya.quoteFlow.value = SwapQuoteData.Loading
            assertEquals(SwapQuoteData.Loading, aggregator.quote.first())

            // Maya answers first, but NEAR is still in flight — must not pick a winner yet.
            maya.quoteFlow.value = SwapQuoteData.Success(quote(amountOutFormatted = "5", provider = SwapProvider.MAYA))
            assertEquals(SwapQuoteData.Loading, aggregator.quote.first())

            // Only once NEAR also settles does the aggregate resolve, picking the higher "You get".
            near.quoteFlow.value = SwapQuoteData.Success(quote(amountOutFormatted = "10", provider = SwapProvider.NEAR))
            val settled = assertIs<SwapQuoteData.Success>(aggregator.quote.first())
            assertEquals(SwapProvider.NEAR, settled.quote.provider)
        }

    @Test
    fun selectProviderOverridesTheAutoSelectionOnlyAmongSettledSuccesses() =
        runTest {
            near.quoteFlow.value = SwapQuoteData.Success(quote(amountOutFormatted = "10", provider = SwapProvider.NEAR))
            maya.quoteFlow.value = SwapQuoteData.Success(quote(amountOutFormatted = "5", provider = SwapProvider.MAYA))

            aggregator.selectProvider(SwapProvider.MAYA)

            val settled = assertIs<SwapQuoteData.Success>(aggregator.quote.first())
            assertEquals(SwapProvider.MAYA, settled.quote.provider)
        }

    @Test
    fun allErrorsSettleToAnErrorNotNullOrLoading() =
        runTest {
            near.quoteFlow.value = SwapQuoteData.Error(SwapMode.EXACT_INPUT, IllegalStateException("near"))
            maya.quoteFlow.value = SwapQuoteData.Error(SwapMode.EXACT_INPUT, IllegalStateException("maya"))

            assertIs<SwapQuoteData.Error>(aggregator.quote.first())
        }

    @Test
    fun providerNotSupportingTheAssetIsResetAndNeverBlocksSettlement() =
        runTest {
            // Simulate a stale Success left over from a previous round that Maya did participate in.
            maya.quoteFlow.value =
                SwapQuoteData.Success(quote(amountOutFormatted = "999", provider = SwapProvider.MAYA))

            // btc-on-NEAR is a plain NearSwapAsset: only the NEAR sub-repository can serve it.
            val nearOnlyAsset = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")
            aggregator.requestExactInputQuote(
                amount = BigDecimal.ONE,
                address = "address",
                refundAddress = "refund",
                destinationAsset = nearOnlyAsset,
                slippage = BigDecimal("2")
            )

            // Maya was cleared rather than left stale, and NEAR was dispatched (fake sets Loading).
            assertNull(maya.quoteFlow.value)
            assertEquals(1, maya.clearQuoteCallCount)
            assertEquals(SwapQuoteData.Loading, aggregator.quote.first())

            // Maya being permanently null (not participating) doesn't block settlement once NEAR resolves.
            near.quoteFlow.value = SwapQuoteData.Success(quote(amountOutFormatted = "1", provider = SwapProvider.NEAR))
            val settled = assertIs<SwapQuoteData.Success>(aggregator.quote.first())
            assertEquals(SwapProvider.NEAR, settled.quote.provider)
        }

    private fun quote(amountOutFormatted: String, provider: SwapProvider): SwapQuote =
        FakeSwapQuote(
            originAsset = SwapAssetTestFixture.zecAsset(),
            destinationAsset = SwapAssetTestFixture.asset(),
            mode = SwapMode.EXACT_INPUT,
            amountIn = BigDecimal.ONE,
            amountInFormatted = BigDecimal.ONE,
            amountOutFormatted = BigDecimal(amountOutFormatted),
            depositAddress = "deposit",
            destinationAddress = "destination",
            refundAddress = "refund",
            provider = provider
        )
}

/**
 * A minimal, hand-written [SwapRepository] double: [quoteFlow] lets a test drive that provider's
 * quote state directly, without going through the real (network-backed) request/settle machinery
 * that [SwapRepositoryImplTest] already covers.
 */
private class FakeSwapRepository : SwapRepository {
    override val assets = MutableStateFlow(SwapAssetsData())

    val quoteFlow = MutableStateFlow<SwapQuoteData?>(null)
    override val quote = quoteFlow

    var clearQuoteCallCount = 0
        private set

    override fun requestRefreshAssets() = Unit

    override suspend fun requestRefreshAssetsOnce() = Unit

    override fun requestExactInputQuote(
        amount: BigDecimal,
        address: String,
        refundAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal
    ) {
        quoteFlow.value = SwapQuoteData.Loading
    }

    override fun requestExactOutputQuote(
        amount: BigDecimal,
        address: String,
        refundAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal
    ) {
        quoteFlow.value = SwapQuoteData.Loading
    }

    override fun requestFlexInputIntoZec(
        amount: BigDecimal,
        refundAddress: String,
        destinationAddress: String,
        originAsset: SwapAsset,
        slippage: BigDecimal
    ) {
        quoteFlow.value = SwapQuoteData.Loading
    }

    override suspend fun submitDepositTransaction(txId: String, transactionProposal: SwapTransactionProposal) = Unit

    override suspend fun checkSwapStatus(swapMetadata: TransactionSwapMetadata) =
        error("not used by SwapAggregatorRepositoryImplTest")

    override fun clear() = clearQuote()

    override fun clearQuote() {
        clearQuoteCallCount++
        quoteFlow.value = null
    }
}
