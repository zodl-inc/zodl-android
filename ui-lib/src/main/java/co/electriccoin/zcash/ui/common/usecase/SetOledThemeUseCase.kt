package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.IsOledThemeEnabledStorageProvider

class SetOledThemeUseCase(
    private val navigationRouter: NavigationRouter,
    private val isOledThemeEnabledStorageProvider: IsOledThemeEnabledStorageProvider,
) {
    suspend operator fun invoke(isOledTheme: Boolean) {
        isOledThemeEnabledStorageProvider.store(isOledTheme)
        navigationRouter.back()
    }
}
