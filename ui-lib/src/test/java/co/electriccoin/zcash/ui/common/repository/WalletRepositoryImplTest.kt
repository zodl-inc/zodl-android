package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.PersistableWallet
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.configuration.model.map.Configuration
import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.ui.common.datasource.RestoreTimestampDataSource
import co.electriccoin.zcash.ui.common.model.OnboardingState
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.provider.IsIronwoodAnnouncementShownStorageProvider
import co.electriccoin.zcash.ui.common.provider.LightWalletEndpointProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
    @BeforeTest
    fun setUp() {
        mockkObject(PersistableWallet.Companion)
        coEvery { PersistableWallet.new(any(), any(), any(), any()) } returns mockk(relaxed = true)

        mockkObject(Synchronizer.Companion)
        coEvery { Synchronizer.erase(any(), any(), any()) } returns true
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `resolveSecretState combines the onboarding flag and wallet presence`() {
        assertEquals(SecretState.READY, resolveSecretState(true, OnboardingState.READY, true))
        assertEquals(SecretState.NONE, resolveSecretState(true, OnboardingState.READY, false))
        assertEquals(SecretState.NONE, resolveSecretState(true, OnboardingState.NONE, false))
        assertEquals(SecretState.NONE, resolveSecretState(true, OnboardingState.NONE, true))
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
    fun createNewWalletStoresWalletBeforeMarkingOnboardingReady() =
        runTest {
            val (repository, harness) = newRepository(onboardingState = OnboardingState.NONE, initialWallet = null)
            repository.scope = CoroutineScope(StandardTestDispatcher(testScheduler))

            repository.createNewWallet()
            advanceUntilIdle()

            assertEquals(listOf("wallet", "restoring=INITIATING", "onboarding=3"), harness.events)
        }

    @Test
    fun initResetsOnboardingAndErasesSdkDataWhenWalletIsMissing() =
        runTest {
            val (repository, harness) = newRepository(onboardingState = OnboardingState.READY, initialWallet = null)
            repository.scope = CoroutineScope(StandardTestDispatcher(testScheduler))

            repository.init()
            advanceUntilIdle()

            assertEquals("0", harness.fakePrefs.getString(StandardPreferenceKeys.ONBOARDING_STATE.key))
            coVerify(exactly = 1) { Synchronizer.erase(any(), any(), any()) }
        }

    @Test
    fun initKeepsOnboardingWhenWalletIsPresent() =
        runTest {
            val (repository, harness) =
                newRepository(onboardingState = OnboardingState.READY, initialWallet = mockk(relaxed = true))
            repository.scope = CoroutineScope(StandardTestDispatcher(testScheduler))

            repository.init()
            advanceUntilIdle()

            assertEquals("3", harness.fakePrefs.getString(StandardPreferenceKeys.ONBOARDING_STATE.key))
            coVerify(exactly = 0) { Synchronizer.erase(any(), any(), any()) }
        }

    private suspend fun newRepository(
        onboardingState: OnboardingState,
        initialWallet: PersistableWallet?,
    ): Pair<WalletRepositoryImpl, TestHarness> {
        val events = mutableListOf<String>()

        val configurationRepository =
            mockk<ConfigurationRepository> {
                every { configurationFlow } returns MutableStateFlow<Configuration?>(mockk())
            }

        val application = mockk<Application>(relaxed = true)

        val lightWalletEndpointProvider =
            mockk<LightWalletEndpointProvider> {
                every { getDefaultEndpoint() } returns LightWalletEndpoint("zec.rocks", 443, true)
                every { getDecommissionedHosts() } returns emptySet()
            }

        val persistableWalletProvider = FakePersistableWalletProvider(initialWallet, events)

        val synchronizerProvider =
            mockk<SynchronizerProvider>(relaxed = true) {
                every { synchronizer } returns MutableStateFlow<Synchronizer?>(null)
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
            )

        fakePrefs.seed(StandardPreferenceKeys.ONBOARDING_STATE.key, onboardingState.toNumber().toString())

        return repository to TestHarness(events, fakePrefs)
    }

    private data class TestHarness(
        val events: List<String>,
        val fakePrefs: RecordingPreferenceProvider,
    )
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
 * A hand-written [PreferenceProvider] stand-in, not a MockK proxy: [PreferenceProvider]'s methods
 * take the [PreferenceKey] value class, and MockK's reflection-based call recorder throws on that
 * combination for suspend members. [observe] re-emits after every [putString] the way the real
 * `AndroidPreferenceProvider` re-emits on every preference change, so
 * `StandardPreferenceKeys.ONBOARDING_STATE.observe(...)` reacts to writes made through this fake.
 */
internal class RecordingPreferenceProvider(
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
