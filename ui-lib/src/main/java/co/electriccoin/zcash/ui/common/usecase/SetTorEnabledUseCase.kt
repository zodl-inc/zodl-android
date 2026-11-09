package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider

class SetTorEnabledUseCase(
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider
) {
    suspend operator fun invoke(enabled: Boolean) = isTorEnabledStorageProvider.store(enabled)
}
