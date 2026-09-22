package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GetLedgerStatusUseCase(
    private val accountDataSource: AccountDataSource,
) {
    fun observe() =
        accountDataSource.allAccounts
            .map {
                val enabled = it?.none { account -> account is LedgerAccount } ?: false
                if (enabled) {
                    Status.ENABLED
                } else {
                    Status.UNAVAILABLE
                }
            }.distinctUntilChanged()
}
