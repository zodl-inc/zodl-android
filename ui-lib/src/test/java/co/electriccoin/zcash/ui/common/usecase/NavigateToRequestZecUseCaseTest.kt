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
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressType
import co.electriccoin.zcash.ui.screen.request.RequestArgs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Opening the Request screen forwards [RequestArgs] for the given address type, plus the
 * exchange-rate-unavailable sheet on top only when the selected (non-USD) currency's rate is in a
 * definitive error state (MOB-1124).
 */
class NavigateToRequestZecUseCaseTest {
    @Test
    fun forwardsRequestWithErrorSheetWhenNonUsdRateErrored() {
        val router = RequestFakeNavigationRouter()
        val repo =
            RequestFakeExchangeRateRepository(
                requestDataState(
                    error = ExchangeRateError.CMC_SERVER_CONNECTION_ERROR,
                    expectedCurrency = FiatCurrency("EUR")
                )
            )

        NavigateToRequestZecUseCase(router, repo)(addressTypeOrdinal = ReceiveAddressType.Transparent.ordinal)

        assertEquals(
            listOf(RequestArgs(ReceiveAddressType.Transparent.ordinal), ExchangeRateUnavailableArgs),
            router.forwardedRoutes
        )
    }

    @Test
    fun forwardsRequestOnlyWhenNoError() {
        val router = RequestFakeNavigationRouter()
        val repo =
            RequestFakeExchangeRateRepository(requestDataState(error = null, expectedCurrency = FiatCurrency("EUR")))

        NavigateToRequestZecUseCase(router, repo)(addressTypeOrdinal = ReceiveAddressType.Sapling.ordinal)

        assertEquals(listOf<Any>(RequestArgs(ReceiveAddressType.Sapling.ordinal)), router.forwardedRoutes)
    }

    @Test
    fun defaultsToUnifiedAddressType() {
        val router = RequestFakeNavigationRouter()
        val repo =
            RequestFakeExchangeRateRepository(requestDataState(error = null, expectedCurrency = FiatCurrency.USD))

        NavigateToRequestZecUseCase(router, repo)()

        assertEquals(listOf<Any>(RequestArgs(ReceiveAddressType.Unified.ordinal)), router.forwardedRoutes)
    }
}

private fun requestDataState(
    error: ExchangeRateError?,
    expectedCurrency: FiatCurrency,
) = ExchangeRateState.Data(error = error, expectedCurrency = expectedCurrency, onRefresh = {})

private class RequestFakeExchangeRateRepository(
    state: ExchangeRateState
) : ExchangeRateRepository {
    override val state: StateFlow<ExchangeRateState> = MutableStateFlow(state).asStateFlow()

    override fun refreshExchangeRateUsd() = Unit
}

private class RequestFakeNavigationRouter : NavigationRouter {
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
