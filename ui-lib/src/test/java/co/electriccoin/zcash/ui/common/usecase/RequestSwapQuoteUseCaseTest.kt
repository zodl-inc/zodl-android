package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.TexUnsupportedOnKSException
import co.electriccoin.zcash.ui.common.model.DynamicSwapAddress
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.insufficientfunds.InsufficientFundsArgs
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteArgs
import co.electriccoin.zcash.ui.screen.texunsupported.TEXUnsupportedArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Instant

/**
 * Exhaustive coverage of [RequestSwapQuoteUseCase]: each public request method crossed with every
 * validation point, both account types on the proposal-creating paths, and every failure branch
 * (TEX-unsupported, insufficient-funds, generic). Driven via the public methods with runBlocking —
 * the use case manages its own dispatchers (the NEAR data-source test uses the same pattern).
 *
 * Notes on cells deliberately not enumerated: the account type is only reached after validation
 * passes, so validation failures are exercised once per method (not per account); flex input never
 * creates a proposal, so it has no account/failure cells; and a Zashi account never produces a
 * TEX-unsupported error, so that cell is meaningless.
 */
@Suppress("TooManyFunctions", "LargeClass")
class RequestSwapQuoteUseCaseTest {
    private val zec = SwapAssetTestFixture.zecAsset()
    private val btc = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")
    private val btcOnEth = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "eth")
    private val eth = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")
    private val sol = SwapAssetTestFixture.asset(tokenTicker = "sol", chainTicker = "sol")

    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)
    private val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
    private val zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true)
    private val keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = StandardTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // region requestExactInput — validation

    @Test
    fun exactInputRejectsAmountMismatch() =
        runBlocking {
            useCase(swapQuote(originAsset = zec, destinationAsset = btc, amountInFormatted = BigDecimal("999")))
                .exactInput()
            assertNavigatedToError()
        }

    @Test
    fun exactInputRejectsNonZecOrigin() =
        runBlocking {
            useCase(swapQuote(originAsset = btc, destinationAsset = btc)).exactInput()
            assertNavigatedToError()
        }

    @Test
    fun exactInputRejectsUnsupportedSelectedAsset() =
        runBlocking {
            useCase(swapQuote(originAsset = zec, destinationAsset = sol)).exactInput(selectedAsset = sol)
            assertNavigatedToError()
        }

    @Test
    fun exactInputRejectsReturnedAssetMismatch() =
        runBlocking {
            useCase(swapQuote(originAsset = zec, destinationAsset = btcOnEth)).exactInput(selectedAsset = btc)
            assertNavigatedToError()
        }

    @Test
    fun exactInputRejectsDestinationAddressMismatch() =
        runBlocking {
            useCase(swapQuote(originAsset = zec, destinationAsset = btc, destinationAddress = "wrong")).exactInput()
            assertNavigatedToError()
        }

    @Test
    fun exactInputRejectsRefundAddressMismatch() =
        runBlocking {
            useCase(swapQuote(originAsset = zec, destinationAsset = btc, refundAddress = "wrong")).exactInput()
            assertNavigatedToError()
        }

    // endregion
    // region requestExactInput — proposal happy + failures

    @Test
    fun exactInputZashiCreatesProposalAndForwards() =
        runBlocking {
            useCase(swapQuote(originAsset = zec, destinationAsset = btc), zashi()).exactInput()
            coVerify { zashiProposalRepository.createExactInputSwapProposal(any(), any()) }
            assertForwardedToQuote()
        }

    @Test
    fun exactInputKeystoneCreatesProposalAndPcztAndForwards() =
        runBlocking {
            useCase(swapQuote(originAsset = zec, destinationAsset = btc), keystone()).exactInput()
            coVerify { keystoneProposalRepository.createExactInputSwapProposal(any(), any()) }
            coVerify { keystoneProposalRepository.createPCZTFromProposal() }
            assertForwardedToQuote()
        }

    @Test
    fun exactInputZashiInsufficientFunds() =
        runBlocking {
            coEvery { zashiProposalRepository.createExactInputSwapProposal(any(), any()) } throws
                InsufficientFundsException()
            useCase(swapQuote(originAsset = zec, destinationAsset = btc), zashi()).exactInput()
            assertNavigatedToInsufficientFunds()
        }

    @Test
    fun exactInputKeystoneInsufficientFunds() =
        runBlocking {
            coEvery { keystoneProposalRepository.createExactInputSwapProposal(any(), any()) } throws
                InsufficientFundsException()
            useCase(swapQuote(originAsset = zec, destinationAsset = btc), keystone()).exactInput()
            assertNavigatedToInsufficientFunds()
        }

    @Test
    fun exactInputKeystoneTexUnsupported() =
        runBlocking {
            coEvery { keystoneProposalRepository.createExactInputSwapProposal(any(), any()) } throws
                TexUnsupportedOnKSException()
            useCase(swapQuote(originAsset = zec, destinationAsset = btc), keystone()).exactInput()
            assertNavigatedToTexUnsupported()
        }

    @Test
    fun exactInputZashiGenericError() =
        runBlocking {
            coEvery { zashiProposalRepository.createExactInputSwapProposal(any(), any()) } throws TestException()
            useCase(swapQuote(originAsset = zec, destinationAsset = btc), zashi()).exactInput()
            assertNavigatedToError()
        }

    @Test
    fun exactInputKeystoneGenericError() =
        runBlocking {
            coEvery { keystoneProposalRepository.createExactInputSwapProposal(any(), any()) } throws TestException()
            useCase(swapQuote(originAsset = zec, destinationAsset = btc), keystone()).exactInput()
            assertNavigatedToError()
        }

    @Test
    fun exactInputRejectsNonIntegralProposalAmount() =
        runBlocking {
            // The zatoshi conversion happens before the account branch, so it is account-independent.
            useCase(swapQuote(originAsset = zec, destinationAsset = btc, amountIn = BigDecimal("100.5")), zashi())
                .exactInput()
            assertNavigatedToError()
        }

    // endregion
    // region requestExactOutput — validation

    @Test
    fun exactOutputRejectsAmountMismatch() =
        runBlocking {
            useCase(exactOutputQuote(amountOutFormatted = BigDecimal("999"))).exactOutput()
            assertNavigatedToError()
        }

    @Test
    fun exactOutputRejectsNonZecOrigin() =
        runBlocking {
            useCase(exactOutputQuote(originAsset = btc)).exactOutput()
            assertNavigatedToError()
        }

    @Test
    fun exactOutputRejectsUnsupportedSelectedAsset() =
        runBlocking {
            useCase(exactOutputQuote(destinationAsset = sol)).exactOutput(selectedAsset = sol)
            assertNavigatedToError()
        }

    @Test
    fun exactOutputRejectsReturnedAssetMismatch() =
        runBlocking {
            useCase(exactOutputQuote(destinationAsset = btcOnEth)).exactOutput(selectedAsset = btc)
            assertNavigatedToError()
        }

    @Test
    fun exactOutputRejectsDestinationAddressMismatch() =
        runBlocking {
            useCase(exactOutputQuote(destinationAddress = "wrong")).exactOutput()
            assertNavigatedToError()
        }

    @Test
    fun exactOutputRejectsRefundAddressMismatch() =
        runBlocking {
            useCase(exactOutputQuote(refundAddress = "wrong")).exactOutput()
            assertNavigatedToError()
        }

    // endregion
    // region requestExactOutput — proposal happy + failures

    @Test
    fun exactOutputZashiCreatesProposalAndForwards() =
        runBlocking {
            useCase(exactOutputQuote(), zashi()).exactOutput()
            coVerify { zashiProposalRepository.createExactOutputSwapProposal(any(), any()) }
            assertForwardedToQuote()
        }

    @Test
    fun exactOutputKeystoneCreatesProposalAndPcztAndForwards() =
        runBlocking {
            useCase(exactOutputQuote(), keystone()).exactOutput()
            coVerify { keystoneProposalRepository.createExactOutputSwapProposal(any(), any()) }
            coVerify { keystoneProposalRepository.createPCZTFromProposal() }
            assertForwardedToQuote()
        }

    @Test
    fun exactOutputZashiInsufficientFunds() =
        runBlocking {
            coEvery { zashiProposalRepository.createExactOutputSwapProposal(any(), any()) } throws
                InsufficientFundsException()
            useCase(exactOutputQuote(), zashi()).exactOutput()
            assertNavigatedToInsufficientFunds()
        }

    @Test
    fun exactOutputKeystoneInsufficientFunds() =
        runBlocking {
            coEvery { keystoneProposalRepository.createExactOutputSwapProposal(any(), any()) } throws
                InsufficientFundsException()
            useCase(exactOutputQuote(), keystone()).exactOutput()
            assertNavigatedToInsufficientFunds()
        }

    @Test
    fun exactOutputKeystoneTexUnsupported() =
        runBlocking {
            coEvery { keystoneProposalRepository.createExactOutputSwapProposal(any(), any()) } throws
                TexUnsupportedOnKSException()
            useCase(exactOutputQuote(), keystone()).exactOutput()
            assertNavigatedToTexUnsupported()
        }

    @Test
    fun exactOutputZashiGenericError() =
        runBlocking {
            coEvery { zashiProposalRepository.createExactOutputSwapProposal(any(), any()) } throws TestException()
            useCase(exactOutputQuote(), zashi()).exactOutput()
            assertNavigatedToError()
        }

    @Test
    fun exactOutputKeystoneGenericError() =
        runBlocking {
            coEvery { keystoneProposalRepository.createExactOutputSwapProposal(any(), any()) } throws TestException()
            useCase(exactOutputQuote(), keystone()).exactOutput()
            assertNavigatedToError()
        }

    @Test
    fun exactOutputRejectsNonIntegralProposalAmount() =
        runBlocking {
            useCase(exactOutputQuote(amountIn = BigDecimal("100.5")), zashi()).exactOutput()
            assertNavigatedToError()
        }

    // endregion
    // region requestFlexInputIntoZec — validation + happy (no proposal, no account branch)

    @Test
    fun flexRejectsAmountMismatch() =
        runBlocking {
            useCase(flexQuote(amountInFormatted = BigDecimal("999"))).flex()
            assertNavigatedToError()
        }

    @Test
    fun flexRejectsUnsupportedSelectedOrigin() =
        runBlocking {
            useCase(flexQuote(originAsset = sol)).flex(selectedAsset = sol)
            assertNavigatedToError()
        }

    @Test
    fun flexRejectsReturnedOriginMismatch() =
        runBlocking {
            useCase(flexQuote(originAsset = btcOnEth)).flex(selectedAsset = btc)
            assertNavigatedToError()
        }

    @Test
    fun flexRejectsNonZecDestination() =
        runBlocking {
            useCase(flexQuote(originAsset = btc, destinationAsset = eth)).flex(selectedAsset = btc)
            assertNavigatedToError()
        }

    @Test
    fun flexRejectsRefundAddressMismatch() =
        runBlocking {
            useCase(flexQuote(refundAddress = "wrong")).flex()
            assertNavigatedToError()
        }

    @Test
    fun flexRejectsDestinationAddressMismatch() =
        runBlocking {
            useCase(flexQuote(destinationAddress = "wrong")).flex()
            assertNavigatedToError()
        }

    @Test
    fun flexForwardsWithoutCreatingAProposal() =
        runBlocking {
            useCase(flexQuote()).flex()
            coVerify(exactly = 0) { zashiProposalRepository.createExactInputSwapProposal(any(), any()) }
            coVerify(exactly = 0) { keystoneProposalRepository.createExactInputSwapProposal(any(), any()) }
            assertForwardedToQuote()
        }

    // endregion
    // region helpers

    private fun assertForwardedToQuote() {
        verify { navigationRouter.forward(SwapQuoteArgs) }
        verify(exactly = 0) { navigateToError(any(), any()) }
    }

    private fun assertNavigatedToError() {
        verify { navigateToError(any(), any()) }
        verify(exactly = 0) { navigationRouter.forward(SwapQuoteArgs) }
    }

    private fun assertNavigatedToInsufficientFunds() {
        verify { navigationRouter.forward(InsufficientFundsArgs) }
        verify(exactly = 0) { navigationRouter.forward(SwapQuoteArgs) }
    }

    private fun assertNavigatedToTexUnsupported() {
        verify { navigationRouter.forward(TEXUnsupportedArgs) }
    }

    private fun zashi(): WalletAccount = mockk<ZashiAccount>()

    private fun keystone(): WalletAccount = mockk<KeystoneAccount>()

    private suspend fun RequestSwapQuoteUseCase.exactInput(selectedAsset: SwapAsset = btc) =
        requestExactInput(
            amount = BigDecimal("1"),
            address = "destination",
            selectedAsset = selectedAsset,
            slippage = BigDecimal("2"),
            canNavigateToSwapQuote = { true }
        )

    private suspend fun RequestSwapQuoteUseCase.exactOutput(selectedAsset: SwapAsset = btc) =
        requestExactOutput(
            amount = BigDecimal("1"),
            address = "destination",
            selectedAsset = selectedAsset,
            slippage = BigDecimal("2"),
            canNavigateToSwapQuote = { true }
        )

    private suspend fun RequestSwapQuoteUseCase.flex(selectedAsset: SwapAsset = btc) =
        requestFlexInputIntoZec(
            amount = BigDecimal("1"),
            refundAddress = "refund",
            selectedAsset = selectedAsset,
            slippage = BigDecimal("2"),
            canNavigateToSwapQuote = { true }
        )

    private suspend fun useCase(
        swapQuote: SwapQuote,
        selectedAccount: WalletAccount = mockk<ZashiAccount>(),
        supportedData: List<SwapAsset> = listOf(btc)
    ): RequestSwapQuoteUseCase {
        val swapRepository = mockk<SwapRepository>(relaxed = true)
        every { swapRepository.assets } returns MutableStateFlow(SwapAssetsData(data = supportedData, zecAsset = zec))
        every { swapRepository.quote } returns MutableStateFlow(SwapQuoteData.Success(swapQuote))

        val shieldedAddress = WalletAddress.Unified.new("deposit")
        val synchronizer = mockk<Synchronizer> { coEvery { validateAddress(any()) } returns AddressType.Unified }
        val synchronizerProvider = mockk<SynchronizerProvider> { coEvery { getSynchronizer() } returns synchronizer }
        val accountDataSource =
            mockk<AccountDataSource> {
                coEvery { requestNextShieldedAddress() } returns shieldedAddress
                coEvery { getSelectedAccount() } returns selectedAccount
            }
        return RequestSwapQuoteUseCase(
            navigationRouter = navigationRouter,
            navigateToErrorUseCase = navigateToError,
            swapRepository = swapRepository,
            zashiProposalRepository = zashiProposalRepository,
            keystoneProposalRepository = keystoneProposalRepository,
            accountDataSource = accountDataSource,
            synchronizerProvider = synchronizerProvider
        )
    }

    /** A valid exact-input quote (ZEC -> btc) unless overridden to trip a specific check. */
    @Suppress("LongParameterList")
    private fun swapQuote(
        originAsset: SwapAsset,
        destinationAsset: SwapAsset,
        amountInFormatted: BigDecimal = BigDecimal("1"),
        amountOutFormatted: BigDecimal = BigDecimal("1"),
        amountIn: BigDecimal = BigDecimal("100"),
        mode: SwapMode = SwapMode.EXACT_INPUT,
        depositAddress: String = "deposit",
        destinationAddress: String = "destination",
        refundAddress: String = "deposit"
    ): SwapQuote =
        // A hand-written fake, not a mock: mockk hangs when a stubbed member returns a @JvmInline
        // value class, and every SwapAddress implementation (DynamicSwapAddress, ...) is one.
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

    /** A valid exact-output quote (ZEC -> btc, EXACT_OUTPUT mode). */
    private fun exactOutputQuote(
        originAsset: SwapAsset = zec,
        destinationAsset: SwapAsset = btc,
        amountOutFormatted: BigDecimal = BigDecimal("1"),
        amountIn: BigDecimal = BigDecimal("100"),
        destinationAddress: String = "destination",
        refundAddress: String = "deposit"
    ): SwapQuote =
        swapQuote(
            originAsset = originAsset,
            destinationAsset = destinationAsset,
            amountOutFormatted = amountOutFormatted,
            amountIn = amountIn,
            mode = SwapMode.EXACT_OUTPUT,
            destinationAddress = destinationAddress,
            refundAddress = refundAddress
        )

    /** A valid flex-input quote (btc -> ZEC): selected asset is the origin, ZEC is the destination. */
    private fun flexQuote(
        originAsset: SwapAsset = btc,
        destinationAsset: SwapAsset = zec,
        amountInFormatted: BigDecimal = BigDecimal("1"),
        refundAddress: String = "refund",
        destinationAddress: String = "deposit"
    ): SwapQuote =
        swapQuote(
            originAsset = originAsset,
            destinationAsset = destinationAsset,
            amountInFormatted = amountInFormatted,
            mode = SwapMode.FLEX_INPUT,
            refundAddress = refundAddress,
            destinationAddress = destinationAddress
        )

    private class TestException : Exception()

    /**
     * Hand-written [SwapQuote] double. Cannot be a mockk: stubbing a member that returns a
     * `@JvmInline value class` (every [SwapAddress] impl is one) hangs mockk. Constructing the value
     * classes directly here is fine.
     */
    @Suppress("LongParameterList")
    private class FakeSwapQuote(
        override val originAsset: SwapAsset,
        override val destinationAsset: SwapAsset,
        override val mode: SwapMode,
        override val amountIn: BigDecimal,
        override val amountInFormatted: BigDecimal,
        override val amountOutFormatted: BigDecimal,
        depositAddress: String,
        destinationAddress: String,
        refundAddress: String
    ) : SwapQuote {
        override val depositAddress: SwapAddress = DynamicSwapAddress(depositAddress)
        override val destinationAddress: SwapAddress = DynamicSwapAddress(destinationAddress)
        override val refundAddress: SwapAddress = DynamicSwapAddress(refundAddress)
        override val provider: String = "test"
        override val zecExchangeRate: BigDecimal = BigDecimal.ONE
        override val amountInUsd: BigDecimal = BigDecimal.ONE
        override val amountOut: BigDecimal = BigDecimal.ONE
        override val amountOutUsd: BigDecimal = BigDecimal.ONE
        override val affiliateFee: BigDecimal = BigDecimal.ZERO
        override val affiliateFeeZatoshi: Zatoshi = Zatoshi(0)
        override val affiliateFeeUsd: BigDecimal = BigDecimal.ZERO
        override val timestamp: Instant = Instant.fromEpochMilliseconds(0)
        override val deadline: Instant = Instant.fromEpochMilliseconds(0)
        override val slippage: BigDecimal = BigDecimal("2")

        override fun getTotal(proposal: Proposal?): BigDecimal = BigDecimal.ZERO

        override fun getTotalUsd(proposal: Proposal?): BigDecimal = BigDecimal.ZERO

        override fun getTotalFeesUsd(proposal: Proposal?): BigDecimal = BigDecimal.ZERO

        override fun getTotalFeesZatoshi(proposal: Proposal?): Zatoshi = Zatoshi(0)
    }

    // endregion
}
