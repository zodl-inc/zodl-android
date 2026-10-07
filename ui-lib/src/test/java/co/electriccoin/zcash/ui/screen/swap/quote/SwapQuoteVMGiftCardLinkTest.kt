package co.electriccoin.zcash.ui.screen.swap.quote

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.GiftCardAddressNotAllowedException
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A quote rejected because a gift card link was given as an address shows the dedicated copy, for swaps and payments
 * alike, never a server message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapQuoteVMGiftCardLinkTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun giftCardLinkRejectionShowsDedicatedError() =
        runTest(UnconfinedTestDispatcher()) {
            SwapMode.entries.forEach { mode ->
                val vm = createVM(SwapQuoteData.Error(mode, GiftCardAddressNotAllowedException()))
                val job = launch { vm.state.collect {} }

                val state = assertIs<SwapQuoteState.Error>(vm.state.value)
                assertEquals(stringRes(R.string.swap_error_giftCardLink), state.subtitle)

                job.cancel()
            }
        }

    private fun createVM(quote: SwapQuoteData): SwapQuoteVM {
        val swapRepository =
            mockk<SwapRepository>(relaxed = true) {
                every { this@mockk.quote } returns MutableStateFlow(quote)
            }
        return SwapQuoteVM(
            observeProposal = mockk<ObserveProposalUseCase> { every { observeNullable() } returns flowOf(null) },
            applicationStateProvider =
                mockk<ApplicationStateProvider> { every { observeOnForeground() } returns emptyFlow() },
            swapRepository = swapRepository,
            cancelSwapQuote = mockk(relaxed = true),
            cancelSwap = mockk(relaxed = true),
            swapQuoteSuccessMapper = mockk(relaxed = true),
            submitProposal = mockk(relaxed = true),
            navigationRouter = mockk(relaxed = true),
        )
    }
}
