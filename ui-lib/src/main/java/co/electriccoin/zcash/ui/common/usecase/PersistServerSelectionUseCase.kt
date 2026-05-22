package co.electriccoin.zcash.ui.common.usecase

import android.app.Application
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.model.ServerSelection
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository

class PersistServerSelectionUseCase(
    private val application: Application,
    private val walletRepository: WalletRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
    private val getSelectedEndpoint: GetSelectedEndpointUseCase,
) {
    @Throws(PersistEndpointException::class)
    suspend operator fun invoke(selection: ServerSelection) {
        when (selection) {
            ServerSelection.Automatic -> persistAutomatic()
            is ServerSelection.Manual -> persistManual(selection)
        }
    }

    private suspend fun persistAutomatic() {
        val endpoint = getAutomaticEndpoint()
        persistFlagAndEndpoint(isAutomatic = true, endpoint = endpoint)
    }

    @Throws(PersistEndpointException::class)
    private suspend fun persistManual(selection: ServerSelection.Manual) {
        when (val result = validateServerEndpoint(selection.endpoint)) {
            ServerValidation.Valid -> {
                persistFlagAndEndpoint(isAutomatic = false, endpoint = selection.endpoint)
            }

            is ServerValidation.InValid -> {
                throw PersistEndpointException(result.reason.message)
            }

            ServerValidation.Running -> {
                throw PersistEndpointException(null)
            }
        }
    }

    private suspend fun persistFlagAndEndpoint(isAutomatic: Boolean, endpoint: LightWalletEndpoint) {
        isServerSelectionAutomaticProvider.store(isAutomatic)
        walletRepository.updateWalletEndpoint(endpoint)
    }

    private suspend fun getAutomaticEndpoint(): LightWalletEndpoint =
        walletRepository.fastestEndpoints.value.servers
            ?.firstOrNull()
            ?: getSelectedEndpoint()?.takeIf { lightWalletEndpointProvider.getEndpoints().contains(it) }
            ?: lightWalletEndpointProvider.getDefaultEndpoint()

    private suspend fun validateServerEndpoint(endpoint: LightWalletEndpoint) =
        synchronizerProvider
            .getSynchronizer()
            .validateServerEndpoint(application, endpoint)
}
