package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.WalletInitMode
import cash.z.ecc.android.sdk.model.FastestServersResult
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.configuration.model.map.Configuration
import co.electriccoin.zcash.crash.android.GlobalCrashReporter
import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.datasource.RestoreTimestampDataSource
import co.electriccoin.zcash.ui.common.model.OnboardingState
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.IsIronwoodAnnouncementShownStorageProvider
import co.electriccoin.zcash.ui.common.provider.IsPublicConnectionProvider
import co.electriccoin.zcash.ui.common.provider.IsServerSelectionAutomaticProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SdkEncryptedPreferenceRecoveryProvider
import co.electriccoin.zcash.ui.common.provider.ServerSelectionMutex
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.WalletBackupFlagStorageProvider
import co.electriccoin.zcash.ui.common.provider.WalletRestoringStateProvider
import co.electriccoin.zcash.ui.common.viewmodel.SecretState
import co.electriccoin.zcash.ui.preference.StandardPreferenceKeys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression coverage for MOB-1836: the onboarding flag ([OnboardingState]) and the stored wallet
 * live in separate preference files and can diverge when the encrypted store is recreated after
 * provable corruption. [resolveSecretState] and [WalletRepositoryImpl.init]'s self-heal must keep
 * a wallet-less app from ever presenting [SecretState.READY].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletRepositoryImplTest {
    private val scopes = mutableListOf<CoroutineScope>()

    @BeforeTest
    fun setUp() {
        mockkObject(PersistableWallet.Companion)
        coEvery { PersistableWallet.new(any(), any(), any(), any()) } returns mockk(relaxed = true)

        mockkObject(Synchronizer.Companion)
    }

    @AfterTest
    fun tearDown() {
        scopes.forEach { it.cancel() }
        scopes.clear()
        unmockkAll()
    }

    @Test
    fun `resolveSecretState combines the onboarding flag and wallet presence`() {
        assertEquals(SecretState.READY, resolveSecretState(true, OnboardingState.READY, true))
        assertEquals(SecretState.NONE, resolveSecretState(true, OnboardingState.READY, false))
        assertEquals(SecretState.NONE, resolveSecretState(true, OnboardingState.NONE, false))
        assertEquals(SecretState.NONE, resolveSecretState(true, OnboardingState.NONE, true))
        assertEquals(SecretState.NONE, resolveSecretState(true, OnboardingState.NEEDS_WARN, true))
        assertEquals(SecretState.NONE, resolveSecretState(true, OnboardingState.NEEDS_BACKUP, true))
        assertEquals(SecretState.LOADING, resolveSecretState(false, OnboardingState.READY, true))
    }

    @Test
    fun secretStateFlowResolvesNoneWhenWalletIsMissing() =
        runTest {
            val (repository, _) = newRepository(onboardingState = OnboardingState.READY, initialWallet = null)

            assertEquals(SecretState.NONE, repository.secretState.first { it != SecretState.LOADING })
        }

    @Test
    fun secretStateFlowResolvesReadyWhenWalletIsPresent() =
        runTest {
            val (repository, _) =
                newRepository(onboardingState = OnboardingState.READY, initialWallet = mockk(relaxed = true))

            assertEquals(SecretState.READY, repository.secretState.first { it != SecretState.LOADING })
        }

    @Test
    fun secretStateDoesNotWaitOnTheWalletStoreWhenFlagIsNotReady() =
        runTest {
            val neverEmittingWalletProvider =
                mockk<PersistableWalletProvider> {
                    every { persistableWallet } returns MutableSharedFlow()
                }

            val (repository, _) =
                newRepository(
                    onboardingState = OnboardingState.NONE,
                    initialWallet = null,
                    persistableWalletProviderOverride = neverEmittingWalletProvider,
                )

            assertEquals(SecretState.NONE, repository.secretState.first { it != SecretState.LOADING })
        }

    @Test
    fun createNewWalletStoresWalletBeforeMarkingOnboardingReady() =
        runTest {
            val (repository, harness) = newRepository(onboardingState = OnboardingState.NONE, initialWallet = null)
            repository.useTestScope(testScheduler)

            repository.createNewWallet()
            advanceUntilIdle()

            assertEquals(listOf("wallet", "restoring=INITIATING", "onboarding=3"), harness.events)
        }

    @Test
    fun initResetsOnboardingAndErasesSdkDataWhenWalletIsMissing() =
        runTest {
            val (repository, harness) = newRepository(onboardingState = OnboardingState.READY, initialWallet = null)
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(listOf("onboarding=0", "sdkRepair", "erase"), harness.events)
        }

    @Test
    fun initKeepsOnboardingWhenWalletIsPresent() =
        runTest {
            val (repository, harness) =
                newRepository(onboardingState = OnboardingState.READY, initialWallet = mockk(relaxed = true))
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals("3", harness.fakePrefs.getString(StandardPreferenceKeys.ONBOARDING_STATE.key))
            coVerify(exactly = 0) { Synchronizer.erase(any(), any(), any()) }
        }

    @Test
    fun initMovesAnAutomaticWalletOnClearnetToTheGateway() =
        runTest {
            val (repository, harness) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = walletOn(ZEC_ROCKS),
                    isAutomaticPreference = true,
                )
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(GATEWAY, harness.walletProvider.getPersistableWallet()?.endpoint)
            assertEquals(listOf("wallet"), harness.events)
        }

    @Test
    fun initKeepsAnAutomaticWalletOnClearnetWhenTheSdkHasNoOhttpGateway() =
        runTest {
            val (repository, harness) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = walletOn(ZEC_ROCKS),
                    isAutomaticPreference = true,
                    hasOhttpGateway = false,
                )
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(ZEC_ROCKS, harness.walletProvider.getPersistableWallet()?.endpoint)
            assertEquals(emptyList(), harness.events)
        }

    @Test
    fun initMovesAnUpgradedWalletWithoutAModePreferenceOnABundledClearnetServerToTheGateway() =
        runTest {
            val (repository, harness) =
                newRepository(onboardingState = OnboardingState.READY, initialWallet = walletOn(ZEC_ROCKS))
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(GATEWAY, harness.walletProvider.getPersistableWallet()?.endpoint)
        }

    @Test
    fun initTurnsAManualGatewaySelectionIntoPrivate() =
        runTest {
            val (repository, harness) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = walletOn(GATEWAY),
                    isAutomaticPreference = false,
                )
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            coVerify(exactly = 1) { harness.isAutomaticProvider.store(true) }
            coVerify(exactly = 1) { harness.isPublicProvider.store(false) }
            assertEquals(GATEWAY, harness.walletProvider.getPersistableWallet()?.endpoint)
        }

    @Test
    fun initMovesAPublicWalletOffADecommissionedHostToAnotherPublicServer() =
        runTest {
            val (repository, harness) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = walletOn(DECOMMISSIONED),
                    isAutomaticPreference = true,
                    isPublic = true,
                    decommissionedHosts = setOf(DECOMMISSIONED.host),
                )
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(ZEC_ROCKS, harness.walletProvider.getPersistableWallet()?.endpoint)
        }

    /**
     * When moving a non-Public wallet off a decommissioned host, switch to the gateway
     * in Automatic/Private mode, not Manual.
     */
    @Test
    fun initMovesANonPublicWalletOffADecommissionedHostToTheGateway() =
        runTest {
            val (repository, harness) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = walletOn(DECOMMISSIONED),
                    isAutomaticPreference = false,
                    decommissionedHosts = setOf(DECOMMISSIONED.host),
                )
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(GATEWAY, harness.walletProvider.getPersistableWallet()?.endpoint)
            coVerify(exactly = 1) { harness.isAutomaticProvider.store(true) }
            coVerify(exactly = 1) { harness.isPublicProvider.store(false) }
        }

    @Test
    fun initKeepsAManualClearnetSelection() =
        runTest {
            val (repository, harness) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = walletOn(ZEC_ROCKS),
                    isAutomaticPreference = false,
                )
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(ZEC_ROCKS, harness.walletProvider.getPersistableWallet()?.endpoint)
            assertEquals(emptyList(), harness.events)
        }

    @Test
    fun initKeepsACustomServerWithoutAModePreference() =
        runTest {
            val custom = LightWalletEndpoint("lwd.example.org", 443, true)
            val (repository, harness) =
                newRepository(onboardingState = OnboardingState.READY, initialWallet = walletOn(custom))
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(custom, harness.walletProvider.getPersistableWallet()?.endpoint)
        }

    @Test
    fun initSkipsRepairAndEraseWhenFlagIsNotReady() =
        runTest {
            val (repository, harness) = newRepository(onboardingState = OnboardingState.NONE, initialWallet = null)
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(emptyList<String>(), harness.events)
            coVerify(exactly = 0) { Synchronizer.erase(any(), any(), any()) }
        }

    @Test
    fun initSkipsEraseWhenAWalletIsStoredWhileSelfHealing() =
        runTest {
            val (repository, harness) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = null,
                    persistableWalletProviderOverride =
                        WalletStoredAfterFirstReadProvider(mockk(relaxed = true)),
                )
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(listOf("onboarding=0", "sdkRepair"), harness.events)
            coVerify(exactly = 0) { Synchronizer.erase(any(), any(), any()) }
        }

    /**
     * The fail-safe hinge of the whole self-heal: "no wallet" must only ever mean "the store said
     * there is no wallet", never "reading the store failed". Nothing in
     * `PersistableWalletProviderImpl.getPersistableWallet` catches, so a read failure propagates
     * out of [WalletRepositoryImpl.init] today — this pins that against a future well-meaning
     * `runCatching` turning an unreadable store into a reset to onboarding and an erase.
     */
    @Test
    fun initNeitherResetsOnboardingNorErasesWhenTheWalletReadFails() =
        runTest {
            val failingWalletProvider =
                mockk<PersistableWalletProvider> {
                    every { persistableWallet } returns MutableSharedFlow()
                }
            coEvery { failingWalletProvider.getPersistableWallet() } throws
                IllegalStateException("encrypted preferences unreadable")

            val (repository, harness) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = null,
                    persistableWalletProviderOverride = failingWalletProvider,
                )
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(emptyList<String>(), harness.events)
            assertEquals("3", harness.fakePrefs.getString(StandardPreferenceKeys.ONBOARDING_STATE.key))
            coVerify(exactly = 0) { Synchronizer.erase(any(), any(), any()) }
        }

    /**
     * "The erase failed and we carried on anyway" is the half-reset state MOB-1836 was reported
     * for, so it has to reach crash reporting rather than only a log line on the user's device.
     */
    @Test
    fun initReportsAFailedEraseToCrashReporting() =
        runTest {
            mockkObject(GlobalCrashReporter)
            every { GlobalCrashReporter.reportCaughtException(any()) } returns Unit

            val (repository, harness) = newRepository(onboardingState = OnboardingState.READY, initialWallet = null)
            val eraseFailure = RuntimeException("erasing the SDK databases failed")
            coEvery { Synchronizer.erase(any(), any(), any()) } throws eraseFailure
            repository.useTestScope(testScheduler)

            repository.init()
            advanceUntilIdle()

            assertEquals(listOf("onboarding=0", "sdkRepair"), harness.events)
            verify(exactly = 1) { GlobalCrashReporter.reportCaughtException(eraseFailure) }
        }

    /**
     * ZIP #10 rule 3: in Private mode (the default), the fastest-server benchmark measures only
     * OHTTP gateways. Clearnet servers are not contacted before the user chooses Public mode.
     */
    @Test
    fun fastestEndpointsBenchmarksOnlyGatewaysInPrivateMode() =
        runTest {
            val testedEndpoints = mutableListOf<List<LightWalletEndpoint>>()
            val synchronizer =
                mockk<Synchronizer> {
                    coEvery { getFastestServers(any()) } answers {
                        testedEndpoints += firstArg<List<LightWalletEndpoint>>()
                        flowOf(FastestServersResult.Done(listOf(GATEWAY)))
                    }
                }

            val (repository, _) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = walletOn(GATEWAY),
                    isPublic = false,
                    synchronizer = synchronizer,
                )
            repository.useTestScope(testScheduler)

            repository.fastestEndpoints.first { !it.isLoading }
            advanceUntilIdle()

            assertEquals(1, testedEndpoints.size)
            assertEquals(listOf(GATEWAY), testedEndpoints[0])
        }

    /**
     * ZIP #10 rule 3: in Public mode, the fastest-server benchmark measures only clearnet servers.
     * OHTTP gateways are not offered in Manual server lists when Public is active.
     */
    @Test
    fun fastestEndpointsBenchmarksOnlyPublicServersInPublicMode() =
        runTest {
            val testedEndpoints = mutableListOf<List<LightWalletEndpoint>>()
            val synchronizer =
                mockk<Synchronizer> {
                    coEvery { getFastestServers(any()) } answers {
                        testedEndpoints += firstArg<List<LightWalletEndpoint>>()
                        flowOf(FastestServersResult.Done(listOf(ZEC_ROCKS)))
                    }
                }

            val (repository, _) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = walletOn(ZEC_ROCKS),
                    isPublic = true,
                    synchronizer = synchronizer,
                )
            repository.useTestScope(testScheduler)

            repository.fastestEndpoints.first { !it.isLoading }
            advanceUntilIdle()

            assertEquals(1, testedEndpoints.size)
            assertEquals(listOf(ZEC_ROCKS), testedEndpoints[0])
        }

    /**
     * ZIP #10 rule 3: when the user saves Public mode, the mode flag changes and the
     * fastest-server benchmark re-triggers with the new endpoint set (now public servers only).
     */
    @Test
    fun fastestEndpointsReTriggersWhenModeChanges() =
        runTest {
            // The benchmark runs on the repository's own sharing scope (Dispatchers.IO), so the
            // test waits on the flow itself rather than on the test scheduler.
            val testedEndpoints = CopyOnWriteArrayList<List<LightWalletEndpoint>>()
            val synchronizer =
                mockk<Synchronizer> {
                    coEvery { getFastestServers(any()) } answers {
                        val endpoints = firstArg<List<LightWalletEndpoint>>()
                        testedEndpoints += endpoints
                        flowOf(FastestServersResult.Done(endpoints))
                    }
                }

            val isPublicProvider =
                mockk<IsPublicConnectionProvider>(relaxed = true) {
                    val flow = MutableStateFlow(false)
                    every { observe() } returns flow
                    coEvery { get() } answers { flow.value }
                    coEvery { store(any()) } coAnswers {
                        flow.value = firstArg()
                    }
                }

            val (repository, _) =
                newRepository(
                    onboardingState = OnboardingState.READY,
                    initialWallet = walletOn(GATEWAY),
                    isPublic = false,
                    synchronizer = synchronizer,
                    isPublicProviderOverride = isPublicProvider,
                )
            repository.useTestScope(testScheduler)

            // Private mode: only the gateway is measured.
            repository.fastestEndpoints.first { !it.isLoading }
            assertEquals(listOf(listOf(GATEWAY)), testedEndpoints.toList())

            // Saving Public re-runs the benchmark over the public servers only.
            isPublicProvider.store(true)
            repository.fastestEndpoints.first { !it.isLoading && testedEndpoints.size >= 2 }
            assertEquals(2, testedEndpoints.size)
            assertEquals(listOf(ZEC_ROCKS), testedEndpoints[1])
        }

    /**
     * Replaces the repository's production scope with one driven by the test's scheduler, and
     * registers both for cancellation in [tearDown] so no test leaves a live scope behind.
     */
    private fun WalletRepositoryImpl.useTestScope(scheduler: TestCoroutineScheduler) {
        scope = CoroutineScope(StandardTestDispatcher(scheduler)).also { scopes += it }
    }

    private suspend fun newRepository(
        onboardingState: OnboardingState,
        initialWallet: PersistableWallet?,
        persistableWalletProviderOverride: PersistableWalletProvider? = null,
        isAutomaticPreference: Boolean? = null,
        isPublic: Boolean = false,
        decommissionedHosts: Set<String> = emptySet(),
        synchronizer: Synchronizer? = null,
        isPublicProviderOverride: IsPublicConnectionProvider? = null,
        hasOhttpGateway: Boolean = true,
    ): Pair<WalletRepositoryImpl, TestHarness> {
        val events = mutableListOf<String>()

        coEvery { Synchronizer.erase(any(), any(), any()) } coAnswers {
            events += "erase"
            true
        }

        val configurationRepository =
            mockk<ConfigurationRepository> {
                every { configurationFlow } returns MutableStateFlow<Configuration?>(mockk())
            }

        val application = mockk<Application>(relaxed = true)

        val lightWalletEndpointProvider =
            mockk<LightWalletEndpointProvider> {
                // An SDK built without OHTTP has no gateway: the default is then the first clearnet server.
                val gateway = GATEWAY.takeIf { hasOhttpGateway }
                every { getDefaultEndpoint() } returns (gateway ?: ZEC_ROCKS)
                every { getOhttpEndpoint() } returns gateway
                every { hasOhttpGateway() } returns hasOhttpGateway
                every { getEndpoints() } returns listOfNotNull(gateway, ZEC_ROCKS)
                every { isOhttpGateway(any()) } answers {
                    gateway != null && firstArg<LightWalletEndpoint>() == gateway
                }
                every { getFallbackEndpoints() } returns listOf(ZEC_ROCKS)
                every { getDecommissionedHosts() } returns decommissionedHosts
            }

        val isServerSelectionAutomaticProvider =
            mockk<IsServerSelectionAutomaticProvider>(relaxed = true) {
                coEvery { get() } returns isAutomaticPreference
            }

        val isPublicConnectionProvider =
            isPublicProviderOverride ?: mockk<IsPublicConnectionProvider>(relaxed = true).also {
                val flow = MutableStateFlow(isPublic)
                every { it.observe() } returns flow
                coEvery { it.get() } returns isPublic
            }

        val persistableWalletProvider =
            persistableWalletProviderOverride ?: FakePersistableWalletProvider(initialWallet, events)

        val fakeSynchronizer = synchronizer
        val synchronizerProvider =
            mockk<SynchronizerProvider>(relaxed = true) {
                every { this@mockk.synchronizer } returns MutableStateFlow(fakeSynchronizer)
            }

        val fakePrefs = RecordingPreferenceProvider(events)
        val standardPreferenceProvider = mockk<StandardPreferenceProvider>()
        coEvery { standardPreferenceProvider() } returns fakePrefs

        val restoreTimestampDataSource = mockk<RestoreTimestampDataSource>(relaxed = true)
        val walletBackupFlagStorageProvider = mockk<WalletBackupFlagStorageProvider>(relaxed = true)

        val walletRestoringStateProvider = mockk<WalletRestoringStateProvider>(relaxed = true)
        coEvery { walletRestoringStateProvider.store(any()) } coAnswers {
            events += "restoring=${firstArg<WalletRestoringState>()}"
        }

        val isIronwoodAnnouncementShownStorageProvider =
            mockk<IsIronwoodAnnouncementShownStorageProvider> {
                every { observe() } returns flowOf(null)
            }

        val sdkEncryptedPreferenceRecoveryProvider = FakeSdkEncryptedPreferenceRecoveryProvider(events)

        val repository =
            WalletRepositoryImpl(
                configurationRepository = configurationRepository,
                application = application,
                lightWalletEndpointProvider = lightWalletEndpointProvider,
                persistableWalletProvider = persistableWalletProvider,
                synchronizerProvider = synchronizerProvider,
                standardPreferenceProvider = standardPreferenceProvider,
                restoreTimestampDataSource = restoreTimestampDataSource,
                walletRestoringStateProvider = walletRestoringStateProvider,
                walletBackupFlagStorageProvider = walletBackupFlagStorageProvider,
                isIronwoodAnnouncementShownStorageProvider = isIronwoodAnnouncementShownStorageProvider,
                sdkEncryptedPreferenceRecoveryProvider = sdkEncryptedPreferenceRecoveryProvider,
                isServerSelectionAutomaticProvider = isServerSelectionAutomaticProvider,
                isPublicConnectionProvider = isPublicConnectionProvider,
                serverSelectionMutex = ServerSelectionMutex(),
            )

        fakePrefs.seed(StandardPreferenceKeys.ONBOARDING_STATE.key, onboardingState.toNumber().toString())

        scopes += repository.scope
        scopes += repository.sharingScope

        return repository to
            TestHarness(
                events,
                fakePrefs,
                persistableWalletProvider,
                isServerSelectionAutomaticProvider,
                isPublicConnectionProvider,
            )
    }

    private fun walletOn(endpoint: LightWalletEndpoint) =
        PersistableWallet(
            network = ZcashNetwork.Mainnet,
            endpoint = endpoint,
            birthday = null,
            seedPhrase = mockk(relaxed = true),
            walletInitMode = WalletInitMode.ExistingWallet,
        )

    private data class TestHarness(
        val events: List<String>,
        val fakePrefs: RecordingPreferenceProvider,
        val walletProvider: PersistableWalletProvider,
        val isAutomaticProvider: IsServerSelectionAutomaticProvider,
        val isPublicProvider: IsPublicConnectionProvider,
    )
}

private val GATEWAY = LightWalletEndpoint("ohttp-gateway.example", 443, true)
private val ZEC_ROCKS = LightWalletEndpoint("zec.rocks", 443, true)
private val DECOMMISSIONED = LightWalletEndpoint("decommissioned.example.com", 443, true)

/**
 * Records `"sdkRepair"` into the shared events list, so tests can assert where the SDK secret
 * store repair lands relative to the onboarding-flag write and the SDK data erase.
 */
private class FakeSdkEncryptedPreferenceRecoveryProvider(
    private val events: MutableList<String>
) : SdkEncryptedPreferenceRecoveryProvider {
    override suspend fun ensureReadable() {
        events += "sdkRepair"
    }
}

/**
 * A hand-written [PersistableWalletProvider] stand-in: MockK cannot mock a suspend member whose
 * parameter is the [PersistableWallet] value class in this codebase's setup, so [store] is
 * implemented directly, backed by a [MutableStateFlow] the way the real
 * `PersistableWalletProviderImpl` observes its storage provider.
 */
private class FakePersistableWalletProvider(
    initial: PersistableWallet?,
    private val events: MutableList<String>
) : PersistableWalletProvider {
    private val walletFlow = MutableStateFlow(initial)

    override val persistableWallet: Flow<PersistableWallet?> = walletFlow

    override suspend fun store(persistableWallet: PersistableWallet) {
        walletFlow.value = persistableWallet
        events += "wallet"
    }

    override suspend fun getPersistableWallet(): PersistableWallet? = walletFlow.value

    override suspend fun requirePersistableWallet(): PersistableWallet = checkNotNull(walletFlow.value)
}

/**
 * Reports no wallet on the first read and a stored wallet on every read after it, which is the
 * shape of the self-heal race: [WalletRepositoryImpl.init] sees an empty encrypted store, and by
 * the time the erase would run a wallet has been stored under it. The erase must not happen.
 */
private class WalletStoredAfterFirstReadProvider(
    private val storedLater: PersistableWallet
) : PersistableWalletProvider {
    private val walletFlow = MutableStateFlow<PersistableWallet?>(null)

    override val persistableWallet: Flow<PersistableWallet?> = walletFlow

    override suspend fun store(persistableWallet: PersistableWallet) {
        walletFlow.value = persistableWallet
    }

    override suspend fun getPersistableWallet(): PersistableWallet? =
        walletFlow.value.also { walletFlow.value = storedLater }

    override suspend fun requirePersistableWallet(): PersistableWallet = checkNotNull(walletFlow.value)
}

/**
 * A hand-written [PreferenceProvider] stand-in, not a MockK proxy: [PreferenceProvider]'s methods
 * take the [PreferenceKey] value class, and MockK's reflection-based call recorder throws on that
 * combination for suspend members. [observe] re-emits after every [putString] the way the real
 * `AndroidPreferenceProvider` re-emits on every preference change, so
 * `StandardPreferenceKeys.ONBOARDING_STATE.observe(...)` reacts to writes made through this fake.
 */
private class RecordingPreferenceProvider(
    private val events: MutableList<String>
) : PreferenceProvider {
    private val values = mutableMapOf<String, String?>()
    private val changes =
        MutableSharedFlow<Unit>(replay = 1).apply {
            tryEmit(Unit)
        }

    /**
     * Sets [key]'s stored value directly, without recording it as an event or notifying
     * [observe] collectors — test setup, not an action under test.
     */
    fun seed(
        key: PreferenceKey,
        value: String?
    ) {
        values[key.key] = value
    }

    override suspend fun hasKey(key: PreferenceKey) = values.containsKey(key.key)

    override suspend fun putString(
        key: PreferenceKey,
        value: String?
    ) {
        values[key.key] = value
        if (key == StandardPreferenceKeys.ONBOARDING_STATE.key) {
            events += "onboarding=$value"
        }
        changes.emit(Unit)
    }

    override suspend fun putStringSet(
        key: PreferenceKey,
        value: Set<String>?
    ) = Unit

    override suspend fun putLong(
        key: PreferenceKey,
        value: Long?
    ) = Unit

    override suspend fun getLong(key: PreferenceKey): Long? = null

    override suspend fun getString(key: PreferenceKey): String? = values[key.key]

    override suspend fun getStringSet(key: PreferenceKey): Set<String>? = null

    override fun observe(key: PreferenceKey): Flow<String?> = changes.map { getString(key) }

    override suspend fun remove(key: PreferenceKey) {
        values.remove(key.key)
    }

    override suspend fun clearPreferences(): Boolean {
        values.clear()
        return true
    }
}
