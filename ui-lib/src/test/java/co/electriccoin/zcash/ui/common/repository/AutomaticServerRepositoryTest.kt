package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.FastestServersResult
import cash.z.ecc.android.sdk.model.PersistableWallet
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * Repository-level resolution of Automatic vs Manual server selection (MOB-1144), including the
 * migration rule: a null preference (wallets from before the setting existed) is Manual only when the
 * wallet points at a custom, non-bundled endpoint, and Automatic otherwise.
 *
 * Also covers the app-side half of the server switch hysteresis (MOB-1832): the guards and the arguments
 * handed to the SDK's fastest-server benchmark per tier (OHTTP first, clearnet only when OHTTP is down),
 * plus the resilience of
 * the foreground lane itself.
 *
 * Note: `init()` launches the lane on an internal `Dispatchers.IO` scope, so the tests drive
 * `observeSwitchCandidates` - the lane `init()` launches - on the test scope instead. Wiring `init()` to
 * the real foreground signal is exercised by the `ChooseServerSelectionTest` instrumentation test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticServerRepositoryTest {
    private val gateway = endpoint("ohttp-gateway.example")
    private val fallbacks = listOf(endpoint("zec.rocks"), endpoint("na.zec.rocks"))
    private val known = listOf(gateway) + fallbacks

    private val isAutomaticProvider = mockk<IsServerSelectionAutomaticProvider>(relaxed = true)
    private val persistedWallet = MutableStateFlow<PersistableWallet?>(null)
    private val persistableWalletProvider =
        mockk<PersistableWalletProvider>(relaxed = true) { every { persistableWallet } returns persistedWallet }
    private val lightWalletEndpointProvider =
        mockk<LightWalletEndpointProvider>(relaxed = true) {
            every { getEndpoints() } returns known
            every { getDefaultEndpoint() } returns gateway
            every { getFallbackEndpoints() } returns fallbacks
            every { isOhttpGateway(any()) } answers { firstArg<LightWalletEndpoint>().host.startsWith("ohttp-gateway") }
        }
    private val localBalances = MutableStateFlow<Map<AccountUuid, AccountBalance>?>(null)
    private val synchronizer = mockk<Synchronizer>(relaxed = true) { every { walletBalances } returns localBalances }
    private val synchronizerProvider =
        mockk<SynchronizerProvider>(relaxed = true).also {
            coEvery { it.getSynchronizerOrNull() } returns synchronizer
            every { it.synchronizer } returns MutableStateFlow<Synchronizer?>(synchronizer)
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

    private val walletRepository = mockk<WalletRepository>(relaxed = true)

    private val timeSource = TestTimeSource()

    // A relaxed mock returns a bare Object for the suspend Boolean getter; back it with a real value.
    private var fallbackActive = false
    private val fallbackFlag =
        mockk<IsOhttpFallbackActiveProvider>(relaxed = true) {
            coEvery { get() } answers { fallbackActive }
            coEvery { store(any()) } answers { fallbackActive = firstArg() }
        }

    private val repository =
        AutomaticServerRepositoryImpl(
            walletRepository = walletRepository,
            zashiProposalRepository = zashiProposalRepository,
            keystoneProposalRepository = keystoneProposalRepository,
            applicationStateProvider = mockk(relaxed = true),
            synchronizerProvider = synchronizerProvider,
            persistableWalletProvider = persistableWalletProvider,
            lightWalletEndpointProvider = lightWalletEndpointProvider,
            isServerSelectionAutomaticProvider = isAutomaticProvider,
            isOhttpFallbackActiveProvider = fallbackFlag,
            evaluationInterval = EvaluationInterval(MINIMUM_EVALUATION_INTERVAL, timeSource)
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
    fun automaticIsNeverCustom() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            assertEquals(false, repository.isServerCustom())
        }

    @Test
    fun manualWithBundledEndpointIsNotCustom() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns false
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(known.last())

            assertEquals(false, repository.isServerCustom())
        }

    @Test
    fun manualWithNonBundledEndpointIsCustom() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns false
            coEvery { persistableWalletProvider.getPersistableWallet() } returns
                wallet(endpoint("custom.example.com"))

            assertEquals(true, repository.isServerCustom())
        }

    @Test
    fun manualWithoutWalletIsNotCustom() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns false
            coEvery { persistableWalletProvider.getPersistableWallet() } returns null

            assertEquals(false, repository.isServerCustom())
        }

    @Test
    fun manualSelectionSkipsTheSdkBenchmark() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns false

            assertNull(repository.evaluateServerSwitch())
            coVerify(exactly = 0) { synchronizer.getFastestServers(any()) }
        }

    @Test
    fun inTransactionStateSkipsTheSdkBenchmark() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            every { zashiProposalRepository.submitState } returns MutableStateFlow(SubmitProposalState.Submitting)

            assertNull(repository.evaluateServerSwitch())
            coVerify(exactly = 0) { synchronizer.getFastestServers(any()) }
        }

    @Test
    fun scenario1And2GatewayReachableStaysOnOhttpWithoutProbingClearnet() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = true, rankedFallbacks = fallbacks)

            assertNull(repository.evaluateServerSwitch())
            // Only the OHTTP tier is benchmarked: no clearnet host learns the device IP while OHTTP works.
            coVerify(exactly = 1) { synchronizer.getFastestServers(listOf(gateway)) }
            coVerify(exactly = 0) { synchronizer.getFastestServers(fallbacks) }
        }

    @Test
    fun scenario3GatewayUnreachableFallsBackToTheBestClearnetServer() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = listOf(known.last(), fallbacks.first()))

            assertEquals(known.last(), repository.evaluateServerSwitch())
            coVerify(exactly = 1) { synchronizer.getFastestServers(listOf(gateway)) }
            coVerify(exactly = 1) { synchronizer.getFastestServers(fallbacks) }
        }

    @Test
    fun scenario4GatewayBackReturnsFromTheFallback() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(known.last())
            stubTiers(gatewayReachable = true, rankedFallbacks = listOf(known.last()))

            assertEquals(gateway, repository.evaluateServerSwitch())
        }

    @Test
    fun scenario6TheFallbackInUseFailsSoTheNextRankedServerIsChosen() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(known.last())
            stubTiers(gatewayReachable = false, rankedFallbacks = listOf(fallbacks.first()))

            assertEquals(fallbacks.first(), repository.evaluateServerSwitch())
        }

    @Test
    fun fallbackFlagFollowsTheDecision() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = listOf(known.last()))
            repository.evaluateServerSwitch()
            assertEquals(true, fallbackActive)

            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(known.last())
            stubTiers(gatewayReachable = true, rankedFallbacks = emptyList())
            repository.evaluateServerSwitch()
            assertEquals(false, fallbackActive)
        }

    @Test
    fun scenario7NothingReachableStaysPut() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = emptyList())

            assertNull(repository.evaluateServerSwitch())
        }

    /**
     * The wait is bounded, so this only pins that a snapshot which has not arrived yet still holds the
     * switch back - [switchIsAppliedAnywayWhenTheLocalBalanceSnapshotNeverArrives] pins the other end.
     */
    @Test
    fun switchCandidateWaitsForTheLocalBalanceSnapshot() =
        runTest {
            stubSwitchDecision(balances = null)

            assertNull(withTimeoutOrNull(1.seconds) { repository.resolveSwitchCandidate() })
        }

    @Test
    fun switchCandidateIsReleasedByTheLocalBalanceSnapshot() =
        runTest {
            stubSwitchDecision(balances = emptyMap())

            assertEquals(known.last(), repository.resolveSwitchCandidate())
        }

    @Test
    fun switchIsAppliedAnywayWhenTheLocalBalanceSnapshotNeverArrives() =
        runTest {
            stubSwitchDecision(balances = null)

            assertEquals(known.last(), repository.resolveSwitchCandidate())
        }

    @Test
    fun anAppliedSwitchIsConfirmedToTheSdk() =
        runTest {
            stubSwitchDecision(balances = emptyMap())

            val foregroundEdges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(foregroundEdges).launchIn(backgroundScope)
            runCurrent()

            foregroundEdges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(known.last()) }
            coVerify(exactly = 1) { synchronizer.confirmServerSwitch(known.last()) }
        }

    @Test
    fun aDeclinedSwitchIsNotConfirmedToTheSdk() =
        runTest {
            stubSwitchDecision(balances = emptyMap())
            stubTiers(gatewayReachable = false, rankedFallbacks = listOf(endpoint("no.longer.offered")))

            val foregroundEdges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(foregroundEdges).launchIn(backgroundScope)
            runCurrent()

            foregroundEdges.emit(Unit)
            runCurrent()

            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }
            coVerify(exactly = 0) { synchronizer.confirmServerSwitch(any()) }
        }

    @Test
    fun aCancelledEvaluationAppliesNothing() =
        runTest {
            stubSwitchDecision(balances = emptyMap())
            coEvery { synchronizer.getFastestServers(any()) } coAnswers { awaitCancellation() }

            val foregroundEdges = MutableSharedFlow<Unit>()
            val lane = repository.observeSwitchCandidates(foregroundEdges).launchIn(backgroundScope)
            runCurrent()

            foregroundEdges.emit(Unit)
            runCurrent()
            lane.cancelAndJoin()

            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }
            coVerify(exactly = 0) { synchronizer.confirmServerSwitch(any()) }
        }

    @Test
    fun aFailedEvaluationStillCountsAgainstTheEvaluationInterval() =
        runTest {
            stubSwitchDecision(balances = emptyMap())
            coEvery { synchronizer.getFastestServers(any()) } throws IllegalStateException("benchmark failed")

            val foregroundEdges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(foregroundEdges).launchIn(backgroundScope)
            runCurrent()

            foregroundEdges.emit(Unit)
            runCurrent()
            foregroundEdges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { synchronizer.getFastestServers(any()) }
        }

    @Test
    fun theLaneSurvivesAFailedEvaluationAndKeepsServingTheNextEdge() =
        runTest {
            stubSwitchDecision(balances = emptyMap())
            coEvery { synchronizer.getFastestServers(any()) } throws IllegalStateException("benchmark failed")

            val foregroundEdges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(foregroundEdges).launchIn(backgroundScope)
            runCurrent()

            foregroundEdges.emit(Unit)
            runCurrent()

            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }

            stubTiers(gatewayReachable = false, rankedFallbacks = listOf(known.last()))
            timeSource += MINIMUM_EVALUATION_INTERVAL
            foregroundEdges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(known.last()) }
        }

    @Test
    fun theLaneSurvivesAFailureToApplyTheSwitch() =
        runTest {
            stubSwitchDecision(balances = emptyMap())
            coEvery { walletRepository.updateWalletEndpoint(any()) } throws IllegalStateException("prefs failed")

            val foregroundEdges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(foregroundEdges).launchIn(backgroundScope)
            runCurrent()

            foregroundEdges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(known.last()) }
        }

    @Test
    fun aSecondForegroundEdgeInsideTheEvaluationIntervalIsNotEvaluated() =
        runTest {
            stubSwitchDecision(balances = emptyMap())
            stubTiers(gatewayReachable = true, rankedFallbacks = emptyList())

            val foregroundEdges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(foregroundEdges).launchIn(backgroundScope)
            runCurrent()

            foregroundEdges.emit(Unit)
            runCurrent()
            foregroundEdges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { synchronizer.getFastestServers(any()) }
        }

    private fun stubSwitchDecision(balances: Map<AccountUuid, AccountBalance>?) {
        val wallet = wallet(known.first())
        coEvery { isAutomaticProvider.get() } returns true
        coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet
        persistedWallet.value = wallet
        localBalances.value = balances
        stubTiers(gatewayReachable = false, rankedFallbacks = listOf(known.last()))
    }

    /** SDK benchmark per tier: the OHTTP tier validates the gateway or nothing; clearnet ranks as given. */
    private fun stubTiers(gatewayReachable: Boolean, rankedFallbacks: List<LightWalletEndpoint>) {
        coEvery { synchronizer.getFastestServers(any()) } answers {
            val requested = firstArg<List<LightWalletEndpoint>>()
            val isOhttpTier = requested.any { it.host.startsWith("ohttp-gateway") }
            val servers = if (isOhttpTier) (if (gatewayReachable) listOf(gateway) else emptyList()) else rankedFallbacks
            flowOf(FastestServersResult.Done(servers))
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
