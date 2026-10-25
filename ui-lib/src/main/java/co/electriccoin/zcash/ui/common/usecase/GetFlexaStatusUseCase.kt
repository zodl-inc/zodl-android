package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class GetFlexaStatusUseCase(
    private val configurationRepository: ConfigurationRepository,
    private val accountDataSource: AccountDataSource,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe() =
        configurationRepository
            .isFlexaAvailable
            .filterNotNull()
            .flatMapLatest { isAvailable ->
                if (isAvailable) {
                    accountDataSource.selectedAccount.map {
                        if (it is ZodlAccount) {
                            Status.ENABLED
                        } else {
                            Status.DISABLED
                        }
                    }
                } else {
                    flowOf(Status.UNAVAILABLE)
                }
            }.distinctUntilChanged()
}

enum class Status {
    UNAVAILABLE,
    ENABLED,
    DISABLED
}
