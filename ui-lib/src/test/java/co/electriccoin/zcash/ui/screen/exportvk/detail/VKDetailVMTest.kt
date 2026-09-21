package co.electriccoin.zcash.ui.screen.exportvk.detail

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.common.usecase.GetVKUseCase
import co.electriccoin.zcash.ui.common.usecase.ShareQRUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The viewing key export screen (MOB-1883): the content starts hidden behind a placeholder of a fixed length
 * and only reveals once biometrics pass, Hide never prompts but a second Reveal does, the QR tab opens first,
 * Share only works once revealed and always sends the key string together with its QR PNG whichever tab is
 * open, and the copy follows the exported key type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VKDetailVMTest {
    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun hiddenContentNeverCarriesTheRealKey() =
        runTest(dispatcher) {
            val vm = startedVm()

            val qrContent = assertIs<VKContentState.Qr>(requireNotNull(vm.state.value).content)
            assertFalse(qrContent.isRevealed)
            assertFalse(qrContent.data.contains(KEY))
            assertEquals(stringRes(R.string.exportViewingKey_reveal), qrContent.hiddenLabel)
            assertFalse(requireNotNull(vm.state.value).secondaryButton.isEnabled)

            switchToKeyTab(vm)

            val keyContent = assertIs<VKContentState.Key>(requireNotNull(vm.state.value).content)
            assertFalse(keyContent.isRevealed)
            assertFalse(keyContent.data.contains(KEY))
            assertEquals(stringRes(R.string.exportViewingKey_reveal), keyContent.hiddenLabel)
        }

    @Test
    fun revealPromptsBiometricsOnceAndShowsTheKey() =
        runTest(dispatcher) {
            val biometrics = biometrics(succeeds = true)
            val vm = startedVm(biometricRepository = biometrics)

            reveal(vm)

            val state = requireNotNull(vm.state.value)
            coVerify(exactly = 1) { biometrics.requestBiometrics(any()) }
            val content = assertIs<VKContentState.Qr>(state.content)
            assertTrue(content.isRevealed)
            assertEquals(KEY, content.data)
            assertTrue(state.secondaryButton.isEnabled)
            assertEquals(stringRes(R.string.exportViewingKey_hide), state.primaryButton.text)
            assertEquals(R.drawable.ic_seed_hide, state.primaryButton.icon)
        }

    @Test
    fun biometricFailureKeepsTheKeyHidden() =
        runTest(dispatcher) {
            val vm = startedVm(biometricRepository = biometrics(succeeds = false))

            reveal(vm)

            assertFalse(assertIs<VKContentState.Qr>(requireNotNull(vm.state.value).content).isRevealed)
            assertFalse(requireNotNull(vm.state.value).secondaryButton.isEnabled)
        }

    @Test
    fun biometricCancellationKeepsTheKeyHidden() =
        runTest(dispatcher) {
            val biometrics =
                mockk<BiometricRepository> {
                    coEvery { requestBiometrics(any()) } throws BiometricsCancelledException()
                }
            val vm = startedVm(biometricRepository = biometrics)

            reveal(vm)

            assertFalse(assertIs<VKContentState.Qr>(requireNotNull(vm.state.value).content).isRevealed)
        }

    @Test
    fun hideDoesNotPromptAgain() =
        runTest(dispatcher) {
            val biometrics = biometrics(succeeds = true)
            val vm = startedVm(biometricRepository = biometrics)
            reveal(vm)

            requireNotNull(vm.state.value).primaryButton.onClick()
            advanceUntilIdle()

            coVerify(exactly = 1) { biometrics.requestBiometrics(any()) }
            val state = requireNotNull(vm.state.value)
            assertFalse(assertIs<VKContentState.Qr>(state.content).isRevealed)
            assertEquals(stringRes(R.string.exportViewingKey_reveal), state.primaryButton.text)
            assertEquals(R.drawable.ic_seed_show, state.primaryButton.icon)
        }

    @Test
    fun revealAfterHidePromptsBiometricsAgain() =
        runTest(dispatcher) {
            val biometrics = biometrics(succeeds = true)
            val vm = startedVm(biometricRepository = biometrics)
            reveal(vm)
            requireNotNull(vm.state.value).primaryButton.onClick()
            advanceUntilIdle()

            reveal(vm)

            coVerify(exactly = 2) { biometrics.requestBiometrics(any()) }
            val content = assertIs<VKContentState.Qr>(requireNotNull(vm.state.value).content)
            assertTrue(content.isRevealed)
            assertEquals(KEY, content.data)
        }

    @Test
    fun tabsAreQrThenKeyWithQrSelectedFirst() =
        runTest(dispatcher) {
            val vm = startedVm()

            val state = requireNotNull(vm.state.value)
            assertEquals(
                listOf(
                    stringRes(R.string.exportViewingKey_tab_qr),
                    stringRes(R.string.exportViewingKey_tab_key)
                ),
                state.tabs.map { it.text }
            )
            assertEquals(listOf(true, false), state.tabs.map { it.isSelected })
        }

    @Test
    fun hiddenPlaceholderLengthDoesNotDependOnTheKey() =
        runTest(dispatcher) {
            val short = startedVm(key = KEY)
            val long = startedVm(key = KEY + "x".repeat(LONGER_KEY_PADDING))

            val shortHidden = assertIs<VKContentState.Qr>(requireNotNull(short.state.value).content).data
            val longHidden = assertIs<VKContentState.Qr>(requireNotNull(long.state.value).content).data
            assertEquals(shortHidden.length, longHidden.length)
            assertEquals(shortHidden, longHidden)
            assertTrue(shortHidden.all { it == '*' })
        }

    @Test
    fun tabSwitchKeepsTheRevealStateAndSwapsTheDisclaimer() =
        runTest(dispatcher) {
            val vm = startedVm(type = VKType.FULL, biometricRepository = biometrics(succeeds = true))
            assertEquals(
                stringRes(R.string.exportViewingKey_disclaimer_qr_full),
                requireNotNull(vm.state.value).disclaimer
            )
            reveal(vm)

            switchToKeyTab(vm)

            val state = requireNotNull(vm.state.value)
            assertEquals(1, state.tabs.indexOfFirst { it.isSelected })
            val content = assertIs<VKContentState.Key>(state.content)
            assertTrue(content.isRevealed)
            assertEquals(KEY, content.data)
            assertEquals(stringRes(R.string.exportViewingKey_disclaimer_key_full), state.disclaimer)
        }

    @Test
    fun shareIsIgnoredWhileHidden() =
        runTest(dispatcher) {
            val shareQR = mockk<ShareQRUseCase>(relaxed = true)
            val vm = startedVm(shareQR = shareQR)

            requireNotNull(vm.state.value).secondaryButton.onClick()
            advanceUntilIdle()

            coVerify(exactly = 0) { shareQR(any(), any(), any(), any(), any()) }
        }

    @Test
    fun shareSendsTheKeyStringWithItsQrPng() =
        runTest(dispatcher) {
            val shareQR = mockk<ShareQRUseCase>(relaxed = true)
            val vm = startedVm(shareQR = shareQR, biometricRepository = biometrics(true))
            reveal(vm)

            requireNotNull(vm.state.value).secondaryButton.onClick()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                shareQR(
                    qrData = KEY,
                    shareText = stringRes(KEY),
                    sharePickerText = stringRes(R.string.exportViewingKey_share_picker_title),
                    filenamePrefix = "zodl_viewing_key_qr_",
                    centerIcon = null
                )
            }
        }

    @Test
    fun shareOnTheKeyTabSendsTheSamePayload() =
        runTest(dispatcher) {
            val shareQR = mockk<ShareQRUseCase>(relaxed = true)
            val vm = startedVm(shareQR = shareQR, biometricRepository = biometrics(true))
            reveal(vm)
            switchToKeyTab(vm)

            requireNotNull(vm.state.value).secondaryButton.onClick()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                shareQR(
                    qrData = KEY,
                    shareText = stringRes(KEY),
                    sharePickerText = stringRes(R.string.exportViewingKey_share_picker_title),
                    filenamePrefix = "zodl_viewing_key_qr_",
                    centerIcon = null
                )
            }
        }

    @Test
    fun copyFollowsTheExportedKeyType() =
        runTest(dispatcher) {
            val incoming = requireNotNull(startedVm(type = VKType.INCOMING).state.value)
            assertEquals(stringRes(R.string.exportViewingKey_detail_title_incoming), incoming.title)
            assertEquals(stringRes(R.string.exportViewingKey_detail_subtitle_incoming), incoming.subtitle)
            assertEquals(stringRes(R.string.exportViewingKey_disclaimer_qr_incoming), incoming.disclaimer)

            val full = requireNotNull(startedVm(type = VKType.FULL).state.value)
            assertEquals(stringRes(R.string.exportViewingKey_detail_title_full), full.title)
            assertEquals(stringRes(R.string.exportViewingKey_detail_subtitle_full), full.subtitle)
            assertEquals(stringRes(R.string.exportViewingKey_disclaimer_qr_full), full.disclaimer)
        }

    @Test
    fun missingKeyNavigatesBackWithoutAState() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(key = null, router = router)

            assertNull(vm.state.value)
            assertEquals(1, router.backCount)
        }

    private fun TestScope.reveal(vm: VKDetailVM) {
        requireNotNull(vm.state.value).primaryButton.onClick()
        advanceUntilIdle()
    }

    private fun TestScope.switchToKeyTab(vm: VKDetailVM) {
        requireNotNull(vm.state.value).tabs[1].onClick()
        advanceUntilIdle()
    }

    private fun biometrics(succeeds: Boolean) =
        mockk<BiometricRepository> {
            if (succeeds) {
                coEvery { requestBiometrics(any()) } returns Unit
            } else {
                coEvery { requestBiometrics(any()) } throws BiometricsFailureException()
            }
        }

    private fun TestScope.startedVm(
        type: VKType = VKType.INCOMING,
        key: String? = KEY,
        biometricRepository: BiometricRepository = biometrics(succeeds = true),
        shareQR: ShareQRUseCase = mockk(relaxed = true),
        router: FakeNavigationRouter = FakeNavigationRouter(),
    ): VKDetailVM {
        val getVK =
            mockk<GetVKUseCase> {
                every { observe(type) } returns flowOf(key)
            }
        val vm =
            VKDetailVM(
                args = VKDetailArgs(type),
                getVK = getVK,
                shareQR = shareQR,
                biometricRepository = biometricRepository,
                navigationRouter = router,
            )
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }
}

private const val KEY = "uview1testviewingkeypayload"

private const val LONGER_KEY_PADDING = 40

private class FakeNavigationRouter : NavigationRouter {
    var backCount = 0
        private set
    val forwardedRoutes = mutableListOf<Any>()

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) = Unit

    override fun replaceAll(vararg routes: Any) = Unit

    override fun replaceFrom(route: KClass<*>, vararg routes: Any) = Unit

    override fun back() {
        backCount++
    }

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
