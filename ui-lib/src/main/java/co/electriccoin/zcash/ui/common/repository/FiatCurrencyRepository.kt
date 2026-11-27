package co.electriccoin.zcash.ui.common.repository

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.common.datasource.FiatCurrencyDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [data] is null until the list has been loaded successfully at least once. */
data class FiatCurrenciesData(
    val data: List<FiatCurrency>? = null,
    val isLoading: Boolean = false,
    val error: Exception? = null,
)

interface FiatCurrencyRepository {
    val currencies: StateFlow<FiatCurrenciesData>

    /** Fetches once; no-op when data is cached or a fetch is in flight. A prior failure counts as not cached. */
    fun ensureLoaded()

    /** "Try again": starts a fetch unless one is already in flight. */
    fun requestRefresh()
}

class FiatCurrencyRepositoryImpl(
    private val fiatCurrencyDataSource: FiatCurrencyDataSource
) : FiatCurrencyRepository {
    /**
     * Scope the background fetch runs on. A test seam: unit tests replace it with a test dispatcher
     * before invoking any method, so the fire-and-forget job runs deterministically.
     */
    @set:RestrictTo(RestrictTo.Scope.TESTS)
    @VisibleForTesting
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val currencies = MutableStateFlow(FiatCurrenciesData())

    private var fetchJob: Job? = null

    override fun ensureLoaded() {
        val current = currencies.value
        if (current.data == null && !current.isLoading) {
            requestRefresh()
        }
    }

    override fun requestRefresh() {
        if (fetchJob?.isActive == true) return
        currencies.update { it.copy(isLoading = true, error = null) }
        fetchJob = scope.launch { fetch() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetch() {
        try {
            val supported = fiatCurrencyDataSource.getSupportedFiatCurrencies()
            currencies.update { it.copy(data = supported, isLoading = false, error = null) }
        } catch (e: Exception) {
            currencies.update { data ->
                data.copy(
                    isLoading = false,
                    error = e.takeIf { data.data == null }
                )
            }
        }
    }
}
