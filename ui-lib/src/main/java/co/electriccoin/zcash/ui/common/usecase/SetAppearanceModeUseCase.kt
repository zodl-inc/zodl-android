package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.screen.more.MoreArgs

class SetAppearanceModeUseCase(
    private val navigationRouter: NavigationRouter,
    private val appearanceModeStorageProvider: AppearanceModeStorageProvider,
    private val isOledEnabledStorageProvider: IsOledEnabledStorageProvider,
) {
    suspend operator fun invoke(
        appearanceMode: AppearanceMode,
        isOledEnabled: Boolean
    ) {
        appearanceModeStorageProvider.store(appearanceMode)
        isOledEnabledStorageProvider.store(isOledEnabled)
        navigationRouter.backTo(MoreArgs::class)
    }
}
