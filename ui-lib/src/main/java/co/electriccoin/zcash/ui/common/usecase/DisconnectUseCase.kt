package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.migration.MigrationAppHooks
import co.electriccoin.zcash.ui.common.model.HWWalletAccount
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
     * The hardware account [accountStorageKeyId] names, or null when it is no longer there. There
     * is deliberately no fallback: with two vendors connected, picking "the first one" would
     * silently disconnect a device the user did not choose.
     */
    suspend fun getHardwareWalletAccount(accountStorageKeyId: String): WalletAccount? =
        accountDataSource
            .getAllAccounts()
            .firstOrNull { it is HWWalletAccount && it.sdkAccount.accountUuid.toStorageKeyId() == accountStorageKeyId }
}
