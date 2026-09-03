package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.PersistableWallet
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.model.FastestServersState
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Repository-level resolution of Automatic vs Manual server selection (MOB-1144), including the
 * migration rule: a null preference (wallets from before the setting existed) is Manual only when the
 * wallet points at a custom, non-bundled endpoint, and Automatic otherwise.
 *
 * Note: the automatic endpoint-switching pipeline lives in `init()`, which launches on an internal
 * `Dispatchers.IO` scope; it is exercised by the `ChooseServerSelectionTest` instrumentation test
 * rather than here, since deterministic unit coverage would require injecting that scope.
 */
class AutomaticServerRepositoryTest {
    private val default = endpoint("zec.rocks")
    private val known = listOf(default, endpoint("na.zec.rocks"))

    private val isAutomaticProvider = mockk<IsServerSelectionAutomaticProvider>(relaxed = true)
    private val persistableWalletProvider = mockk<PersistableWalletProvider>(relaxed = true)
    private val lightWalletEndpointProvider =
        mockk<LightWalletEndpointProvider>(relaxed = true) { every { getEndpoints() } returns known }
    private val synchronizerProvider =
        mockk<SynchronizerProvider>(relaxed = true) { every { synchronizer } returns MutableStateFlow(null) }

    private val repository =
        AutomaticServerRepositoryImpl(
            walletRepository = mockk(relaxed = true),
            zashiProposalRepository = mockk(relaxed = true),
            keystoneProposalRepository = mockk(relaxed = true),
            applicationStateProvider = mockk(relaxed = true),
            synchronizerProvider = synchronizerProvider,
            persistableWalletProvider = persistableWalletProvider,
            lightWalletEndpointProvider = lightWalletEndpointProvider,
            isServerSelectionAutomaticProvider = isAutomaticProvider
        )

    @Test
    fun explicitAutomaticPreferenceIsAutomatic() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            assertEquals(true, repository.isServerAutomatic())
        }

    @Test
    fun explicitManualPreferenceIsManual() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns false
            assertEquals(false, repository.isServerAutomatic())
        }

    @Test
    fun nullPreferenceWithCustomEndpointIsManual() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns null
            coEvery { persistableWalletProvider.getPersistableWallet() } returns
                wallet(endpoint("custom.example.com"))

            assertEquals(false, repository.isServerAutomatic())
        }

    @Test
    fun nullPreferenceWithBundledEndpointIsAutomatic() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns null
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(known.last())

            assertEquals(true, repository.isServerAutomatic())
        }

    @Test
    fun nullPreferenceWithoutWalletIsAutomatic() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns null
            coEvery { persistableWalletProvider.getPersistableWallet() } returns null

            assertEquals(true, repository.isServerAutomatic())
        }

    @Test
    fun automaticCandidateWaitsForLocalBalanceSnapshot() {
        val fastest = FastestServersState(servers = listOf(default), isLoading = false)
        val loading = FastestServersState(servers = listOf(default), isLoading = true)

        assertEquals(null, resolveAutomaticServerCandidate(fastest, localBalances = null))
        assertEquals(null, resolveAutomaticServerCandidate(loading, localBalances = emptyMap<Any, Any>()))
        assertEquals(default, resolveAutomaticServerCandidate(fastest, localBalances = emptyMap<Any, Any>()))
    }

    private fun wallet(walletEndpoint: LightWalletEndpoint) =
        mockk<PersistableWallet> { every { endpoint } returns walletEndpoint }

    private fun endpoint(host: String) =
        LightWalletEndpoint(
            host = host,
            port = 443,
            isSecure = true
        )
}
