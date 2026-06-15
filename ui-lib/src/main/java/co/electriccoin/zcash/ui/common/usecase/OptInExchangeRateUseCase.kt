package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.IsExchangeRateEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.PreferredFiatProvider

class OptInExchangeRateUseCase(
    private val navigationRouter: NavigationRouter,
    private val isExchangeRateEnabledStorageProvider: IsExchangeRateEnabledStorageProvider,
    private val preferredFiatProvider: PreferredFiatProvider,
) {
    suspend operator fun invoke(optIn: Boolean, fiatCurrency: FiatCurrency? = null) {
        if (fiatCurrency != null) {
            preferredFiatProvider.store(fiatCurrency)
        }
        if (optIn) optIn() else optOut()
    }

    private suspend fun optOut() {
        isExchangeRateEnabledStorageProvider.store(false)
        navigationRouter.back()
    }

    private suspend fun optIn() {
        isExchangeRateEnabledStorageProvider.store(true)
        navigationRouter.back()
    }
}
