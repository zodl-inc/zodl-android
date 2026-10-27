package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.migration.MigrationAppHooks
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.viewingkey.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Removes a view-only account from this device, shaped like DisconnectUseCase for a hardware wallet:
 * the user authenticates first, any migration work scheduled for the account is cancelled, the SDK
 * account is deleted (which also forgets its name), and when it was the selected account the Zodl
 * account is selected so the app never points at an account that is gone. The address book and the
 * account's metadata files stay, only Reset Zodl deletes metadata, and the synchronizer is not
 * restarted, as for a disconnect.
 *
 * Only a view-only account is ever removed here: the id is resolved again right before deleting,
 * and an id that names nothing, or names the Zodl account or a hardware wallet, removes nothing.
 * Whether the account was selected is read again after the authentication, since the account
 * resolved before it can be a stale snapshot.
 */
internal class RemoveViewOnlyAccountUseCase(
    private val getViewOnlyAccount: GetViewOnlyAccountUseCase,
    private val accountDataSource: AccountDataSource,
    private val biometricRepository: BiometricRepository,
    private val migrationAppHooks: MigrationAppHooks,
) {
    /**
     * @return false when there was no view-only account to remove.
     * @throws co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException if the user
     *         dismissed the authentication; nothing was removed.
     * @throws co.electriccoin.zcash.ui.common.repository.BiometricsFailureException if the
     *         authentication failed; nothing was removed.
     */
    suspend operator fun invoke(accountKeyId: String): Boolean =
        withContext(Dispatchers.IO) {
            val account = getViewOnlyAccount(accountKeyId) ?: return@withContext false

            biometricRepository.requestBiometrics(
                BiometricRequest(message = stringRes(R.string.view_only_remove_biometric_message))
            )

            migrationAppHooks.cancelMigrationWork(accountKeyId)

            val wasSelected =
                accountDataSource
                    .getAllAccounts()
                    .firstOrNull { it.sdkAccount.accountUuid == account.sdkAccount.accountUuid }
                    ?.isSelected
                    ?: account.isSelected

            accountDataSource.deleteAccount(account)

            if (wasSelected) {
                accountDataSource.selectAccount(accountDataSource.getZashiAccount())
            }
            true
        }
}
