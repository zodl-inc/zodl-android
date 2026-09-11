package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.provider.IsSwapRefundWarningDismissedStorageProvider
import co.electriccoin.zcash.ui.screen.swap.refundwarning.SwapRefundWarningArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [NavigateToSwapRefundWarningUseCase] lets an amount at or above the $300 threshold - and any
 * amount once the warning has been dismissed for good on that surface - through without a sheet,
 * shows the sheet for everything below (including an amount the app cannot price), and resolves the
 * caller from the sheet's own Continue/Cancel while ignoring results from a different request. It
 * owns the suppression flag - written only for a Continue that reaches an awaiting caller - and
 * refuses to push a second sheet while one is still pending.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigateToSwapRefundWarningUseCaseTest {
    private val forwarded = mutableListOf<Any>()
    private val router =
        mockk<NavigationRouter>(relaxed = true) {
            every { forward(*anyVararg()) } answers {
                (args[0] as Array<*>).filterNotNull().forEach { forwarded.add(it) }
            }
        }

    @Test
    fun amountAtOrAboveThresholdSkipsTheWarning() =
        runTest(UnconfinedTestDispatcher()) {
            val useCase = useCase()

            assertTrue(useCase(BigDecimal("300"), SwapMode.EXACT_INPUT))
            assertTrue(useCase(BigDecimal("300.01"), SwapMode.EXACT_INPUT))

            assertTrue(forwarded.isEmpty())
        }

    @Test
    fun dismissedWarningSkipsTheWarning() =
        runTest(UnconfinedTestDispatcher()) {
            val useCase = useCase(FakeIsSwapRefundWarningDismissedStorageProvider(setOf(SwapMode.FLEX_INPUT)))

            assertTrue(useCase(BigDecimal("299.99"), SwapMode.FLEX_INPUT))

            assertTrue(forwarded.isEmpty())
        }

    @Test
    fun suppressionDoesNotLeakAcrossSurfaces() =
        runTest(UnconfinedTestDispatcher()) {
            val useCase = useCase(FakeIsSwapRefundWarningDismissedStorageProvider(setOf(SwapMode.EXACT_OUTPUT)))

            val result = async { useCase(BigDecimal("100"), SwapMode.FLEX_INPUT) }
            val args = forwarded.single() as SwapRefundWarningArgs

            assertEquals(SwapMode.FLEX_INPUT, args.mode)

            useCase.onContinue(args, false)
            assertTrue(result.await())
        }

    @Test
    fun amountBelowThresholdForwardsTheWarningForTheGivenMode() =
        runTest(UnconfinedTestDispatcher()) {
            val useCase = useCase()

            val result = async { useCase(BigDecimal("299.99"), SwapMode.EXACT_OUTPUT) }
            val args = forwarded.single() as SwapRefundWarningArgs

            assertEquals(SwapMode.EXACT_OUTPUT, args.mode)

            useCase.onContinue(args, false)
            assertTrue(result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun unknownFiatAmountFailsSafeAndForwardsTheWarning() =
        runTest(UnconfinedTestDispatcher()) {
            val useCase = useCase()

            val result = async { useCase(null, SwapMode.EXACT_INPUT) }
            val args = forwarded.single() as SwapRefundWarningArgs

            useCase.onCancel(args)
            assertFalse(result.await())
            verify(exactly = 1) { router.back() }
        }

    @Test
    fun ignoresResultFromADifferentRequest() =
        runTest(UnconfinedTestDispatcher()) {
            val useCase = useCase()

            val result = async { useCase(BigDecimal("10"), SwapMode.FLEX_INPUT) }
            val args = forwarded.single() as SwapRefundWarningArgs

            useCase.onContinue(args.copy(requestId = "stale"), false)
            assertTrue(result.isActive)

            useCase.onContinue(args, false)
            assertTrue(result.await())
        }

    @Test
    fun continueWithDontShowAgainPersistsTheFlagForThatModeOnly() =
        runTest(UnconfinedTestDispatcher()) {
            val dismissed = FakeIsSwapRefundWarningDismissedStorageProvider()
            val useCase = useCase(dismissed)

            val result = async { useCase(BigDecimal("10"), SwapMode.FLEX_INPUT) }
            val args = forwarded.single() as SwapRefundWarningArgs

            useCase.onContinue(args, true)

            assertTrue(result.await())
            assertEquals(mapOf(SwapMode.FLEX_INPUT to true), dismissed.stored)
        }

    @Test
    fun continueWithoutDontShowAgainPersistsNothing() =
        runTest(UnconfinedTestDispatcher()) {
            val dismissed = FakeIsSwapRefundWarningDismissedStorageProvider()
            val useCase = useCase(dismissed)

            val result = async { useCase(BigDecimal("10"), SwapMode.FLEX_INPUT) }
            val args = forwarded.single() as SwapRefundWarningArgs

            useCase.onContinue(args, false)

            assertTrue(result.await())
            assertTrue(dismissed.stored.isEmpty())
        }

    @Test
    fun cancelPersistsNothing() =
        runTest(UnconfinedTestDispatcher()) {
            val dismissed = FakeIsSwapRefundWarningDismissedStorageProvider()
            val useCase = useCase(dismissed)

            val result = async { useCase(BigDecimal("10"), SwapMode.EXACT_OUTPUT) }
            val args = forwarded.single() as SwapRefundWarningArgs

            useCase.onCancel(args)

            assertFalse(result.await())
            assertTrue(dismissed.stored.isEmpty())
        }

    @Test
    fun secondInvokeWhileTheSheetIsPendingResolvesFalseWithoutForwarding() =
        runTest(UnconfinedTestDispatcher()) {
            val useCase = useCase()

            val result = async { useCase(BigDecimal("10"), SwapMode.EXACT_INPUT) }
            val args = forwarded.single() as SwapRefundWarningArgs

            assertFalse(useCase(BigDecimal("10"), SwapMode.EXACT_INPUT))
            assertEquals(1, forwarded.size)

            useCase.onContinue(args, false)
            assertTrue(result.await())
        }

    private fun useCase(
        isSwapRefundWarningDismissed: FakeIsSwapRefundWarningDismissedStorageProvider =
            FakeIsSwapRefundWarningDismissedStorageProvider()
    ) = NavigateToSwapRefundWarningUseCase(
        navigationRouter = router,
        isSwapRefundWarningDismissed = isSwapRefundWarningDismissed
    )
}

private class FakeIsSwapRefundWarningDismissedStorageProvider(
    dismissedModes: Set<SwapMode> = emptySet()
) : IsSwapRefundWarningDismissedStorageProvider {
    val stored = dismissedModes.associateWith { true }.toMutableMap()

    override suspend fun get(mode: SwapMode): Boolean = stored[mode] ?: false

    override suspend fun store(mode: SwapMode, value: Boolean) {
        stored[mode] = value
    }
}
