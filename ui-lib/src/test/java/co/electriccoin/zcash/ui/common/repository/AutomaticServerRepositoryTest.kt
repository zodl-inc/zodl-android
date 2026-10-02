package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.FastestServersResult
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
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

    private val automaticPreference = MutableStateFlow<Boolean?>(true)
    private val isAutomaticProvider =
        mockk<IsServerSelectionAutomaticProvider>(relaxed = true) { every { observe() } returns automaticPreference }
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
    private val fallbackActiveFlow = MutableStateFlow(false)
    private val fallbackFlag =
        mockk<IsOhttpFallbackActiveProvider>(relaxed = true) {
            every { observe() } returns fallbackActiveFlow
            coEvery { get() } answers { fallbackActive }
            coEvery { store(any()) } answers { fallbackActive = firstArg() }
        }

    // A relaxed mock returns a bare Object for the suspend Boolean getter; back it with a real value.
    init {
        coEvery { isAutomaticProvider.get() } returns true
    }

    private val foreground = MutableStateFlow(true)
    private val applicationStateProvider =
        mockk<ApplicationStateProvider>(relaxed = true) { every { isInForeground } returns foreground }

    private val repository =
        AutomaticServerRepositoryImpl(
            application = mockk(relaxed = true),
            walletRepository = walletRepository,
            zashiProposalRepository = zashiProposalRepository,
            keystoneProposalRepository = keystoneProposalRepository,
            applicationStateProvider = applicationStateProvider,
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
    fun scenario3GatewayUnreachableReportsUnavailabilityInsteadOfAutomaticFallback() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = listOf(known.last(), fallbacks.first()))

            assertNull(repository.evaluateServerSwitch())
            coVerify(exactly = 1) { synchronizer.getFastestServers(listOf(gateway)) }
            coVerify(exactly = 0) { synchronizer.getFastestServers(fallbacks) }
        }

    @Test
    fun gatewayUnavailableBannerClearsWhenAutomaticModeIsLeft() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            val banner = collectBanner()

            assertNull(repository.evaluateServerSwitch())
            assertEquals(true, banner.last())

            automaticPreference.value = false
            assertEquals(false, banner.last())
        }

    @Test
    fun gatewayUnavailableReportedOutsideAutomaticModeNeverShowsTheBanner() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            val banner = collectBanner()

            // The user leaves automatic mode while an evaluation that started earlier is still running:
            // it then finishes and reports the gateway as unavailable.
            automaticPreference.value = false
            assertNull(repository.evaluateServerSwitch())

            assertEquals(false, banner.last())
        }

    /**
     * The wait is bounded, so this only pins that a snapshot which has not arrived yet still holds the
     * switch back - [switchIsAppliedAnywayWhenTheLocalBalanceSnapshotNeverArrives] pins the other end.
     */
    @Test
    fun switchCandidateWaitsForTheLocalBalanceSnapshot() =
        runTest {
            stubSwitchDecision(balances = null)

            assertNull(withTimeoutOrNull(1.seconds) { repository.resolveSwitchCandidate(SwitchTrigger.CONNECTIVITY) })
        }

    @Test
    fun switchCandidateIsReleasedByTheLocalBalanceSnapshot() =
        runTest {
            stubSwitchDecision(balances = emptyMap())

            assertEquals(gateway, repository.resolveSwitchCandidate(SwitchTrigger.CONNECTIVITY))
        }

    @Test
    fun switchIsAppliedAnywayWhenTheLocalBalanceSnapshotNeverArrives() =
        runTest {
            stubSwitchDecision(balances = null)

            assertEquals(gateway, repository.resolveSwitchCandidate(SwitchTrigger.CONNECTIVITY))
        }

    @Test
    fun anAppliedSwitchIsConfirmedToTheSdk() =
        runTest {
            stubSwitchDecision(balances = emptyMap())

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges, SwitchTrigger.CONNECTIVITY).launchIn(backgroundScope)
            runCurrent()

            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(gateway) }
            coVerify(exactly = 1) { synchronizer.confirmServerSwitch(gateway) }
        }

    @Test
    fun aDeclinedSwitchIsNotConfirmedToTheSdk() =
        runTest {
            stubSwitchDecision(balances = emptyMap())
            // The user picks a server manually while the evaluation is running: the first automatic-mode
            // check (evaluation) still passes, the second (apply) does not.
            coEvery { isAutomaticProvider.get() } returnsMany listOf(true, false)

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges, SwitchTrigger.CONNECTIVITY).launchIn(backgroundScope)
            runCurrent()

            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }
            coVerify(exactly = 0) { synchronizer.confirmServerSwitch(any()) }
        }

    @Test
    fun aCancelledEvaluationAppliesNothing() =
        runTest {
            stubSwitchDecision(balances = emptyMap())
            coEvery { synchronizer.getFastestServers(any()) } coAnswers { awaitCancellation() }

            val edges = MutableSharedFlow<Unit>()
            val lane = repository.observeSwitchCandidates(edges).launchIn(backgroundScope)
            runCurrent()

            edges.emit(Unit)
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

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges).launchIn(backgroundScope)
            runCurrent()

            edges.emit(Unit)
            runCurrent()
            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { synchronizer.getFastestServers(any()) }
        }

    @Test
    fun theLaneSurvivesAFailedEvaluationAndKeepsServingTheNextEdge() =
        runTest {
            stubSwitchDecision(balances = emptyMap())
            coEvery { synchronizer.getFastestServers(any()) } throws IllegalStateException("benchmark failed")

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges, SwitchTrigger.CONNECTIVITY).launchIn(backgroundScope)
            runCurrent()

            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }

            stubTiers(gatewayReachable = true, rankedFallbacks = emptyList())
            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(gateway) }
        }

    @Test
    fun theLaneSurvivesAFailureToApplyTheSwitch() =
        runTest {
            stubSwitchDecision(balances = emptyMap())
            coEvery { walletRepository.updateWalletEndpoint(any()) } throws IllegalStateException("prefs failed")

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges, SwitchTrigger.CONNECTIVITY).launchIn(backgroundScope)
            runCurrent()

            edges.emit(Unit)
            runCurrent()
            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 2) { walletRepository.updateWalletEndpoint(gateway) }
        }

    @Test
    fun aSecondForegroundEdgeInsideTheEvaluationIntervalIsNotEvaluated() =
        runTest {
            stubSwitchDecision(balances = emptyMap())

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges).launchIn(backgroundScope)
            runCurrent()

            edges.emit(Unit)
            runCurrent()
            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { synchronizer.getFastestServers(any()) }
        }

    @Test
    fun aForegroundEdgeAfterTheEvaluationIntervalIsEvaluatedAgain() =
        runTest {
            stubSwitchDecision(balances = emptyMap())

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges).launchIn(backgroundScope)
            runCurrent()

            edges.emit(Unit)
            runCurrent()
            timeSource += MINIMUM_EVALUATION_INTERVAL
            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 2) { synchronizer.getFastestServers(any()) }
        }

    @Test
    fun gatewayFailureNeedsTwoConsecutiveNonConnectivityTriggersToReportUnavailability() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            stubGatewayProbe(reachable = false)
            val banner = collectBanner()

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            assertEquals(false, banner.last())

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            assertEquals(true, banner.last())
            coVerify(exactly = 0) { synchronizer.getFastestServers(fallbacks) }
        }

    @Test
    fun healthTriggerOnTheGatewayUsesALightProbeInsteadOfTheBenchmark() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = true, rankedFallbacks = fallbacks)
            stubGatewayProbe(reachable = true)
            val banner = collectBanner()

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))

            assertEquals(false, banner.last())
            coVerify(exactly = 1) { synchronizer.validateServerEndpoint(any(), gateway) }
            coVerify(exactly = 0) { synchronizer.getFastestServers(any()) }
        }

    @Test
    fun healthTriggerOnAClearnetServerStillRanksTheGatewayWithTheBenchmark() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(known.last())
            stubTiers(gatewayReachable = true, rankedFallbacks = fallbacks)

            assertEquals(gateway, repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            coVerify(exactly = 1) { synchronizer.getFastestServers(listOf(gateway)) }
        }

    @Test
    fun foregroundTriggerOnTheGatewayStillUsesTheBenchmark() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = true, rankedFallbacks = fallbacks)

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.FOREGROUND))
            coVerify(exactly = 1) { synchronizer.getFastestServers(listOf(gateway)) }
        }

    @Test
    fun healthTicksRunInTheForegroundAndStopInTheBackground() =
        runTest {
            val ticks = mutableListOf<SwitchTrigger>()
            repository.healthTicks().onEach { ticks += it }.launchIn(backgroundScope)
            runCurrent()

            advanceTimeBy(2.minutes + 1.seconds)
            assertEquals(listOf(SwitchTrigger.HEALTH), ticks)

            foreground.value = false
            runCurrent()
            advanceTimeBy(10.minutes)
            assertEquals(listOf(SwitchTrigger.HEALTH), ticks)

            // Back in the foreground the ticks restart with a full interval.
            foreground.value = true
            runCurrent()
            advanceTimeBy(2.minutes - 1.seconds)
            assertEquals(listOf(SwitchTrigger.HEALTH), ticks)
            advanceTimeBy(2.seconds)
            assertEquals(listOf(SwitchTrigger.HEALTH, SwitchTrigger.HEALTH), ticks)
        }

    @Test
    fun noHealthTicksWhenTheAppStartsInTheBackground() =
        runTest {
            foreground.value = false
            val ticks = mutableListOf<SwitchTrigger>()
            repository.healthTicks().onEach { ticks += it }.launchIn(backgroundScope)
            runCurrent()

            advanceTimeBy(10.minutes)

            assertEquals(emptyList(), ticks)
        }

    @Test
    fun fallbackRecoveryTicksKeepTheirOwnIntervalAndAreNotGatedByTheForeground() =
        runTest {
            fallbackActiveFlow.value = true
            foreground.value = false
            val ticks = mutableListOf<SwitchTrigger>()
            repository.healthTicks().onEach { ticks += it }.launchIn(backgroundScope)
            runCurrent()

            advanceTimeBy(5.minutes + 1.seconds)

            assertEquals(listOf(SwitchTrigger.RECOVERY), ticks)
        }

    @Test
    fun connectivityTriggerReportsGatewayUnavailabilityWithoutConfirmation() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            val banner = collectBanner()

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.CONNECTIVITY))

            assertEquals(true, banner.last())
        }

    @Test
    fun gatewaySuccessBetweenFailuresResetsTheConfirmationCounter() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            val banner = collectBanner()

            stubGatewayProbe(reachable = false)
            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))

            stubGatewayProbe(reachable = true)
            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))

            stubGatewayProbe(reachable = false)
            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))

            assertEquals(false, banner.last())
        }

    @Test
    fun aGatewayTheBenchmarkCannotRankIsStillHealthyWhenItValidates() =
        runTest {
            // Tor circuits can miss the benchmark's fetch thresholds; plain validation decides then.
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            coEvery { synchronizer.validateServerEndpoint(any(), gateway) } returns ServerValidation.Valid
            val banner = collectBanner()

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.CONNECTIVITY))

            assertEquals(false, banner.last())
        }

    @Test
    fun aGatewayThatNeitherRanksNorValidatesIsReportedUnavailable() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            coEvery { synchronizer.validateServerEndpoint(any(), gateway) } returns
                ServerValidation.InValid(IllegalStateException("down"))
            val banner = collectBanner()

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.CONNECTIVITY))

            assertEquals(true, banner.last())
        }

    @Test
    fun onAClearnetServerWithTheGatewayBackTheWalletReturnsToTheGateway() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(known.last())
            stubTiers(gatewayReachable = true, rankedFallbacks = fallbacks)

            assertEquals(gateway, repository.evaluateServerSwitch(SwitchTrigger.RECOVERY))
        }

    /** A wallet on a clearnet server while the OHTTP gateway answers, so a switch back to it is due. */
    private fun stubSwitchDecision(balances: Map<AccountUuid, AccountBalance>?) {
        val wallet = wallet(known.last())
        coEvery { isAutomaticProvider.get() } returns true
        coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet
        persistedWallet.value = wallet
        localBalances.value = balances
        stubTiers(gatewayReachable = true, rankedFallbacks = emptyList())
    }

    private fun TestScope.collectBanner(): List<Boolean> {
        coEvery { isAutomaticProvider.get() } returns true
        val banner = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.ohttpUnavailable.toList(banner)
        }
        return banner
    }

    /** The light probe used by HEALTH ticks on the gateway: plain validation of the one gateway. */
    private fun stubGatewayProbe(reachable: Boolean) {
        coEvery { synchronizer.validateServerEndpoint(any(), gateway) } returns
            if (reachable) ServerValidation.Valid else ServerValidation.InValid(IllegalStateException("down"))
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
