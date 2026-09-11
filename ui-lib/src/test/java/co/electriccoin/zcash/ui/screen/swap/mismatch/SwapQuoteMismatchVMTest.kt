package co.electriccoin.zcash.ui.screen.swap.mismatch

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchType
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [SwapQuoteMismatchVM] renders the generic mismatch copy (no technical detail reaches the UI), sends
 * the technical report by email from the Report button, and dismisses the sheet in both cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapQuoteMismatchVMTest {
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)
    private val sendEmailUseCase = mockk<SendEmailUseCase>(relaxed = true)

    private val args =
        SwapQuoteMismatchArgs(
            provider = "near",
            mode = SwapMode.EXACT_OUTPUT,
            originTokenTicker = "zec",
            originChainTicker = "zec",
            destinationTokenTicker = "usdc",
            destinationChainTicker = "arb",
            mismatchType = SwapQuoteMismatchType.OUTPUT_AMOUNT,
            depositAddress = "deposit-address"
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun rendersTheGenericMismatchCopy() {
        val state = vm().state.value

        assertNotNull(state)
        assertEquals(stringRes(R.string.swap_mismatch_title), state.title)
        assertEquals(
            listOf(
                stringRes(R.string.swap_mismatch_msg_1),
                stringRes(R.string.swap_mismatch_msg_2),
                stringRes(R.string.swap_mismatch_msg_3)
            ),
            state.paragraphs
        )
        assertEquals(stringRes(R.string.swap_mismatch_goBack), state.goBackButton.text)
        assertEquals(stringRes(R.string.send_report), state.reportButton.text)
    }

    @Test
    fun goBackDismissesTheSheet() {
        vm()
            .state.value!!
            .goBackButton
            .onClick()

        verify(exactly = 1) { navigationRouter.back() }
        coVerify(exactly = 0) { sendEmailUseCase(ofType<SwapQuoteMismatchArgs>()) }
    }

    @Test
    fun onBackDismissesTheSheet() {
        vm().state.value!!.onBack()

        verify(exactly = 1) { navigationRouter.back() }
    }

    @Test
    fun reportSendsTheTechnicalDetailByEmailAndDismissesTheSheet() =
        runTest {
            vm()
                .state.value!!
                .reportButton
                .onClick()

            coVerify(exactly = 1) { sendEmailUseCase(args) }
            verify(exactly = 1) { navigationRouter.back() }
        }

    private fun vm() =
        SwapQuoteMismatchVM(
            args = args,
            navigationRouter = navigationRouter,
            sendEmailUseCase = sendEmailUseCase
        )
}
