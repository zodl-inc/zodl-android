package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.migration.MigrationAppHooks
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.util.loggableNot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DisconnectUseCase(
    private val accountDataSource: AccountDataSource,
    private val biometricRepository: BiometricRepository,
    private val migrationAppHooks: MigrationAppHooks,
) {
    private val logger = loggableNot("DisconnectUseCase")

    /**
     * A disconnected hardware account must take its scheduled migration work with it — otherwise
     * the lanes zombie-retry for an account that no longer exists. Deleting the account also drops
     * a Ledger account's stored binding, and the software wallet is selected afterwards so the app
     * is never left pointing at an account that is gone.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(hardwareAccount: WalletAccount) =
        withContext(Dispatchers.IO) {
            biometricRepository.requestBiometrics(
                BiometricRequest(message = stringRes(R.string.disconnect_hardware_wallet_biometric_message))
            )

            migrationAppHooks.cancelMigrationWork(hardwareAccount.sdkAccount.accountUuid.toStorageKeyId())

            logger("deleteAccount $hardwareAccount")
            accountDataSource.deleteAccount(hardwareAccount)

            logger("deleteAccount success")

            val zashiAccount = accountDataSource.getZashiAccount()
            accountDataSource.selectAccount(zashiAccount)
        }

    /**
     * The hardware account the disconnect screen acts on: the selected one when a hardware wallet
     * is selected, otherwise the only one there is. With a single Keystone connected — every case
     * that existed before Ledger — both answers are that Keystone.
     */
    suspend fun getHardwareWalletAccount(): WalletAccount? {
        val accounts = accountDataSource.getAllAccounts().filter { it.isHardwareWallet }
        return accounts.firstOrNull { it.isSelected } ?: accounts.firstOrNull()
    }
}

private val WalletAccount.isHardwareWallet: Boolean
    get() = this is KeystoneAccount || this is LedgerAccount
