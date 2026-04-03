package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.util.loggableNot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DisconnectUseCase(
    private val accountDataSource: AccountDataSource,
    private val biometricRepository: BiometricRepository,
    private val navigationRouter: NavigationRouter,
) {
    private val logger = loggableNot("DisconnectUseCase")

    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(keystoneAccount: KeystoneAccount) =
        withContext(Dispatchers.IO) {
            try {
                // Request biometric authentication before disconnecting
                biometricRepository.requestBiometrics(
                    BiometricRequest(message = stringRes(R.string.disconnect_hardware_wallet_biometric_message))
                )

                logger("deleteAccount $keystoneAccount")
                // Delete the hardware wallet account
                accountDataSource.deleteAccount(keystoneAccount)

                logger("deleteAccount success")

                // Explicitly select Zashi account after disconnecting Keystone
                val zashiAccount = accountDataSource.getZashiAccount()
                accountDataSource.selectAccount(zashiAccount)

                // Navigate back to home/root after successful disconnection
                navigationRouter.backToRoot()

                Result.success(Unit)
            } catch (_: BiometricsFailureException) {
                // do nothing
            } catch (_: BiometricsCancelledException) {
                // do nothing
            } catch (e: Exception) {
                logger("deleteAccount error $e")
            }
        }

    suspend fun getKeystoneAccount(): KeystoneAccount? =
        accountDataSource
            .getAllAccounts()
            .filterIsInstance<KeystoneAccount>()
            .firstOrNull()
}
