package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Finds the view-only account a route names by its storage-key id. Any other kind of account with
 * that id counts as missing, so the edit and remove screens can never act on the Zodl account or a
 * hardware wallet.
 */
internal class GetViewOnlyAccountUseCase(
    private val getWalletAccounts: GetWalletAccountsUseCase,
) {
    /** The account once the accounts are loaded, or null when it is missing or not view-only. */
    suspend operator fun invoke(accountKeyId: String): ViewOnlyAccount? =
        getWalletAccounts.require().first().findViewOnly(accountKeyId)

    /**
     * The account as it changes, a rename included. Emits nothing until the accounts are loaded,
     * then null whenever the account is missing or not view-only.
     */
    fun observe(accountKeyId: String): Flow<ViewOnlyAccount?> =
        getWalletAccounts.require().map { it.findViewOnly(accountKeyId) }

    private fun List<WalletAccount>.findViewOnly(accountKeyId: String): ViewOnlyAccount? =
        firstOrNull { it.sdkAccount.accountUuid.toStorageKeyId() == accountKeyId } as? ViewOnlyAccount
}
