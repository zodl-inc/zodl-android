package co.electriccoin.zcash.ui.screen.deletewallet

import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.WalletCoordinator
import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.common.repository.FlexaRepository
import co.electriccoin.zcash.ui.common.repository.HomeMessageCacheRepository
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.first
import okhttp3.internal.closeQuietly

class ResetZashiUseCase(
    private val walletCoordinator: WalletCoordinator,
    private val flexaRepository: FlexaRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val standardPreferenceProvider: StandardPreferenceProvider,
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider,
    private val homeMessageCacheRepository: HomeMessageCacheRepository,
    private val biometricRepository: BiometricRepository,
    private val addressBookRepository: AddressBookRepository,
    private val metadataRepository: MetadataRepository,
) {
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    suspend operator fun invoke(keepFiles: Boolean) {
        try {
            requestBiometrics()
            flexaRepository.disconnect()
            deleteLocalFiles(keepFiles)
            closeSynchronizer()
            clearSDK()
            clearSharedPrefs()
            clearInMemoryData()
        } catch (_: BiometricsFailureException) {
            // do nothing
        } catch (_: BiometricsCancelledException) {
            // do nothing
        }
    }

    private suspend fun requestBiometrics() {
        biometricRepository.requestBiometrics(
            BiometricRequest(
                message =
                    stringRes(
                        R.string.authentication_system_ui_subtitle,
                        stringRes(R.string.authentication_use_case_delete_wallet)
                    )
            )
        )
    }

    private suspend fun closeSynchronizer() {
        (synchronizerProvider.getSynchronizer() as SdkSynchronizer).closeQuietly()
    }

    private fun deleteLocalFiles(keepFiles: Boolean) {
        if (!keepFiles) {
            addressBookRepository.delete()
            metadataRepository.delete()
        }
    }

    private suspend fun clearSDK() {
        walletCoordinator.deleteSdkDataFlow().first()
    }

    private suspend fun clearSharedPrefs() {
        standardPreferenceProvider().clearPreferences()
        encryptedPreferenceProvider().clearPreferences()
    }

    private fun clearInMemoryData() {
        homeMessageCacheRepository.reset()
    }
}
