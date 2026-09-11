package co.electriccoin.zcash.ui.common.viewmodel

import android.app.Application
import androidx.biometric.BiometricManager
import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.api.PreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import co.electriccoin.zcash.spackle.AndroidApiVersion
import co.electriccoin.zcash.ui.common.provider.GetMonotonicTimeProvider
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.fixture.VersionInfoFixture
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Regression coverage for MOB-1447: opening the app before any wallet has been created or
 * restored must never surface the app-access authentication prompt.
 *
 * [WalletViewModel.secretState] starts at [SecretState.LOADING] and only resolves to
 * [SecretState.NONE] or [SecretState.READY] once preferences and configuration finish loading.
 * [AuthenticationViewModel.appAccessAuthenticationResultState] must defer to
 * [AuthenticationUIState.Initial] while that resolution is pending, instead of racing ahead to
 * [AuthenticationUIState.Required] and triggering a biometric prompt that a moment later turns
 * out to have been unnecessary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationViewModelTest {
    private val application = mockk<Application>(relaxed = true)
    private val biometricManager = mockk<BiometricManager>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(AndroidApiVersion)
        every { AndroidApiVersion.isExactlyO } returns false
        every { AndroidApiVersion.isAtLeastR } returns true
        every { AndroidApiVersion.isExactlyP } returns false
        every { AndroidApiVersion.isExactlyQ } returns false
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun loadingSecretStateStaysInitial() =
        runTest {
            val (viewModel, _) = newViewModel(secretState = SecretState.LOADING)

            val states = mutableListOf<AuthenticationUIState>()
            backgroundScope.launch { viewModel.appAccessAuthenticationResultState.collect { states += it } }
            advanceUntilIdle()

            assertEquals(listOf<AuthenticationUIState>(AuthenticationUIState.Initial), states)
        }

    @Test
    fun loadingThenNoneBecomesNotRequired() =
        runTest {
            val (viewModel, secretState) = newViewModel(secretState = SecretState.LOADING)

            backgroundScope.launch { viewModel.appAccessAuthenticationResultState.collect { } }
            advanceUntilIdle()

            secretState.value = SecretState.NONE
            advanceUntilIdle()

            assertEquals(AuthenticationUIState.NotRequired, viewModel.appAccessAuthenticationResultState.value)
        }

    @Test
    fun loadingThenReadyBecomesRequired() =
        runTest {
            val (viewModel, secretState) = newViewModel(secretState = SecretState.LOADING)

            backgroundScope.launch { viewModel.appAccessAuthenticationResultState.collect { } }
            advanceUntilIdle()

            secretState.value = SecretState.READY
            advanceUntilIdle()

            assertEquals(AuthenticationUIState.Required, viewModel.appAccessAuthenticationResultState.value)
        }

    @Test
    fun notRequiredSurvivesLaterWalletCreationInTheSameSession() =
        runTest {
            val (viewModel, secretState) = newViewModel(secretState = SecretState.NONE)

            backgroundScope.launch { viewModel.appAccessAuthenticationResultState.collect { } }
            advanceUntilIdle()
            assertEquals(AuthenticationUIState.NotRequired, viewModel.appAccessAuthenticationResultState.value)

            secretState.value = SecretState.READY
            advanceUntilIdle()

            assertEquals(AuthenticationUIState.NotRequired, viewModel.appAccessAuthenticationResultState.value)
        }

    @Test
    fun disabledAuthenticationPreferenceSkipsPromptEvenWhenReady() =
        runTest {
            val (viewModel, _) =
                newViewModel(secretState = SecretState.READY, isAppAccessAuthenticationPreference = "false")

            backgroundScope.launch { viewModel.appAccessAuthenticationResultState.collect { } }
            advanceUntilIdle()

            assertEquals(AuthenticationUIState.NotRequired, viewModel.appAccessAuthenticationResultState.value)
        }

    @Test
    fun runningUnderTestServiceSkipsPromptEvenWhenReady() =
        runTest {
            val (viewModel, _) =
                newViewModel(secretState = SecretState.READY, isRunningUnderTestService = true)

            backgroundScope.launch { viewModel.appAccessAuthenticationResultState.collect { } }
            advanceUntilIdle()

            assertEquals(AuthenticationUIState.NotRequired, viewModel.appAccessAuthenticationResultState.value)
        }

    @Test
    fun recentBackgroundDoesNotResetAuthenticationState() =
        runTest {
            val (viewModel, _, setMonotonicTimeMillis) = newViewModel(secretState = SecretState.READY)
            viewModel.appAccessAuthentication.value = AuthenticationUIState.Successful
            viewModel.setWelcomeAnimationDisplayed()

            setMonotonicTimeMillis(0L)
            viewModel.onEnteredBackground()

            setMonotonicTimeMillis(5.minutes.inWholeMilliseconds)
            viewModel.runAuthenticationRequiredCheck()

            assertEquals(AuthenticationUIState.Successful, viewModel.appAccessAuthentication.value)
            assertFalse(viewModel.showWelcomeAnimation.value)
        }

    @Test
    fun backgroundTimeoutResetsAuthenticationState() =
        runTest {
            val (viewModel, _, setMonotonicTimeMillis) = newViewModel(secretState = SecretState.READY)
            viewModel.appAccessAuthentication.value = AuthenticationUIState.Successful
            viewModel.setWelcomeAnimationDisplayed()

            setMonotonicTimeMillis(0L)
            viewModel.onEnteredBackground()

            setMonotonicTimeMillis(16.minutes.inWholeMilliseconds)
            viewModel.runAuthenticationRequiredCheck()

            assertEquals(AuthenticationUIState.Initial, viewModel.appAccessAuthentication.value)
            assertTrue(viewModel.showWelcomeAnimation.value)
        }

    @Test
    fun authenticationCheckWithoutRecordedBackgroundTimeIsNoOp() =
        runTest {
            val (viewModel, _, _) = newViewModel(secretState = SecretState.READY)
            viewModel.appAccessAuthentication.value = AuthenticationUIState.Successful

            viewModel.runAuthenticationRequiredCheck()

            assertEquals(AuthenticationUIState.Successful, viewModel.appAccessAuthentication.value)
        }

    private fun newViewModel(
        secretState: SecretState,
        isAppAccessAuthenticationPreference: String? = null,
        isRunningUnderTestService: Boolean = false
    ): Triple<AuthenticationViewModel, MutableStateFlow<SecretState>, (Long) -> Unit> {
        val secretStateFlow = MutableStateFlow(secretState)
        val walletViewModel = mockk<WalletViewModel>()
        every { walletViewModel.secretState } returns secretStateFlow

        val standardPreferenceProvider = mockk<StandardPreferenceProvider>()
        coEvery { standardPreferenceProvider() } returns FakePreferenceProvider(isAppAccessAuthenticationPreference)

        val getVersionInfo = mockk<GetVersionInfoProvider>()
        every { getVersionInfo() } returns
            VersionInfoFixture.new(isRunningUnderTestService = isRunningUnderTestService)

        var monotonicTimeMillis = 0L
        val getMonotonicTime = mockk<GetMonotonicTimeProvider>()
        every { getMonotonicTime() } answers { monotonicTimeMillis }

        val viewModel =
            AuthenticationViewModel(
                application = application,
                biometricManager = biometricManager,
                getMonotonicTime = getMonotonicTime,
                getVersionInfo = getVersionInfo,
                standardPreferenceProvider = standardPreferenceProvider,
                walletViewModel = walletViewModel
            )

        return Triple(viewModel, secretStateFlow) { monotonicTimeMillis = it }
    }
}

/**
 * A hand-written [PreferenceProvider] stand-in, not a MockK proxy: [PreferenceProvider]'s methods
 * take the [PreferenceKey] value class, and MockK's reflection-based call recorder throws on that
 * combination for suspend members (`getString`). [getString] returns [stringValue] for every key;
 * [observe] never emits more than the one initial `null` needed to trigger the first read.
 */
private class FakePreferenceProvider(
    private val stringValue: String?
) : PreferenceProvider {
    override suspend fun hasKey(key: PreferenceKey) = false

    override suspend fun putString(
        key: PreferenceKey,
        value: String?
    ) = Unit

    override suspend fun putStringSet(
        key: PreferenceKey,
        value: Set<String>?
    ) = Unit

    override suspend fun putLong(
        key: PreferenceKey,
        value: Long?
    ) = Unit

    override suspend fun getLong(key: PreferenceKey): Long? = null

    override suspend fun getString(key: PreferenceKey): String? = stringValue

    override suspend fun getStringSet(key: PreferenceKey): Set<String>? = null

    override fun observe(key: PreferenceKey): Flow<String?> = flowOf(null)

    override suspend fun remove(key: PreferenceKey) = Unit

    override suspend fun clearPreferences(): Boolean = true
}
