package co.electriccoin.zcash.ui.common.usecase

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.model.ServerSelection
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.ServerSelectionProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.common.viewmodel.SecretState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PersistServerSelectionUseCaseTest {
    @Test
    @SmallTest
    fun endpointUpdateFailureRollsBackStoredSelection() =
        runTest {
            val previousEndpoint = LightWalletEndpoint(host = "previous.example.com", port = 443, isSecure = true)
            val previousSelection = ServerSelection.manual(endpoint = previousEndpoint, isCustom = true)
            val automaticEndpoint = LightWalletEndpoint(host = "fastest.example.com", port = 443, isSecure = true)
            val serverSelectionProvider = FakeServerSelectionProvider(previousSelection)
            val walletRepository =
                FakeWalletRepository(
                    fastestEndpoint = automaticEndpoint,
                    endpointUpdateException = IllegalStateException("update failed")
                )

            val error =
                assertFailsWith<PersistEndpointException> {
                    createUseCase(
                        walletRepository = walletRepository,
                        serverSelectionProvider = serverSelectionProvider
                    ).invoke(ServerSelection.automatic())
                }

            assertEquals("update failed", error.message)
            assertEquals(automaticEndpoint, walletRepository.updatedEndpoint)
            assertEquals(previousSelection, serverSelectionProvider.getServerSelection())
        }

    private fun createUseCase(
        walletRepository: WalletRepository,
        serverSelectionProvider: ServerSelectionProvider
    ): PersistServerSelectionUseCase {
        val application = ApplicationProvider.getApplicationContext<Application>()
        return PersistServerSelectionUseCase(
            application = application,
            walletRepository = walletRepository,
            synchronizerProvider = UnusedSynchronizerProvider(),
            lightWalletEndpointProvider = LightWalletEndpointProvider(application),
            serverSelectionProvider = serverSelectionProvider,
            getSelectedEndpoint = GetSelectedEndpointUseCase(EmptyPersistableWalletProvider)
        )
    }
}

private class FakeServerSelectionProvider(
    initialSelection: ServerSelection?
) : ServerSelectionProvider {
    private val mutableServerSelection = MutableStateFlow(initialSelection)

    override val serverSelection: Flow<ServerSelection?> = mutableServerSelection

    override suspend fun store(serverSelection: ServerSelection) {
        mutableServerSelection.value = serverSelection
    }

    override suspend fun getServerSelection() = mutableServerSelection.value
}

private class FakeWalletRepository(
    fastestEndpoint: LightWalletEndpoint,
    private val endpointUpdateException: Exception
) : WalletRepository {
    override val secretState = MutableStateFlow(SecretState.NONE)
    override val fastestEndpoints = MutableStateFlow(FastestServersState(listOf(fastestEndpoint), false))
    override val walletRestoringState = MutableStateFlow(WalletRestoringState.NONE)

    var updatedEndpoint: LightWalletEndpoint? = null
        private set

    override fun createNewWallet() = Unit

    override fun restoreWallet(
        network: ZcashNetwork,
        seedPhrase: SeedPhrase,
        birthday: BlockHeight
    ) = Unit

    override suspend fun updateWalletEndpoint(endpoint: LightWalletEndpoint) {
        updatedEndpoint = endpoint
        throw endpointUpdateException
    }

    override fun refreshFastestServers() = Unit
}

private object EmptyPersistableWalletProvider : PersistableWalletProvider {
    override val persistableWallet = MutableStateFlow<PersistableWallet?>(null)

    override suspend fun store(persistableWallet: PersistableWallet) = Unit

    override suspend fun getPersistableWallet(): PersistableWallet? = null

    override suspend fun requirePersistableWallet(): PersistableWallet = error("Persistable wallet is not used")
}

private class UnusedSynchronizerProvider : SynchronizerProvider {
    override val error = MutableStateFlow<SynchronizerError?>(null)
    override val synchronizer = MutableStateFlow<Synchronizer?>(null)

    override suspend fun getSynchronizer(): Synchronizer = error("Synchronizer is not used")

    override fun resetSynchronizer() = Unit
}
