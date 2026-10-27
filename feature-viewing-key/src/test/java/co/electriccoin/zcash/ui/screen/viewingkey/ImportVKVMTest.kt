package co.electriccoin.zcash.ui.screen.viewingkey

import cash.z.ecc.android.sdk.model.UnifiedFullViewingKeyComponents
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.repository.PendingViewOnlyImport
import co.electriccoin.zcash.ui.common.repository.ViewOnlyPendingImportRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.ClearClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitViewOnlyKeyResult
import co.electriccoin.zcash.ui.common.usecase.SubmitViewOnlyKeyUseCase
import co.electriccoin.zcash.ui.common.usecase.UfvkDescriber
import co.electriccoin.zcash.ui.common.usecase.ValidateUfvkUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewOnlyKeyCheckException
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import co.electriccoin.zcash.ui.screen.connecthw.date.HWDateArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported.AlreadyImportedVKArgs
import co.electriccoin.zcash.ui.screen.viewingkey.importvk.ImportVKVM
import co.electriccoin.zcash.ui.screen.viewingkey.scan.ScanVKArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The paste screen enables Continue for any text, submits it once, and routes by the outcome: an
 * importable key to the birthday date, a key the wallet holds to the "already imported" sheet, anything else
 * to the invalid-key sheet. A key the SDK fails to read opens the general error screen instead, and
 * Continue can be tried again. A pasted key is wiped from the clipboard on Continue and on back, and
 * the key never shows up in the state's text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportVKVMTest {
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private val clearClipboard = mockk<ClearClipboardUseCase>(relaxed = true)

    private val repository = ViewOnlyPendingImportRepositoryImpl()

    private val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)

    private var result = SubmitViewOnlyKeyResult.Accepted

    private val submit =
        mockk<SubmitViewOnlyKeyUseCase> { coEvery { this@mockk.invoke(any(), any()) } answers { result } }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun continueIsEnabledOnlyWithText() =
        runTest {
            val vm = vm()
            collect(vm)

            assertFalse(vm.state.value.continueButton.isEnabled)
            vm.state.value.field
                .onValueChange("   ")
            runCurrent()
            assertFalse(vm.state.value.continueButton.isEnabled)
            vm.state.value.field
                .onValueChange(TYPED)
            runCurrent()
            assertTrue(vm.state.value.continueButton.isEnabled)
        }

    @Test
    fun anImportableKeyGoesOnToTheBirthdayDate() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, TYPED)

            vm.state.value.continueButton
                .onClick()
            runCurrent()

            coVerify(exactly = 1) { submit.invoke(TYPED, any()) }
            verify(exactly = 1) { navigationRouter.forward(HWDateArgs(HWWalletEnrollment.ViewOnly)) }
            assertNull(vm.invalidKeySheet.value)
        }

    @Test
    fun aKeyTheWalletHoldsOpensTheAlreadyImportedSheet() =
        runTest {
            result = SubmitViewOnlyKeyResult.AlreadyImported
            val vm = vm()
            collect(vm)
            type(vm, TYPED)

            vm.state.value.continueButton
                .onClick()
            runCurrent()

            verify(exactly = 1) { navigationRouter.forward(AlreadyImportedVKArgs) }
        }

    @Test
    fun anInvalidKeyOpensTheInvalidKeySheetAndEditClosesIt() =
        runTest {
            result = SubmitViewOnlyKeyResult.Invalid
            val vm = vm()
            collect(vm)
            type(vm, "garbage")

            vm.state.value.continueButton
                .onClick()
            runCurrent()

            val sheet = assertNotNull(vm.invalidKeySheet.value)
            verify(exactly = 0) { navigationRouter.forward(any()) }
            sheet.primaryAction.onClick()
            runCurrent()
            assertNull(vm.invalidKeySheet.value)
        }

    @Test
    fun aKeyTheSdkFailsToReadOpensTheErrorScreenNotTheInvalidKeySheet() =
        runTest {
            val vm = vm(submitWithDescriber { throw IllegalStateException("native failure") })
            collect(vm)
            type(vm, SDK_KEY)

            vm.state.value.continueButton
                .onClick()
            runCurrent()

            assertNull(vm.invalidKeySheet.value)
            verify(exactly = 1) {
                navigateToError(match { assertIs<ErrorArgs.General>(it).exception is ViewOnlyKeyCheckException }, any())
            }
            verify(exactly = 0) { navigationRouter.forward(any()) }
            assertNull(repository.get())
            assertTrue(vm.state.value.continueButton.isEnabled)
            assertFalse(vm.state.value.continueButton.isLoading)
        }

    @Test
    fun aKeyTheSdkReadsAsInvalidOpensTheInvalidKeySheetNotTheErrorScreen() =
        runTest {
            val vm = vm(submitWithDescriber { null })
            collect(vm)
            type(vm, SDK_KEY)

            vm.state.value.continueButton
                .onClick()
            runCurrent()

            assertNotNull(vm.invalidKeySheet.value)
            verify(exactly = 0) { navigateToError(any(), any()) }
            assertNull(repository.get())
            assertTrue(vm.state.value.continueButton.isEnabled)
            assertFalse(vm.state.value.continueButton.isLoading)
        }

    @Test
    fun scanInsteadHidesTheSheetBeforeOpeningTheScanner() =
        runTest {
            result = SubmitViewOnlyKeyResult.Invalid
            val vm = vm()
            collect(vm)
            type(vm, "garbage")
            vm.state.value.continueButton
                .onClick()
            runCurrent()

            assertNotNull(assertNotNull(vm.invalidKeySheet.value).secondaryAction).onClick()
            runCurrent()
            assertNull(vm.invalidKeySheet.value)
            verify(exactly = 0) { navigationRouter.forward(ScanVKArgs) }

            advanceTimeBy(HIDE_DELAY_MS)
            runCurrent()
            verify(exactly = 1) { navigationRouter.forward(ScanVKArgs) }
        }

    @Test
    fun aDoubleTapSubmitsOnce() =
        runTest {
            val gate = CompletableDeferred<SubmitViewOnlyKeyResult>()
            coEvery { submit.invoke(any(), any()) } coAnswers { gate.await() }
            val vm = vm()
            collect(vm)
            type(vm, TYPED)

            vm.state.value.continueButton
                .onClick()
            runCurrent()
            assertTrue(vm.state.value.continueButton.isLoading)
            vm.state.value.continueButton
                .onClick()
            gate.complete(SubmitViewOnlyKeyResult.Accepted)
            runCurrent()

            coVerify(exactly = 1) { submit.invoke(any(), any()) }
        }

    @Test
    fun backDuringTheCheckCancelsItSoNothingNavigatesLater() =
        runTest {
            val gate = CompletableDeferred<SubmitViewOnlyKeyResult>()
            coEvery { submit.invoke(any(), any()) } coAnswers { gate.await() }
            repository.set(PendingViewOnlyImport(ufvk = "uview1pending", name = ""))
            val vm = vm()
            collect(vm)
            type(vm, TYPED)
            vm.state.value.continueButton
                .onClick()
            runCurrent()

            vm.state.value.onBack()
            gate.complete(SubmitViewOnlyKeyResult.Accepted)
            runCurrent()

            verify(exactly = 1) { navigationRouter.back() }
            verify(exactly = 0) { navigationRouter.forward(any()) }
            assertNull(repository.get())
        }

    @Test
    fun aPastedKeyIsWipedFromTheClipboardOnContinue() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, TYPED)

            vm.state.value.continueButton
                .onClick()
            runCurrent()

            verify(exactly = 1) { clearClipboard.invoke() }
        }

    @Test
    fun aPastedKeyIsWipedFromTheClipboardOnBack() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, TYPED)

            vm.state.value.onBack()

            verify(exactly = 1) { clearClipboard.invoke() }
        }

    @Test
    fun aTypedKeyLeavesTheClipboardAlone() =
        runTest {
            val vm = vm()
            collect(vm)
            TYPED.forEachIndexed { index, _ -> type(vm, TYPED.substring(0, index + 1)) }

            vm.state.value.continueButton
                .onClick()
            runCurrent()
            vm.state.value.onBack()

            verify(exactly = 0) { clearClipboard.invoke() }
        }

    @Test
    fun theStateNeverPrintsTheKey() =
        runTest {
            val vm = vm()
            collect(vm)
            type(vm, TYPED)

            assertFalse(
                vm.state.value
                    .toString()
                    .contains("uview")
            )
        }

    @Test
    fun clearingTheFlowsRootForgetsThePendingKey() =
        runTest {
            val vm = vm()
            repository.set(PendingViewOnlyImport(ufvk = TYPED, name = "Savings"))

            vm.triggerOnCleared()

            assertNull(repository.get())
        }

    @Test
    fun theQrButtonOpensTheScanner() =
        runTest {
            val vm = vm()
            collect(vm)

            vm.state.value.scanButton
                .onClick()

            verify(exactly = 1) { navigationRouter.forward(ScanVKArgs) }
        }

    private fun TestScope.type(
        vm: ImportVKVM,
        text: String,
    ) {
        vm.state.value.field
            .onValueChange(text)
        runCurrent()
    }

    private fun vm(submitViewOnlyKey: SubmitViewOnlyKeyUseCase = submit) =
        ImportVKVM(
            submitViewOnlyKey = submitViewOnlyKey,
            clearClipboard = clearClipboard,
            pendingImportRepository = repository,
            navigationRouter = navigationRouter,
            navigateToError = navigateToError,
        )

    /**
     * The real submit and validation with only the SDK's reading of the key faked, so the test covers
     * how the describer's answer reaches the screen.
     */
    private fun submitWithDescriber(describe: () -> UnifiedFullViewingKeyComponents?) =
        SubmitViewOnlyKeyUseCase(
            validateUfvk =
                ValidateUfvkUseCase(
                    object : UfvkDescriber {
                        override suspend fun describe(
                            ufvk: String,
                            network: ZcashNetwork,
                        ): UnifiedFullViewingKeyComponents? = describe()
                    }
                ),
            synchronizerProvider = mockk(),
            pendingImportRepository = repository,
        )

    /**
     * `onCleared` is protected on `ViewModel`, and the test needs the real disposal path rather
     * than a stand-in for it.
     */
    private fun ImportVKVM.triggerOnCleared() {
        ImportVKVM::class.java
            .getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(this)
    }

    private fun TestScope.collect(vm: ImportVKVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.invalidKeySheet.collect { } }
        runCurrent()
    }

    private companion object {
        const val TYPED = "uview1thisisafixturethatisnotarealkey"

        /** Text that passes the use case's pre-filter for the build's network, so the SDK is asked. */
        val SDK_KEY = ValidateUfvkUseCase.keyPrefixOf(VersionInfo.NETWORK) + "thisisafixturethatisnotarealkey"
        const val HIDE_DELAY_MS = 351L
    }
}
