package co.electriccoin.zcash.ui.common.usecase

import android.app.Application
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.IsPublicConnectionProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.ServerSelectionMutex
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository

class PersistServerSelectionUseCase(
    private val application: Application,
    private val walletRepository: WalletRepository,
    private val synchronizerProvider: SynchronizerProvider,
    private val isServerSelectionAutomaticProvider: IsServerSelectionAutomaticProvider,
    private val isOhttpFallbackActiveProvider: IsOhttpFallbackActiveProvider,
    private val getAutomaticEndpoint: GetAutomaticEndpointUseCase,
    private val isPublicConnectionProvider: IsPublicConnectionProvider,
    private val lightWalletEndpointProvider: LightWalletEndpointProvider,
    private val serverSelectionMutex: ServerSelectionMutex,
) {
    /** Private: switching to it always starts from the OHTTP gateway. */
    suspend fun persistPrivate() =
        serverSelectionMutex.withLock {
            isOhttpFallbackActiveProvider.store(false)
            isPublicConnectionProvider.store(false)
            val endpoint = getAutomaticEndpoint()
            persistFlagAndEndpoint(isAutomatic = true, endpoint = endpoint)
        }

    /**
     * Public: automatic selection on public (clearnet) servers; starts from the default clearnet server.
     * [afterPrivateUnavailable] marks a switch made from the "private connection unavailable" prompt: the
     * wallet then returns to Private by itself once the gateway answers again. A deliberate Public never does.
     * [fastest] is the public server a speed test just ranked first; without a usable one the default is used.
     */
    suspend fun persistPublic(afterPrivateUnavailable: Boolean = false, fastest: LightWalletEndpoint? = null) =
        serverSelectionMutex.withLock {
            isOhttpFallbackActiveProvider.store(afterPrivateUnavailable)
            isPublicConnectionProvider.store(true)
            val publicServers = lightWalletEndpointProvider.getFallbackEndpoints()
            val endpoint =
                fastest?.takeIf { it in publicServers }
                    ?: publicServers.firstOrNull()
                    ?: lightWalletEndpointProvider.getDefaultEndpoint()
            persistFlagAndEndpoint(isAutomatic = true, endpoint = endpoint)
        }

    /**
     * Persists a manual server selection.
     *
     * The OHTTP gateway is only ever reached as Private mode; a manual pin of it is converted to
     * Private to keep the health monitoring lane active. Validation happens outside the lock since
     * it's a network call.
     */
    @Throws(PersistEndpointException::class)
    suspend fun persistManual(endpoint: LightWalletEndpoint) {
        if (lightWalletEndpointProvider.isOhttpGateway(endpoint)) {
            persistPrivate()
            return
        }
        when (val result = validateServerEndpoint(endpoint)) {
            ServerValidation.Valid -> {
                serverSelectionMutex.withLock {
                    isPublicConnectionProvider.store(false)
                    persistFlagAndEndpoint(isAutomatic = false, endpoint = endpoint)
                }
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
