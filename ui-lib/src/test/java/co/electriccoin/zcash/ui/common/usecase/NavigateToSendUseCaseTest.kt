package co.electriccoin.zcash.ui.common.usecase

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateError
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.screen.exchangerateunavailable.ExchangeRateUnavailableArgs
import co.electriccoin.zcash.ui.screen.send.Send
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Opening Send pushes the Send screen, and additionally the exchange-rate-unavailable sheet on top
 * only when the selected (non-USD) currency's rate is in a definitive error state (MOB-1124).
 */
class NavigateToSendUseCaseTest {
    @Test
    fun forwardsSendWithErrorSheetWhenNonUsdRateErrored() {
        val router = FakeNavigationRouter()
        val repo =
            FakeExchangeRateRepository(
                dataState(error = ExchangeRateError.CMC_SERVER_CONNECTION_ERROR, expectedCurrency = FiatCurrency("EUR"))
            )

        NavigateToSendUseCase(router, repo)()

        assertEquals(listOf(Send(), ExchangeRateUnavailableArgs), router.forwardedRoutes)
    }

    @Test
    fun forwardsSendOnlyWhenNoError() {
        val router = FakeNavigationRouter()
        val repo = FakeExchangeRateRepository(dataState(error = null, expectedCurrency = FiatCurrency("EUR")))

        NavigateToSendUseCase(router, repo)()

        assertEquals(listOf<Any>(Send()), router.forwardedRoutes)
    }

    @Test
    fun forwardsSendOnlyWhenErroredCurrencyIsUsd() {
        // USD is the fallback rate source; there's no "switch to USD" remedy to offer, so no sheet.
        val router = FakeNavigationRouter()
        val repo =
            FakeExchangeRateRepository(
                dataState(error = ExchangeRateError.CMC_SERVER_CONNECTION_ERROR, expectedCurrency = FiatCurrency.USD)
            )

        NavigateToSendUseCase(router, repo)()

        assertEquals(listOf<Any>(Send()), router.forwardedRoutes)
    }

    @Test
    fun forwardsSendOnlyWhenNotOptedIn() {
        val router = FakeNavigationRouter()
        val repo = FakeExchangeRateRepository(ExchangeRateState.OptIn)

        NavigateToSendUseCase(router, repo)()

        assertEquals(listOf<Any>(Send()), router.forwardedRoutes)
    }
}

private fun dataState(
    error: ExchangeRateError?,
    expectedCurrency: FiatCurrency,
) = ExchangeRateState.Data(error = error, expectedCurrency = expectedCurrency, onRefresh = {})

private class FakeExchangeRateRepository(
    state: ExchangeRateState
) : ExchangeRateRepository {
    override val state: StateFlow<ExchangeRateState> = MutableStateFlow(state).asStateFlow()

    override fun refreshExchangeRateUsd() = Unit
}

private class FakeNavigationRouter : NavigationRouter {
    val forwardedRoutes = mutableListOf<Any>()

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) = Unit

    override fun replaceAll(vararg routes: Any) = Unit

    override fun back() = Unit

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
