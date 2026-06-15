package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressType

class NavigateToRequestShieldedUseCase(
    private val accountDataSource: AccountDataSource,
    private val navigateToRequestZec: NavigateToRequestZecUseCase,
) {
    suspend operator fun invoke(requestNewAddress: Boolean = true) {
        if (requestNewAddress) {
            accountDataSource.requestNextShieldedAddress()
        }
        navigateToRequestZec(ReceiveAddressType.Unified.ordinal)
    }
}
