package co.electriccoin.zcash.ui.common.usecase

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.exchangerate.picker.CurrencyConversionPickerArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The currency picker returns its selection to the caller without persisting (MOB-1124): a pick
 * resolves to that currency, a dismissal resolves to null, and the picker is opened with the
 * caller's current selection. Uses [UnconfinedTestDispatcher] so the suspended [invoke] reaches the
 * rendezvous before the result is emitted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigateToSelectFiatCurrencyUseCaseTest {
    @Test
    fun selectionResolvesToPickedCurrencyAndForwardsCurrentSelection() =
        runTest(UnconfinedTestDispatcher()) {
            val router = FakeSelectFiatNavigationRouter()
            val useCase = NavigateToSelectFiatCurrencyUseCase(router)

            val result = async { useCase(FiatCurrency.USD) }

            val args = router.forwardedRoutes.single() as CurrencyConversionPickerArgs
            assertEquals("USD", args.selectedCode)

            useCase.onSelected(FiatCurrency("EUR"), args)

            assertEquals(FiatCurrency("EUR"), result.await())
            assertEquals(1, router.backCount)
        }

    @Test
    fun cancellationResolvesToNull() =
        runTest(UnconfinedTestDispatcher()) {
            val router = FakeSelectFiatNavigationRouter()
            val useCase = NavigateToSelectFiatCurrencyUseCase(router)

            val result = async { useCase(FiatCurrency("EUR")) }

            val args = router.forwardedRoutes.single() as CurrencyConversionPickerArgs
            assertEquals("EUR", args.selectedCode)

            useCase.onSelectionCancelled(args)

            assertNull(result.await())
            assertEquals(1, router.backCount)
        }

    @Test
    fun defaultsToUsdWhenThereIsNoCurrentSelection() =
        runTest(UnconfinedTestDispatcher()) {
            val router = FakeSelectFiatNavigationRouter()
            val useCase = NavigateToSelectFiatCurrencyUseCase(router)

            // A missing preference must still open the picker with USD highlighted, not nothing.
            val result = async { useCase(null) }

            val args = router.forwardedRoutes.single() as CurrencyConversionPickerArgs
            assertEquals("USD", args.selectedCode)

            // invoke() stays suspended waiting for a pick; cancel it so the test body can finish.
            result.cancel()
        }
}

private class FakeSelectFiatNavigationRouter : NavigationRouter {
    val forwardedRoutes = mutableListOf<Any>()
    var backCount = 0
        private set

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) = Unit

    override fun replaceAll(vararg routes: Any) = Unit

    override fun back() {
        backCount++
    }

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
