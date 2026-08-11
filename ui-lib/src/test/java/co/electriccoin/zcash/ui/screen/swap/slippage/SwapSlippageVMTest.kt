package co.electriccoin.zcash.ui.screen.swap.slippage

import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.usecase.NavigateToSlippageUseCase
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [SwapSlippageVM] seeds its picker from the slippage passed in args, warns when the selection is
 * below the default, and routes confirm/back through [NavigateToSlippageUseCase] instead of the
 * repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapSlippageVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun seedsPickerFromArgsCurrentSlippage() {
        val vm = vm(currentSlippage = "5")

        assertEquals(BigDecimal("5"), vm.state.value.picker.amount)
    }

    @Test
    fun warnsWhenSelectionBelowDefault() =
        runTest {
            // The warning sub-flow starts at null and only computes once state is collected.
            val below = vm(currentSlippage = "1")
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { below.state.collect {} }
            assertNotNull(below.state.value.warning)

            val atDefault = vm(currentSlippage = "2")
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { atDefault.state.collect {} }
            assertNull(atDefault.state.value.warning)
        }

    @Test
    fun confirmRoutesSelectionThroughUseCase() =
        runTest {
            val navigateToSlippage = mockk<NavigateToSlippageUseCase>(relaxed = true)
            val args = args(currentSlippage = "5")
            val vm = SwapSlippageVM(args = args, navigateToSlippage = navigateToSlippage)

            vm.state.value.primary
                .onClick()

            coVerify(exactly = 1) { navigateToSlippage.onSelected(BigDecimal("5"), args) }
        }

    @Test
    fun backRoutesCancellationThroughUseCase() =
        runTest {
            val navigateToSlippage = mockk<NavigateToSlippageUseCase>(relaxed = true)
            val args = args(currentSlippage = "5")
            val vm = SwapSlippageVM(args = args, navigateToSlippage = navigateToSlippage)

            vm.state.value.onBack()

            coVerify(exactly = 1) { navigateToSlippage.onSelectionCancelled(args) }
        }

    private fun args(currentSlippage: String) =
        SwapSlippageArgs(currentSlippage = currentSlippage, fiatAmount = null, mode = SwapMode.EXACT_INPUT)

    private fun vm(currentSlippage: String) =
        SwapSlippageVM(
            args = args(currentSlippage),
            navigateToSlippage = mockk(relaxed = true)
        )
}
