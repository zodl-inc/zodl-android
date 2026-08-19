package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.design.theme.AppearanceMode

class SetAppearanceModeUseCase(
    private val navigationRouter: NavigationRouter,
    private val appearanceModeStorageProvider: AppearanceModeStorageProvider,
) {
    suspend operator fun invoke(appearanceMode: AppearanceMode) {
        appearanceModeStorageProvider.store(appearanceMode)
        navigationRouter.back()
    }
}
