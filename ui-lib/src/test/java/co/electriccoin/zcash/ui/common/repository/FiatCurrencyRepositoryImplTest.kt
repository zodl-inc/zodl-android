package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.common.datasource.FiatCurrencyDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [FiatCurrencyRepositoryImpl] caches the supported-currency list for the process lifetime and only
 * refetches on an explicit retry (MOB-1707). The repository's coroutine scope is injected with an
 * [UnconfinedTestDispatcher] so the fire-and-forget fetch runs eagerly and deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FiatCurrencyRepositoryImplTest {
    private val testScope = CoroutineScope(UnconfinedTestDispatcher())

    private val usd = FiatCurrency("USD")
    private val eur = FiatCurrency("EUR")

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    /** Builds the repository with its background scope swapped for the eager test scope. */
    private fun repository(dataSource: FiatCurrencyDataSource): FiatCurrencyRepositoryImpl =
        FiatCurrencyRepositoryImpl(dataSource).apply { scope = testScope }

    @Test
    fun ensureLoadedLoadsTheListOnce() =
        runTest {
            val dataSource = FakeFiatCurrencyDataSource(listOf(usd, eur))
            val repository = repository(dataSource)

            repository.ensureLoaded()
            repository.ensureLoaded()

            val data = repository.currencies.value
            assertEquals(listOf(usd, eur), data.data)
            assertFalse(data.isLoading)
            assertNull(data.error)
            assertEquals(1, dataSource.calls)
        }

    @Test
    fun requestRefreshMarksLoadingSynchronously() =
        runTest {
            val gate = CompletableDeferred<List<FiatCurrency>>()
            val dataSource = FakeFiatCurrencyDataSource(gate = gate)
            val repository = repository(dataSource)

            repository.requestRefresh()

            assertTrue(repository.currencies.value.isLoading)
            assertNull(repository.currencies.value.error)

            gate.complete(listOf(usd))
        }

    @Test
    fun concurrentRefreshesShareOneFetch() =
        runTest {
            val gate = CompletableDeferred<List<FiatCurrency>>()
            val dataSource = FakeFiatCurrencyDataSource(gate = gate)
            val repository = repository(dataSource)

            repository.requestRefresh()
            repository.requestRefresh()

            assertEquals(1, dataSource.calls)

            gate.complete(listOf(usd))

            assertEquals(listOf(usd), repository.currencies.value.data)
            assertEquals(1, dataSource.calls)
        }

    @Test
    fun failureWithoutCacheSurfacesTheError() =
        runTest {
            val failure = RuntimeException("boom")
            val dataSource = FakeFiatCurrencyDataSource(failure = failure)
            val repository = repository(dataSource)

            repository.ensureLoaded()

            val data = repository.currencies.value
            assertEquals(failure, data.error)
            assertNull(data.data)
            assertFalse(data.isLoading)
        }

    @Test
    fun ensureLoadedRefetchesAfterAFailure() =
        runTest {
            val dataSource = FakeFiatCurrencyDataSource(listOf(usd), failFirstCall = true)
            val repository = repository(dataSource)

            repository.ensureLoaded()
            repository.ensureLoaded()

            val data = repository.currencies.value
            assertEquals(listOf(usd), data.data)
            assertNull(data.error)
            assertEquals(2, dataSource.calls)
        }

    @Test
    fun failureAfterASuccessfulLoadKeepsTheCache() =
        runTest {
            val dataSource = FakeFiatCurrencyDataSource(listOf(usd), failSecondCall = true)
            val repository = repository(dataSource)

            repository.ensureLoaded()
            repository.requestRefresh()

            val data = repository.currencies.value
            assertEquals(listOf(usd), data.data)
            assertNull(data.error)
            assertFalse(data.isLoading)
        }
}

private class FakeFiatCurrencyDataSource(
    private val currencies: List<FiatCurrency> = listOf(),
    private val failure: Exception? = null,
    private val failFirstCall: Boolean = false,
    private val failSecondCall: Boolean = false,
    private val gate: CompletableDeferred<List<FiatCurrency>>? = null,
) : FiatCurrencyDataSource {
    var calls = 0
        private set

    override suspend fun getSupportedFiatCurrencies(): List<FiatCurrency> {
        val call = ++calls
        failure?.let { throw it }
        if (failFirstCall && call == 1) error("first call failed")
        if (failSecondCall && call == 2) error("second call failed")
        return gate?.await() ?: currencies
    }
}
