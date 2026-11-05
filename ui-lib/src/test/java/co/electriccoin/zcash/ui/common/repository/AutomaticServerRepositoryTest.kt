@file:Suppress("MaxLineLength")

package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.FastestServersResult
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.type.ServerValidation
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealth
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionHealthOwner
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionOutage
import co.electriccoin.zcash.ui.common.privateconnection.PrivateConnectionTarget
import co.electriccoin.zcash.ui.common.privateconnection.RequestHealthDetection
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.IsOhttpFallbackActiveProvider
import co.electriccoin.zcash.ui.common.provider.IsPublicConnectionProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.NetworkConnectivityProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.ServerSelectionMutex
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
@Suppress("LargeClass")
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
            every { getOhttpEndpoint() } returns gateway
            every { hasOhttpGateway() } returns true
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

    private val ledgerProposalPipeline =
        mockk<LedgerProposalPipeline>(relaxed = true) {
            every { transactionProposal } returns MutableStateFlow<TransactionProposal?>(null)
            every { submitState } returns MutableStateFlow<SubmitProposalState?>(null)
        }

    private val walletRepository = mockk<WalletRepository>(relaxed = true)

    private val timeSource = TestTimeSource()

    /** A relaxed mock returns a bare Object for the suspend Boolean getter; back it with a real value. */
    private var fallbackActive = false
    private val fallbackActiveFlow = MutableStateFlow(false)
    private val isPublicFlow = MutableStateFlow(false)
    private val isPublicProvider =
        mockk<IsPublicConnectionProvider>(relaxed = true).also {
            coEvery { it.get() } returns false
            every { it.observe() } returns isPublicFlow
        }

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

    private val networkConnectivityProvider =
        mockk<NetworkConnectivityProvider>(relaxed = true) {
            every { isOnline() } returns true // Default to online
        }

    private val serverSelectionMutex = ServerSelectionMutex()

    private val repository =
        AutomaticServerRepositoryImpl(
            application = mockk(relaxed = true),
            walletRepository = walletRepository,
            zashiProposalRepository = zashiProposalRepository,
            keystoneProposalRepository = keystoneProposalRepository,
            ledgerProposalPipeline = ledgerProposalPipeline,
            applicationStateProvider = applicationStateProvider,
            synchronizerProvider = synchronizerProvider,
            persistableWalletProvider = persistableWalletProvider,
            lightWalletEndpointProvider = lightWalletEndpointProvider,
            isServerSelectionAutomaticProvider = isAutomaticProvider,
            isOhttpFallbackActiveProvider = fallbackFlag,
            isPublicConnectionProvider = isPublicProvider,
            networkConnectivityProvider = networkConnectivityProvider,
            serverSelectionMutex = serverSelectionMutex,
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
    fun ohttpUnavailableIsClearedWhenAutomaticModeIsTurnedOff() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)

            val emissions = mutableListOf<Boolean>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.ohttpUnavailable.collect { emissions += it }
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.clearUnavailableOnManualMode().collect {}
            }

            assertNull(repository.evaluateServerSwitch())
            assertNull(repository.evaluateServerSwitch())
            assertEquals(listOf(false, true), emissions)

            automaticPreference.value = false
            assertEquals(listOf(false, true, false), emissions)

            // Re-entering automatic mode must not resurrect the outage recorded before leaving it.
            automaticPreference.value = true
            assertEquals(listOf(false, true, false), emissions)
        }

    @Test
    fun publicForegroundAsksTheSdkSwitchPolicyAboutTheClearnetServersOnly() =
        runTest {
            stubPublic(current = fallbacks[0])
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } returns fallbacks[1]

            assertEquals(fallbacks[1], repository.evaluateServerSwitch(SwitchTrigger.FOREGROUND))

            coVerify(exactly = 1) {
                synchronizer.evaluateServerSwitch(
                    current = fallbacks[0],
                    candidates = fallbacks,
                    fetchThreshold = 5.seconds,
                    blocksToFetch = 1
                )
            }
            coVerify(exactly = 0) { synchronizer.getFastestServers(any()) }
            coVerify(exactly = 0) { synchronizer.validateServerEndpoint(any(), any()) }
        }

    /**
     * An SDK built without OHTTP has no gateway: a wallet in Private mode then gets the clearnet server
     * selection of Public mode, never the gateway-unavailable report.
     */
    @Test
    fun withoutAnOhttpGatewayPrivateModeUsesTheClearnetSelection() =
        runTest {
            stubNoGateway(current = fallbacks[0])
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } returns fallbacks[1]
            val banner = collectBanner()

            assertEquals(fallbacks[1], repository.evaluateServerSwitch(SwitchTrigger.FOREGROUND))
            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            runCurrent()

            coVerify(exactly = 1) {
                synchronizer.evaluateServerSwitch(
                    current = fallbacks[0],
                    candidates = fallbacks,
                    fetchThreshold = 5.seconds,
                    blocksToFetch = 1
                )
            }
            coVerify(exactly = 0) { synchronizer.getFastestServers(any()) }
            coVerify(exactly = 0) { synchronizer.validateServerEndpoint(any(), any()) }
            assertTrue(banner.none { it })
        }

    @Test
    fun withoutAnOhttpGatewayAClearnetSwitchIsApplied() =
        runTest {
            stubNoGateway(current = fallbacks[0])
            localBalances.value = emptyMap()
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } returns fallbacks[1]

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges, SwitchTrigger.FOREGROUND).launchIn(backgroundScope)
            runCurrent()
            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(fallbacks[1]) }
            coVerify(exactly = 1) { synchronizer.confirmServerSwitch(fallbacks[1]) }
            assertEquals(false, fallbackActive)
        }

    @Test
    fun publicIgnoresTheGatewayHealthAndRecoveryTriggers() =
        runTest {
            stubPublic(current = fallbacks[0])

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            assertNull(repository.evaluateServerSwitch(SwitchTrigger.RECOVERY))

            coVerify(exactly = 0) { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) }
        }

    @Test
    fun publicLooksForABetterServerWhenTheCurrentOneStopsAnswering() =
        runTest {
            stubPublic(current = fallbacks[0])
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } returns fallbacks[1]

            assertEquals(fallbacks[1], repository.evaluateServerSwitch(SwitchTrigger.CONNECTIVITY))
        }

    /**
     * A clearnet server candidate is dropped when Private mode is chosen while the speed test is running.
     */
    @Test
    fun aClearnetCandidateIsDroppedWhenTheUserMovedToPrivateMeanwhile() =
        runTest {
            stubPublic(current = fallbacks[0])
            localBalances.value = emptyMap()
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } coAnswers {
                coEvery { isPublicProvider.get() } returns false
                fallbacks[1]
            }

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges, SwitchTrigger.FOREGROUND).launchIn(backgroundScope)
            runCurrent()
            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }
            assertEquals(false, fallbackActive)
        }

    /**
     * An automatic server switch candidate is held back until the user's mode/endpoint save completes.
     * If the mode changes while waiting, the candidate is dropped.
     */
    @Test
    fun aSwitchWaitsForTheUsersSaveAndIsDroppedWhenItChangedTheMode() =
        runTest {
            stubPublic(current = fallbacks[0])
            localBalances.value = emptyMap()
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } returns fallbacks[1]

            val saveFinishes = CompletableDeferred<Unit>()
            backgroundScope.launch {
                serverSelectionMutex.withLock {
                    saveFinishes.await()
                    coEvery { isPublicProvider.get() } returns false
                }
            }
            runCurrent()

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges, SwitchTrigger.FOREGROUND).launchIn(backgroundScope)
            runCurrent()
            edges.emit(Unit)
            runCurrent()
            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }

            saveFinishes.complete(Unit)
            runCurrent()

            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }
        }

    @Test
    fun theTransactionSignalFollowsTheProposalStates() =
        runTest {
            val proposal = MutableStateFlow<TransactionProposal?>(null)
            every { zashiProposalRepository.transactionProposal } returns proposal
            val freshRepository =
                AutomaticServerRepositoryImpl(
                    application = mockk(relaxed = true),
                    walletRepository = walletRepository,
                    zashiProposalRepository = zashiProposalRepository,
                    keystoneProposalRepository = keystoneProposalRepository,
                    ledgerProposalPipeline = ledgerProposalPipeline,
                    applicationStateProvider = applicationStateProvider,
                    synchronizerProvider = synchronizerProvider,
                    persistableWalletProvider = persistableWalletProvider,
                    lightWalletEndpointProvider = lightWalletEndpointProvider,
                    isServerSelectionAutomaticProvider = isAutomaticProvider,
                    isOhttpFallbackActiveProvider = fallbackFlag,
                    isPublicConnectionProvider = isPublicProvider,
                    networkConnectivityProvider = networkConnectivityProvider,
                    serverSelectionMutex = serverSelectionMutex,
                    evaluationInterval = EvaluationInterval(MINIMUM_EVALUATION_INTERVAL, timeSource)
                )
            val values = mutableListOf<Boolean>()
            backgroundScope.launch { freshRepository.isTransactionInProgress.toList(values) }
            runCurrent()

            proposal.value = mockk<TransactionProposal>()
            runCurrent()
            proposal.value = null
            runCurrent()

            assertEquals(listOf(false, true, false), values)
        }

    /**
     * A gateway candidate is dropped when Public mode is chosen deliberately while the
     * switch evaluation awaits a local balance snapshot.
     */
    @Test
    fun aGatewayCandidateIsDroppedWhenThePublicChoiceWasDeliberate() =
        runTest {
            stubSwitchDecision(balances = null)
            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges, SwitchTrigger.CONNECTIVITY).launchIn(backgroundScope)
            runCurrent()
            edges.emit(Unit)
            runCurrent()
            coEvery { isPublicProvider.get() } returns true
            fallbackActive = false
            localBalances.value = emptyMap()
            runCurrent()

            coVerify(exactly = 0) { walletRepository.updateWalletEndpoint(any()) }
        }

    @Test
    fun noHealthTicksInADeliberatePublic() =
        runTest {
            stubPublic(current = fallbacks[0])
            val ticks = mutableListOf<SwitchTrigger>()
            repository.healthTicks().onEach { ticks += it }.launchIn(backgroundScope)
            runCurrent()

            advanceTimeBy(10.minutes)

            assertEquals(emptyList(), ticks)
        }

    @Test
    fun publicInTransactionStateSkipsTheSdkSwitchPolicy() =
        runTest {
            stubPublic(current = fallbacks[0])
            every { zashiProposalRepository.transactionProposal } returns MutableStateFlow(mockk<TransactionProposal>())

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.FOREGROUND))

            coVerify(exactly = 0) { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) }
        }

    @Test
    fun publicSwitchIsAppliedAndConfirmedWithoutMarkingTheOhttpFallback() =
        runTest {
            stubPublic(current = fallbacks[0])
            localBalances.value = emptyMap()
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } returns fallbacks[1]

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges, SwitchTrigger.FOREGROUND).launchIn(backgroundScope)
            runCurrent()
            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(fallbacks[1]) }
            coVerify(exactly = 1) { synchronizer.confirmServerSwitch(fallbacks[1]) }
            assertEquals(false, fallbackActive)
        }

    @Test
    fun publicAfterAnOutageReturnsToTheGatewayOnceItAnswers() =
        runTest {
            stubPublic(current = fallbacks[0])
            fallbackActive = true
            stubTiers(gatewayReachable = true, rankedFallbacks = fallbacks)

            assertEquals(gateway, repository.evaluateServerSwitch(SwitchTrigger.RECOVERY))
            coVerify(exactly = 0) { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) }
        }

    @Test
    fun publicAfterAnOutageStaysWhileTheGatewayIsStillDown() =
        runTest {
            stubPublic(current = fallbacks[0])
            fallbackActive = true
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            coEvery { synchronizer.validateServerEndpoint(any(), any()) } returns
                ServerValidation.InValid(IllegalStateException("down"))

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.RECOVERY))
        }

    @Test
    fun aDeliberatePublicNeverProbesTheGatewayOnRecovery() =
        runTest {
            stubPublic(current = fallbacks[0])
            fallbackActive = false

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.RECOVERY))
            coVerify(exactly = 0) { synchronizer.getFastestServers(any()) }
        }

    @Test
    fun aRecoveryTickDoesNotCancelARunningReRankOnTheFallback() =
        runTest {
            stubPublic(current = fallbacks[0])
            fallbackActive = true
            localBalances.value = emptyMap()
            stubGatewayProbe(reachable = false)
            // The public re-rank is slower than the 30 s recovery cadence.
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } coAnswers {
                delay(40.seconds)
                fallbacks[1]
            }

            val triggers = MutableSharedFlow<SwitchTrigger>()
            repository.switchLane(triggers).launchIn(backgroundScope)
            runCurrent()

            triggers.emit(SwitchTrigger.CONNECTIVITY)
            runCurrent()
            advanceTimeBy(30.seconds)
            triggers.emit(SwitchTrigger.RECOVERY)
            runCurrent()
            advanceTimeBy(15.seconds)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(fallbacks[1]) }
            // The tick was skipped, not run: only the CONNECTIVITY evaluation probed the gateway.
            coVerify(exactly = 1) { synchronizer.validateServerEndpoint(any(), gateway) }
        }

    @Test
    fun aRecoveryTickRunsAgainAfterTheReRankEnded() =
        runTest {
            stubPublic(current = fallbacks[0])
            fallbackActive = true
            stubGatewayProbe(reachable = false)

            val triggers = MutableSharedFlow<SwitchTrigger>()
            repository.switchLane(triggers).launchIn(backgroundScope)
            runCurrent()

            triggers.emit(SwitchTrigger.RECOVERY)
            runCurrent()
            triggers.emit(SwitchTrigger.RECOVERY)
            runCurrent()

            coVerify(exactly = 2) { synchronizer.validateServerEndpoint(any(), gateway) }
        }

    @Test
    fun aRecoveryTickDoesNotRestartTheEvaluationInterval() =
        runTest {
            stubPublic(current = fallbacks[0])
            fallbackActive = true
            stubGatewayProbe(reachable = false)
            coEvery { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) } returns null

            val triggers = MutableSharedFlow<SwitchTrigger>()
            repository.switchLane(triggers).launchIn(backgroundScope)
            runCurrent()

            triggers.emit(SwitchTrigger.RECOVERY)
            runCurrent()
            triggers.emit(SwitchTrigger.FOREGROUND)
            runCurrent()

            // The probe did not count as an attempt, so the foreground edge still gets its re-rank.
            coVerify(exactly = 1) { synchronizer.evaluateServerSwitch(any(), any(), any(), any()) }
        }

    @Test
    fun returningToTheGatewayLeavesPublicMode() =
        runTest {
            stubPublic(current = fallbacks[0])
            fallbackActive = true
            localBalances.value = emptyMap()
            stubTiers(gatewayReachable = true, rankedFallbacks = fallbacks)

            val edges = MutableSharedFlow<Unit>()
            repository.observeSwitchCandidates(edges, SwitchTrigger.RECOVERY).launchIn(backgroundScope)
            runCurrent()
            edges.emit(Unit)
            runCurrent()

            coVerify(exactly = 1) { walletRepository.updateWalletEndpoint(gateway) }
            coVerify(exactly = 1) { isPublicProvider.store(false) }
            assertEquals(false, fallbackActive)
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
    fun aPendingLedgerProposalSkipsTheSdkBenchmark() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            every { ledgerProposalPipeline.transactionProposal } returns
                MutableStateFlow<TransactionProposal?>(mockk<TransactionProposal>())

            assertNull(repository.evaluateServerSwitch())
            coVerify(exactly = 0) { synchronizer.getFastestServers(any()) }
        }

    @Test
    fun aPendingLedgerSubmitStateSkipsTheSdkBenchmark() =
        runTest {
            coEvery { isAutomaticProvider.get() } returns true
            every { ledgerProposalPipeline.submitState } returns MutableStateFlow(SubmitProposalState.Submitting)

            assertNull(repository.evaluateServerSwitch())
            coVerify(exactly = 0) { synchronizer.getFastestServers(any()) }
        }

    /**
     * RECOVERY ticks run every 30 s (foreground) or 5 min (background) to check whether the gateway is back.
     * The full fastest-server benchmark can take up to 90 s and would be cancelled by switchLane's mapLatest
     * before completing. RECOVERY evaluations use lightweight probeGateway (one GetLightdInfo per gateway,
     * 20 s cap) instead of the full benchmark.
     */
    @Test
    fun recoveryTriggerUsesProbeGatewayNotFullBenchmark() =
        runTest {
            stubPublic(current = fallbacks[0])
            fallbackActive = true
            coEvery { synchronizer.validateServerEndpoint(any(), gateway) } returns ServerValidation.Valid

            val result = repository.evaluateServerSwitch(SwitchTrigger.RECOVERY)

            // RECOVERY evaluations probe via validateServerEndpoint, not the full getFastestServers benchmark
            assertEquals(gateway, result)
            coVerify(exactly = 0) { synchronizer.getFastestServers(any()) }
            coVerify(exactly = 1) { synchronizer.validateServerEndpoint(any(), gateway) }
        }

    @Test
    fun connectivityTriggerOnFallbackUsesProbeGatewayNotFullBenchmark() =
        runTest {
            stubPublic(current = fallbacks[0])
            fallbackActive = true
            coEvery { synchronizer.validateServerEndpoint(any(), gateway) } returns ServerValidation.Valid

            val result = repository.evaluateServerSwitch(SwitchTrigger.CONNECTIVITY)

            // CONNECTIVITY evaluations on fallback probe via validateServerEndpoint, not getFastestServers
            assertEquals(gateway, result)
            coVerify(exactly = 0) { synchronizer.getFastestServers(any()) }
            coVerify(exactly = 1) { synchronizer.validateServerEndpoint(any(), gateway) }
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
    fun offlineGatewayFailuresDoNotCountTowardUnavailability() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            stubGatewayProbe(reachable = false)
            every { networkConnectivityProvider.isOnline() } returns false
            val banner = collectBanner()

            // Multiple offline failures should not count
            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            assertEquals(false, banner.last())

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            assertEquals(false, banner.last())

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            assertEquals(false, banner.last())

            // Gateway unavailable should never be reported
            coVerify(exactly = 0) { synchronizer.getFastestServers(fallbacks) }
        }

    @Test
    fun offlineFailureFollowedByOnlineFailuresStillNeedsTwoOnlineFailures() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            stubGatewayProbe(reachable = false)
            val banner = collectBanner()

            // First failure while offline (doesn't count)
            every { networkConnectivityProvider.isOnline() } returns false
            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            assertEquals(false, banner.last())

            // Come back online, now failures count
            every { networkConnectivityProvider.isOnline() } returns true

            // First online failure (counts as first)
            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            assertEquals(false, banner.last())

            // Second online failure (counts as second, now report)
            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            assertEquals(true, banner.last())
        }

    @Test
    fun connectivityTriggerAlsoSkipsOfflineFailures() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            every { networkConnectivityProvider.isOnline() } returns false
            val banner = collectBanner()

            // Multiple offline connectivity failures should not count
            assertNull(repository.evaluateServerSwitch(SwitchTrigger.CONNECTIVITY))
            assertEquals(false, banner.last())

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.CONNECTIVITY))
            assertEquals(false, banner.last())
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
    fun fallbackRecoveryTicksRunInTheBackgroundEveryFiveMinutes() =
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
    fun fallbackRecoveryTicksRunEveryThirtySecondsInTheForeground() =
        runTest {
            fallbackActiveFlow.value = true
            foreground.value = true
            val ticks = mutableListOf<SwitchTrigger>()
            repository.healthTicks().onEach { ticks += it }.launchIn(backgroundScope)
            runCurrent()

            advanceTimeBy(60.seconds + 1.seconds)

            assertEquals(listOf(SwitchTrigger.RECOVERY, SwitchTrigger.RECOVERY), ticks)
        }

    @Test
    fun connectivityTriggerAlsoNeedsTwoConsecutiveFailuresToReportUnavailability() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            val banner = collectBanner()

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.CONNECTIVITY))
            assertEquals(false, banner.last())

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

    @Test
    fun anAutomaticWalletOnClearnetMovesToTheGatewayAndReportsItOnceTheOutageIsConfirmed() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returnsMany
                listOf(wallet(known.last()), wallet(gateway))
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            coEvery { synchronizer.validateServerEndpoint(any(), gateway) } returns
                ServerValidation.InValid(IllegalStateException("down"))
            val banner = collectBanner()

            assertEquals(gateway, repository.evaluateServerSwitch(SwitchTrigger.FOREGROUND))
            assertEquals(false, banner.last())

            assertNull(repository.evaluateServerSwitch(SwitchTrigger.HEALTH))
            assertEquals(true, banner.last())
            coVerify(exactly = 0) { synchronizer.getFastestServers(fallbacks) }
        }

    /** The owner of the request-health detection, recorded: what it was asked to watch, and the outage it reports. */
    private class RecordingHealthOwner : PrivateConnectionHealthOwner {
        val outageState = MutableStateFlow<PrivateConnectionOutage?>(null)

        /** Every instruction it received, in order: an owned target, or "pause" / "stop". */
        val calls = mutableListOf<Any>()
        var acknowledged = 0
        var genericErrors = 0

        override val outage: kotlinx.coroutines.flow.Flow<PrivateConnectionOutage?> = outageState

        override suspend fun own(target: PrivateConnectionTarget) {
            calls += target
        }

        override fun pause() {
            calls += "pause"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun acknowledge() {
            acknowledged += 1
        }

        override fun onGenericError() {
            genericErrors += 1
        }
    }

    private val healthOwner = RecordingHealthOwner()
    private val torEnabled = MutableStateFlow<Boolean?>(false)
    private val torProvider = mockk<IsTorEnabledStorageProvider>(relaxed = true) { every { observe() } returns torEnabled }

    /** The repository with the request-health detection on: it owns the outage, the legacy confirmation is off. */
    private fun detectingRepository() =
        AutomaticServerRepositoryImpl(
            application = mockk(relaxed = true),
            walletRepository = walletRepository,
            zashiProposalRepository = zashiProposalRepository,
            keystoneProposalRepository = keystoneProposalRepository,
            ledgerProposalPipeline = ledgerProposalPipeline,
            applicationStateProvider = applicationStateProvider,
            synchronizerProvider = synchronizerProvider,
            persistableWalletProvider = persistableWalletProvider,
            lightWalletEndpointProvider = lightWalletEndpointProvider,
            isServerSelectionAutomaticProvider = isAutomaticProvider,
            isOhttpFallbackActiveProvider = fallbackFlag,
            isPublicConnectionProvider = isPublicProvider,
            networkConnectivityProvider = networkConnectivityProvider,
            serverSelectionMutex = serverSelectionMutex,
            evaluationInterval = EvaluationInterval(MINIMUM_EVALUATION_INTERVAL, timeSource),
            privateConnectionHealth = healthOwner,
            requestHealthDetection = RequestHealthDetection { true },
            isTorEnabledStorageProvider = torProvider,
        )

    @Test
    fun withTheRequestHealthDetectionOnTheLegacyConfirmationNeverReports() =
        runTest {
            val detecting = detectingRepository()
            coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(gateway)
            stubTiers(gatewayReachable = false, rankedFallbacks = fallbacks)
            stubGatewayProbe(reachable = false)
            val banner = mutableListOf<Boolean>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { detecting.ohttpUnavailable.toList(banner) }

            repeat(4) { assertNull(detecting.evaluateServerSwitch(SwitchTrigger.CONNECTIVITY)) }

            assertEquals(false, banner.last())
        }

    @Test
    fun theConfirmedOutageOfTheRequestHealthDetectionRaisesAndClearsTheBanner() =
        runTest {
            val detecting = detectingRepository()
            val banner = mutableListOf<Boolean>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { detecting.ohttpUnavailable.toList(banner) }
            runCurrent()
            assertEquals(false, banner.last())

            healthOwner.outageState.value =
                PrivateConnectionOutage(PrivateConnectionHealth.Episode(1, 3_000_000_000), null)
            runCurrent()
            assertEquals(true, banner.last())

            // Leaving Automatic hides the outage at the source, as for the legacy signal.
            automaticPreference.value = false
            coEvery { isAutomaticProvider.get() } returns false
            runCurrent()
            assertEquals(false, banner.last())
        }

    @Test
    fun acknowledgingTheOutageIsHandedToTheRequestHealthDetection() {
        repository.acknowledgeOhttpUnavailable()
        // The default owner ignores it; the detecting repository forwards it.
        detectingRepository().acknowledgeOhttpUnavailable()
        assertEquals(1, healthOwner.acknowledged)
    }

    @Test
    fun theRequestHealthDetectionWatchesTheGatewayOnlyInPrivateModeInTheForeground() =
        runTest {
            val detecting = detectingRepository()
            val gatewayWallet = wallet(gateway)
            coEvery { persistableWalletProvider.getPersistableWallet() } returns gatewayWallet
            persistedWallet.value = gatewayWallet
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { detecting.watchPrivateConnection().toList() }
            runCurrent()
            assertEquals(PrivateConnectionTarget(gateway, viaTor = false), healthOwner.calls.last())

            torEnabled.value = true
            runCurrent()
            assertEquals(PrivateConnectionTarget(gateway, viaTor = true), healthOwner.calls.last())

            // The background pauses the watch (it keeps what it knows); coming back owns the route again.
            foreground.value = false
            runCurrent()
            assertEquals("pause", healthOwner.calls.last())

            foreground.value = true
            runCurrent()
            assertEquals(PrivateConnectionTarget(gateway, viaTor = true), healthOwner.calls.last())

            // Public ends it for good.
            isPublicFlow.value = true
            runCurrent()
            assertEquals("stop", healthOwner.calls.last())
        }

    @Test
    fun aClearnetServerIsNeverWatchedAsThePrivateRoute() =
        runTest {
            val detecting = detectingRepository()
            val clearnet = wallet(known.last())
            persistedWallet.value = clearnet
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { detecting.watchPrivateConnection().toList() }
            runCurrent()
            assertEquals("stop", healthOwner.calls.last())
        }

    @Test
    fun theBackgroundOfAClearnetServerStopsInsteadOfPausing() =
        runTest {
            val detecting = detectingRepository()
            persistedWallet.value = wallet(known.last())
            foreground.value = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { detecting.watchPrivateConnection().toList() }
            runCurrent()
            // Nothing to remember for a route that is not the Private one.
            assertEquals("stop", healthOwner.calls.last())
        }

    @Test
    fun theGatewayHealthTickIsReplacedByTheRequestHealthDetection() =
        runTest {
            val detecting = detectingRepository()
            val ticks = mutableListOf<SwitchTrigger>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { detecting.healthTicks().toList(ticks) }
            runCurrent()
            advanceTimeBy(30.minutes)
            runCurrent()
            assertEquals(emptyList(), ticks)
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

    private fun stubPublic(current: LightWalletEndpoint) {
        val wallet = wallet(current)
        coEvery { isAutomaticProvider.get() } returns true
        coEvery { isPublicProvider.get() } returns true
        isPublicFlow.value = true
        coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet
        persistedWallet.value = wallet
    }

    /** Private mode on [current] with an SDK built without OHTTP: no gateway, only the clearnet servers. */
    private fun stubNoGateway(current: LightWalletEndpoint) {
        val wallet = wallet(current)
        every { lightWalletEndpointProvider.getEndpoints() } returns fallbacks
        every { lightWalletEndpointProvider.getDefaultEndpoint() } returns fallbacks[0]
        every { lightWalletEndpointProvider.getOhttpEndpoint() } returns null
        every { lightWalletEndpointProvider.hasOhttpGateway() } returns false
        every { lightWalletEndpointProvider.isOhttpGateway(any()) } returns false
        coEvery { isAutomaticProvider.get() } returns true
        coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet
        persistedWallet.value = wallet
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
        coEvery { synchronizer.validateServerEndpoint(any(), gateway) } returns
            if (gatewayReachable) ServerValidation.Valid else ServerValidation.InValid(IllegalStateException("down"))
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
