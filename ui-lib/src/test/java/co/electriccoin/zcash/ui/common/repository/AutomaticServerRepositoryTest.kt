package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.PersistableWallet
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Repository-level resolution of Automatic vs Manual server selection (MOB-1144), including the
 * migration rule: a null preference (wallets from before the setting existed) is Manual only when the
 * wallet points at a custom, non-bundled endpoint, and Automatic otherwise.
 *
 * Also covers the app-side half of the server switch hysteresis (MOB-1832): the guards and the arguments
 * handed to the SDK's `evaluateServerSwitch`, which owns the thresholds themselves.
 *
 * Note: the foreground trigger pipeline lives in `init()`, which launches on an internal
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
    private val synchronizer = mockk<Synchronizer>(relaxed = true)
    private val synchronizerProvider =
        mockk<SynchronizerProvider>(relaxed = true).also {
            coEvery { it.getSynchronizerOrNull() } returns synchronizer
        }

    /**
     * A relaxed mock hands back a non-null value for `.value`, which the repository would read as
     * "a transaction is in flight", so both proposal repositories are stubbed with explicit empty state.
     */
    private val zashiProposalRepository =
        mockk<ZashiProposalRepository>(relaxed = true) {
            every { transactionProposal } returns MutableStateFlow<TransactionProposal?>(null)
            every { submitState } returns MutableStateFlow<SubmitProposalState?>(null)
        }
    private val keystoneProposalRepository =
        mockk<KeystoneProposalRepository>(relaxed = true) {
            every { transactionProposal } returns MutableStateFlow<TransactionProposal?>(null)
            every { submitState } returns MutableStateFlow<SubmitProposalState?>(null)
        }

    private val repository =
        AutomaticServerRepositoryImpl(
            walletRepository = mockk(relaxed = true),
            zashiProposalRepository = zashiProposalRepository,
            keystoneProposalRepository = keystoneProposalRepository,
            applicationStateProvider = mockk(relaxed = true),
            persistableWalletProvider = persistableWalletProvider,
            lightWalletEndpointProvider = lightWalletEndpointProvider,
            isServerSelectionAutomaticProvider = isAutomaticProvider,
            synchronizerProvider = synchronizerProvider
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
    fun manualSelectionSkipsTheSdkBenchmark() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns false

            assertNull(repository.evaluateServerSwitch())
            coVerify(exactly = 0) { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) }
        }

    @Test
    fun inTransactionStateSkipsTheSdkBenchmark() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            every { zashiProposalRepository.submitState } returns MutableStateFlow(SubmitProposalState.Submitting)

            assertNull(repository.evaluateServerSwitch())
            coVerify(exactly = 0) { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) }
        }

    @Test
    fun sdkDecisionToStayIsPassedThrough() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(known.first())
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } returns null

            assertNull(repository.evaluateServerSwitch())
        }

    @Test
    fun sdkDecisionToSwitchIsPassedThrough() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(known.first())
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } returns known.last()

            assertEquals(known.last(), repository.evaluateServerSwitch())
        }

    @Test
    fun sdkIsCalledWithTheCurrentEndpointAndEveryKnownCandidate() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(known.first())
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } returns null

            repository.evaluateServerSwitch()

            coVerify(exactly = 1) {
                synchronizer.evaluateServerSwitch(
                    current = known.first(),
                    candidates = known,
                    fetchThreshold = 5.seconds,
                    blocksToFetch = 1
                )
            }
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
