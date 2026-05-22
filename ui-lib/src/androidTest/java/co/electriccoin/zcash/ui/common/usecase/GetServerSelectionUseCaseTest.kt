package co.electriccoin.zcash.ui.common.usecase

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import cash.z.ecc.android.sdk.WalletInitMode
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.model.ServerSelection
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetServerSelectionUseCaseTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val lightWalletEndpointProvider = LightWalletEndpointProvider(application)
    private val knownEndpoints = lightWalletEndpointProvider.getEndpoints()

    @Test
    @SmallTest
    fun nullFlagResolvesToAutomatic() =
        runTest {
            val selection =
                createUseCase(
                    isAutomatic = null,
                    walletEndpoint = knownEndpoints.first()
                ).invoke()

            assertEquals(ServerSelection.Automatic, selection)
        }

    @Test
    @SmallTest
    fun trueFlagResolvesToAutomatic() =
        runTest {
            val selection =
                createUseCase(
                    isAutomatic = true,
                    walletEndpoint = knownEndpoints[1]
                ).invoke()

            assertEquals(ServerSelection.Automatic, selection)
        }

    @Test
    @SmallTest
    fun falseFlagWithKnownEndpointResolvesToManual() =
        runTest {
            val endpoint = knownEndpoints[1]

            val selection =
                createUseCase(
                    isAutomatic = false,
                    walletEndpoint = endpoint
                ).invoke()

            assertEquals(ServerSelection.Manual(endpoint = endpoint), selection)
        }

    @Test
    @SmallTest
    fun falseFlagWithUnknownEndpointResolvesToManual() =
        runTest {
            val endpoint = LightWalletEndpoint(host = "custom.example.com", port = 9067, isSecure = true)

            val selection =
                createUseCase(
                    isAutomatic = false,
                    walletEndpoint = endpoint
                ).invoke()

            assertIs<ServerSelection.Manual>(selection)
            assertEquals(endpoint, selection.endpoint)
        }

    @Test
    @SmallTest
    fun falseFlagWithNoWalletResolvesToAutomatic() =
        runTest {
            val selection =
                createUseCase(
                    isAutomatic = false,
                    walletEndpoint = null
                ).invoke()

            assertEquals(ServerSelection.Automatic, selection)
        }

    private fun createUseCase(
        isAutomatic: Boolean?,
        walletEndpoint: LightWalletEndpoint?
    ) = GetServerSelectionUseCase(
        isServerSelectionAutomaticProvider = FakeAutomaticFlagProvider(isAutomatic),
        persistableWalletProvider = FakePersistableWalletProvider(walletEndpoint),
    )
}

private class FakeAutomaticFlagProvider(
    initial: Boolean?
) : IsServerSelectionAutomaticProvider {
    private val state = MutableStateFlow(initial)

    override suspend fun get(): Boolean? = state.value

    override suspend fun store(amount: Boolean) {
        state.value = amount
    }

    override fun observe(): Flow<Boolean?> = state

    override suspend fun clear() {
        state.value = null
    }
}

private class FakePersistableWalletProvider(
    endpoint: LightWalletEndpoint?
) : PersistableWalletProvider {
    private val wallet =
        endpoint?.let {
            PersistableWallet(
                network = ZcashNetwork.Mainnet,
                endpoint = it,
                birthday = null,
                seedPhrase = SeedPhrase(List(SeedPhrase.SEED_PHRASE_SIZE) { "abandon" }),
                walletInitMode = WalletInitMode.ExistingWallet
            )
        }

    override val persistableWallet = MutableStateFlow(wallet)

    override suspend fun store(persistableWallet: PersistableWallet) = Unit

    override suspend fun getPersistableWallet(): PersistableWallet? = wallet

    override suspend fun requirePersistableWallet(): PersistableWallet = checkNotNull(wallet)
}
