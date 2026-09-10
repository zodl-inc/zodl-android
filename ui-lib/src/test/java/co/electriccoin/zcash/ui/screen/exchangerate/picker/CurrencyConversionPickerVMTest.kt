package co.electriccoin.zcash.ui.screen.exchangerate.picker

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.FiatCurrenciesData
import co.electriccoin.zcash.ui.common.repository.FiatCurrencyRepository
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectFiatCurrencyUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The currency picker asks the repository for the provider's currency list and highlights exactly
 * the caller's current selection (MOB-1124, MOB-1707). Loading and error states follow the list's
 * fetch, and "Try again" retries it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyConversionPickerVMTest {
    private val testDispatcher = StandardTestDispatcher()

    private val usd = FiatCurrency("USD")
    private val eur = FiatCurrency("EUR")
    private val jpy = FiatCurrency("JPY")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun requestsTheListOnceAndStartsLoading() =
        runTest(testDispatcher) {
            val repository = FakeFiatCurrencyRepository()

            val vm = pickerVM(repository, selectedCode = "USD")

            assertEquals(1, repository.ensureLoadedCalls)
            assertIs<CurrencyConversionPickerDataState.Loading>(vm.state.value.data)
        }

    @Test
    fun exposesTheProvidedCurrenciesInOrder() =
        runTest(testDispatcher) {
            val repository = FakeFiatCurrencyRepository()
            repository.emit(FiatCurrenciesData(data = listOf(usd, eur, jpy)))
            val vm = pickerVM(repository, selectedCode = "EUR")
            collectState(vm)

            val data = assertIs<CurrencyConversionPickerDataState.Success>(vm.state.value.data)
            assertEquals(listOf("USD", "EUR", "JPY"), data.items.map { it.key })
            assertEquals(listOf("EUR"), data.items.filter { it.isSelected }.map { it.key })
        }

    @Test
    fun highlightsNothingWhenTheSelectionIsNotOffered() =
        runTest(testDispatcher) {
            val repository = FakeFiatCurrencyRepository()
            repository.emit(FiatCurrenciesData(data = listOf(usd, eur)))
            val vm = pickerVM(repository, selectedCode = "JPY")
            collectState(vm)

            val data = assertIs<CurrencyConversionPickerDataState.Success>(vm.state.value.data)
            assertTrue(data.items.none { it.isSelected })
        }

    @Test
    fun errorRetryRequestsARefresh() =
        runTest(testDispatcher) {
            val repository = FakeFiatCurrencyRepository()
            val vm = pickerVM(repository, selectedCode = "USD")
            collectState(vm)

            repository.emit(FiatCurrenciesData(error = RuntimeException("boom")))
            advanceUntilIdle()

            val data = assertIs<CurrencyConversionPickerDataState.Error>(vm.state.value.data)
            data.buttonState.onClick()

            assertEquals(1, repository.requestRefreshCalls)
        }

    @Test
    fun retryingShowsLoadingAgain() =
        runTest(testDispatcher) {
            val repository = FakeFiatCurrencyRepository()
            val vm = pickerVM(repository, selectedCode = "USD")
            collectState(vm)

            repository.emit(FiatCurrenciesData(error = RuntimeException("boom")))
            advanceUntilIdle()
            assertIs<CurrencyConversionPickerDataState.Error>(vm.state.value.data)

            repository.emit(FiatCurrenciesData(isLoading = true, error = null))
            advanceUntilIdle()

            assertIs<CurrencyConversionPickerDataState.Loading>(vm.state.value.data)
        }

    /** Keeps `WhileSubscribed` active for the remainder of the test. */
    private fun TestScope.collectState(vm: CurrencyConversionPickerVM) {
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
    }

    private fun pickerVM(repository: FiatCurrencyRepository, selectedCode: String) =
        CurrencyConversionPickerVM(
            args = CurrencyConversionPickerArgs(selectedCode = selectedCode),
            navigateToSelectFiatCurrency = NavigateToSelectFiatCurrencyUseCase(NoopNavigationRouter()),
            fiatCurrencyRepository = repository
        )
}

private class FakeFiatCurrencyRepository : FiatCurrencyRepository {
    private val currenciesFlow = MutableStateFlow(FiatCurrenciesData())

    override val currencies: StateFlow<FiatCurrenciesData> = currenciesFlow.asStateFlow()

    var ensureLoadedCalls = 0
        private set

    var requestRefreshCalls = 0
        private set

    fun emit(data: FiatCurrenciesData) = currenciesFlow.update { data }

    override fun ensureLoaded() {
        ensureLoadedCalls++
    }

    override fun requestRefresh() {
        requestRefreshCalls++
    }
}

private class NoopNavigationRouter : NavigationRouter {
    override fun forward(vararg routes: Any) = Unit

    override fun replace(vararg routes: Any) = Unit

    override fun replaceAll(vararg routes: Any) = Unit

    override fun back() = Unit

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
