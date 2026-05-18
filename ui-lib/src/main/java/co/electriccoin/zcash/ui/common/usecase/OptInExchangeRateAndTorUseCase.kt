package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider

class OptInExchangeRateAndTorUseCase(
    private val navigationRouter: NavigationRouter,
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider
) {
    suspend operator fun invoke(optIn: Boolean) {
        isTorEnabledStorageProvider.store(optIn)
        navigationRouter.back()
    }
}
