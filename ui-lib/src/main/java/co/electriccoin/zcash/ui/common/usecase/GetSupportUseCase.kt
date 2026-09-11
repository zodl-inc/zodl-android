package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import co.electriccoin.zcash.configuration.api.ConfigurationProvider
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.screen.support.model.SupportInfo

class GetSupportUseCase(
    private val context: Context,
    private val androidConfigurationProvider: ConfigurationProvider,
    private val isTorEnabledStorageProvider: IsTorEnabledStorageProvider
) {
    suspend operator fun invoke() =
        SupportInfo.new(context, androidConfigurationProvider, isTorEnabledStorageProvider.get())
}
