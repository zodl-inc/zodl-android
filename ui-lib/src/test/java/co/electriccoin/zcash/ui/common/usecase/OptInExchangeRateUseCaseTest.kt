package co.electriccoin.zcash.ui.common.usecase

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.IsExchangeRateEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.PreferredFiatProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behavior of opting in/out of currency conversion (MOB-1124): the selected currency must be
 * persisted before the opt-in flag, opting out must not touch the currency, and both paths
 * navigate back.
 */
class OptInExchangeRateUseCaseTest {
    @Test
    fun optInWithCurrencyStoresCurrencyThenOptInAndNavigatesBack() =
        runTest {
            val events = mutableListOf<String>()
            val router = FakeOptInNavigationRouter()
            val optInProvider = FakeIsExchangeRateEnabledStorageProvider(events)
            val fiatProvider = FakePreferredFiatProvider(events)
            val useCase = OptInExchangeRateUseCase(router, optInProvider, fiatProvider)

            useCase(optIn = true, fiatCurrency = FiatCurrency("EUR"))

            // Order matters: currency is stored before the opt-in flag.
            assertEquals(listOf("storeFiat=EUR", "storeOptIn=true"), events)
            assertEquals(FiatCurrency("EUR"), fiatProvider.stored)
            assertEquals(true, optInProvider.stored)
            assertEquals(1, router.backCount)
        }

    @Test
    fun optOutDoesNotStoreCurrencyAndNavigatesBack() =
        runTest {
            val events = mutableListOf<String>()
            val router = FakeOptInNavigationRouter()
            val optInProvider = FakeIsExchangeRateEnabledStorageProvider(events)
            val fiatProvider = FakePreferredFiatProvider(events)
            val useCase = OptInExchangeRateUseCase(router, optInProvider, fiatProvider)

            useCase(optIn = false)

            assertEquals(listOf("storeOptIn=false"), events)
            assertNull(fiatProvider.stored)
            assertEquals(false, optInProvider.stored)
            assertEquals(1, router.backCount)
        }

    @Test
    fun optInWithoutCurrencyLeavesCurrencyUntouched() =
        runTest {
            val events = mutableListOf<String>()
            val router = FakeOptInNavigationRouter()
            val optInProvider = FakeIsExchangeRateEnabledStorageProvider(events)
            val fiatProvider = FakePreferredFiatProvider(events)
            val useCase = OptInExchangeRateUseCase(router, optInProvider, fiatProvider)

            useCase(optIn = true)

            assertEquals(listOf("storeOptIn=true"), events)
            assertNull(fiatProvider.stored)
        }
}

private class FakeIsExchangeRateEnabledStorageProvider(
    private val events: MutableList<String>
) : IsExchangeRateEnabledStorageProvider {
    var stored: Boolean? = null
        private set

    override suspend fun get(): Boolean? = stored

    override suspend fun store(amount: Boolean) {
        stored = amount
        events += "storeOptIn=$amount"
    }

    override fun observe(): Flow<Boolean?> = emptyFlow()

    override suspend fun clear() {
        stored = null
    }
}

private class FakePreferredFiatProvider(
    private val events: MutableList<String>
) : PreferredFiatProvider {
    var stored: FiatCurrency? = null
        private set

    override suspend fun get(): FiatCurrency? = stored

    override suspend fun store(amount: FiatCurrency) {
        stored = amount
        events += "storeFiat=${amount.code}"
    }

    override fun observe(): Flow<FiatCurrency?> = emptyFlow()

    override suspend fun clear() {
        stored = null
    }
}

private class FakeOptInNavigationRouter : NavigationRouter {
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
