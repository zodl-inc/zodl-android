package co.electriccoin.zcash.ui.common.usecase

import android.app.Application
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository

class PersistServerSelectionUseCase(
    private val application: Application,
    private val walletRepository: WalletRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
    private val isOhttpFallbackActiveProvider: IsOhttpFallbackActiveProvider,
    private val getAutomaticEndpoint: GetAutomaticEndpointUseCase,
) {
    suspend fun persistAutomatic() {
        // Switching to Automatic always starts from the OHTTP gateway; if it turns out to be
        // unreachable, AutomaticServerRepository reports it to the user (no automatic clearnet fallback).
        isOhttpFallbackActiveProvider.store(false)
        val endpoint = getAutomaticEndpoint()
        persistFlagAndEndpoint(isAutomatic = true, endpoint = endpoint)
    }

    @Throws(PersistEndpointException::class)
    suspend fun persistManual(endpoint: LightWalletEndpoint) {
        when (val result = validateServerEndpoint(endpoint)) {
            ServerValidation.Valid -> persistFlagAndEndpoint(isAutomatic = false, endpoint = endpoint)
            is ServerValidation.InValid -> throw PersistEndpointException(result.reason.message)
            ServerValidation.Running -> throw PersistEndpointException(null)
        }
    }

    private suspend fun persistFlagAndEndpoint(isAutomatic: Boolean, endpoint: LightWalletEndpoint) {
        isServerSelectionAutomaticProvider.store(isAutomatic)
        // An explicit manual choice is respected as-is: it is never an "OHTTP fallback", so the
        // fallback coordinator must not try to move the wallet back to the gateway.
        if (!isAutomatic) isOhttpFallbackActiveProvider.store(false)
        walletRepository.updateWalletEndpoint(endpoint)
    }

    private suspend fun validateServerEndpoint(endpoint: LightWalletEndpoint) =
        synchronizerProvider
            .getSynchronizer()
            .validateServerEndpoint(application, endpoint)
}
