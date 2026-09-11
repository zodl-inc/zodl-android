package co.electriccoin.zcash.ui.screen.swap.refundwarning

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapRefundWarningUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SwapRefundWarningVM] words the warning per mode and hands the checkbox state to the use case
 * along with the user's Continue - the sheet itself neither reads nor writes the suppression flag.
 * Backing out resolves the pipeline as a cancel, never as a confirmation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapRefundWarningVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun checkboxStartsUncheckedAndToggles() =
        runTest {
            val vm = vm()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

            assertFalse(vm.state.value.checkbox.isChecked)

            vm.state.value.checkbox
                .onClick()

            assertTrue(vm.state.value.checkbox.isChecked)
        }

    @Test
    fun swapModesUseTheSwapWording() =
        runTest {
            val swapMessage = stringRes(R.string.swap_refund_warning_message_swap)

            assertEquals(swapMessage, vm(args = args(SwapMode.EXACT_INPUT)).state.value.message)
            assertEquals(swapMessage, vm(args = args(SwapMode.FLEX_INPUT)).state.value.message)
        }

    @Test
    fun exactOutputUsesThePaymentWording() =
        runTest {
            assertEquals(
                stringRes(R.string.swap_refund_warning_message_pay),
                vm(args = args(SwapMode.EXACT_OUTPUT)).state.value.message
            )
        }

    @Test
    fun continueWithoutTheCheckboxDoesNotAskToSuppressTheWarning() =
        runTest {
            val navigateToSwapRefundWarning = mockk<NavigateToSwapRefundWarningUseCase>(relaxed = true)
            val args = args()
            val vm = vm(args = args, navigateToSwapRefundWarning = navigateToSwapRefundWarning)

            vm.state.value.continueButton
                .onClick()

            coVerify(exactly = 1) { navigateToSwapRefundWarning.onContinue(args, false) }
        }

    @Test
    fun continueWithTheCheckboxAsksToSuppressTheWarning() =
        runTest {
            val navigateToSwapRefundWarning = mockk<NavigateToSwapRefundWarningUseCase>(relaxed = true)
            val args = args(SwapMode.EXACT_OUTPUT)
            val vm = vm(args = args, navigateToSwapRefundWarning = navigateToSwapRefundWarning)

            vm.state.value.checkbox
                .onClick()
            vm.state.value.continueButton
                .onClick()

            coVerify(exactly = 1) { navigateToSwapRefundWarning.onContinue(args, true) }
        }

    @Test
    fun cancelResolvesAsACancelEvenWithTheCheckboxTicked() =
        runTest {
            val navigateToSwapRefundWarning = mockk<NavigateToSwapRefundWarningUseCase>(relaxed = true)
            val args = args()
            val vm = vm(args = args, navigateToSwapRefundWarning = navigateToSwapRefundWarning)

            vm.state.value.checkbox
                .onClick()
            vm.state.value.cancelButton
                .onClick()

            coVerify(exactly = 1) { navigateToSwapRefundWarning.onCancel(args) }
            coVerify(exactly = 0) { navigateToSwapRefundWarning.onContinue(any(), any()) }
        }

    @Test
    fun backCancels() =
        runTest {
            val navigateToSwapRefundWarning = mockk<NavigateToSwapRefundWarningUseCase>(relaxed = true)
            val args = args()
            val vm = vm(args = args, navigateToSwapRefundWarning = navigateToSwapRefundWarning)

            vm.state.value.onBack()

            coVerify(exactly = 1) { navigateToSwapRefundWarning.onCancel(args) }
            coVerify(exactly = 0) { navigateToSwapRefundWarning.onContinue(any(), any()) }
        }

    private fun args(mode: SwapMode = SwapMode.EXACT_INPUT) = SwapRefundWarningArgs(mode = mode)

    private fun vm(
        args: SwapRefundWarningArgs = args(),
        navigateToSwapRefundWarning: NavigateToSwapRefundWarningUseCase = mockk(relaxed = true)
    ) = SwapRefundWarningVM(
        args = args,
        navigateToSwapRefundWarning = navigateToSwapRefundWarning
    )
}
