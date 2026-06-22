package co.electriccoin.zcash.ui.screen.exchangerateunavailable

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.IsExchangeRateEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.PreferredFiatProvider
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.OptInExchangeRateUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateError
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByFiatDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The exchange-rate-unavailable sheet (MOB-1124): its copy names the currency that failed,
 * "Switch to USD" opts in with USD as the preferred fiat, and "Continue in ZEC"/back simply dismiss
 * without changing anything.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeRateUnavailableVMTest {
    @Test
    fun titleAndSubtitleNameTheFailedCurrency() {
        val vm = vm(expectedCurrency = FiatCurrency("JPY"))

        val state = vm.state.value

        // Title uses the currency code, subtitle uses the localized display name.
        assertEquals(stringRes(R.string.send_currencyUnavailable_title, "JPY"), state.title)
        assertEquals(
            stringRes(
                R.string.send_currencyUnavailable_desc,
                stringResByFiatDisplayName(FiatCurrency("JPY"))
            ),
            state.subtitle
        )
    }

    @Test
    fun fallsBackToUsdCopyWhenStateIsNotData() {
        val vm = vm(repositoryState = ExchangeRateState.OptIn)

        assertEquals(stringRes(R.string.send_currencyUnavailable_title, "USD"), vm.state.value.title)
    }

    @Test
    fun switchToUsdOptsInWithUsd() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val router = FakeNavigationRouter()
                val optInProvider = FakeIsExchangeRateEnabledStorageProvider()
                val fiatProvider = FakePreferredFiatProvider()
                val vm =
                    vm(
                        router = router,
                        optInProvider = optInProvider,
                        fiatProvider = fiatProvider
                    )

                vm.state.value.switchToUsdButton
                    .onClick()
                advanceUntilIdle()

                assertEquals(FiatCurrency.USD, fiatProvider.stored)
                assertEquals(true, optInProvider.stored)
                // OptInExchangeRateUseCase navigates back, and the VM also dismisses → two pops.
                assertEquals(2, router.backCount)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun continueInZecDismissesWithoutChangingAnything() {
        val router = FakeNavigationRouter()
        val optInProvider = FakeIsExchangeRateEnabledStorageProvider()
        val fiatProvider = FakePreferredFiatProvider()
        val vm = vm(router = router, optInProvider = optInProvider, fiatProvider = fiatProvider)

        vm.state.value.continueInZecButton
            .onClick()

        assertEquals(1, router.backCount)
        assertNull(fiatProvider.stored)
        assertNull(optInProvider.stored)
    }

    @Test
    fun backDismissesWithoutChangingAnything() {
        val router = FakeNavigationRouter()
        val optInProvider = FakeIsExchangeRateEnabledStorageProvider()
        val fiatProvider = FakePreferredFiatProvider()
        val vm = vm(router = router, optInProvider = optInProvider, fiatProvider = fiatProvider)

        vm.state.value.onBack()

        assertEquals(1, router.backCount)
        assertNull(fiatProvider.stored)
        assertNull(optInProvider.stored)
    }

    private fun vm(
        expectedCurrency: FiatCurrency = FiatCurrency("JPY"),
        repositoryState: ExchangeRateState = dataState(expectedCurrency),
        router: FakeNavigationRouter = FakeNavigationRouter(),
        optInProvider: FakeIsExchangeRateEnabledStorageProvider = FakeIsExchangeRateEnabledStorageProvider(),
        fiatProvider: FakePreferredFiatProvider = FakePreferredFiatProvider(),
    ) = ExchangeRateUnavailableVM(
        navigationRouter = router,
        optInExchangeRate = OptInExchangeRateUseCase(router, optInProvider, fiatProvider),
        exchangeRateRepository = FakeExchangeRateRepository(repositoryState)
    )
}

private fun dataState(expectedCurrency: FiatCurrency) =
    ExchangeRateState.Data(
        error = ExchangeRateError.CMC_SERVER_CONNECTION_ERROR,
        expectedCurrency = expectedCurrency,
        onRefresh = {}
    )

private class FakeExchangeRateRepository(
    state: ExchangeRateState
) : ExchangeRateRepository {
    override val state: StateFlow<ExchangeRateState> = MutableStateFlow(state).asStateFlow()

    override fun refreshExchangeRateUsd() = Unit
}

private class FakeIsExchangeRateEnabledStorageProvider : IsExchangeRateEnabledStorageProvider {
    var stored: Boolean? = null
        private set

    override suspend fun get(): Boolean? = stored

    override suspend fun store(amount: Boolean) {
        stored = amount
    }

    override fun observe(): Flow<Boolean?> = emptyFlow()

    override suspend fun clear() {
        stored = null
    }
}

private class FakePreferredFiatProvider : PreferredFiatProvider {
    var stored: FiatCurrency? = null
        private set

    override suspend fun get(): FiatCurrency? = stored

    override suspend fun store(amount: FiatCurrency) {
        stored = amount
    }

    override fun observe(): Flow<FiatCurrency?> = emptyFlow()

    override suspend fun clear() {
        stored = null
    }
}

private class FakeNavigationRouter : NavigationRouter {
    var backCount = 0
        private set

    override fun forward(vararg routes: Any) = Unit

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
