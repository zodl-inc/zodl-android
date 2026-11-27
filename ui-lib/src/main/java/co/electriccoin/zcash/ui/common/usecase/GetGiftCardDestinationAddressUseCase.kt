package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.UnifiedAddressRequest
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider

/**
 * The address a redeemed gift card is swept to: the selected account's Orchard-only unified address.
 */
class GetGiftCardDestinationAddressUseCase(
    private val accountDataSource: AccountDataSource,
    private val synchronizerProvider: SynchronizerProvider,
) {
    suspend operator fun invoke(): String {
        val account = accountDataSource.getSelectedAccount()
        return synchronizerProvider
            .getSynchronizer()
            .getCustomUnifiedAddress(account.sdkAccount, UnifiedAddressRequest.Orchard)
    }
}
