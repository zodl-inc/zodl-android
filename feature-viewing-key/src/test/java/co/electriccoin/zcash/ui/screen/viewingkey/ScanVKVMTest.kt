package co.electriccoin.zcash.ui.screen.viewingkey

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.usecase.SubmitViewOnlyKeyResult
import co.electriccoin.zcash.ui.common.usecase.SubmitViewOnlyKeyUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewOnlyKeyCheckException
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import co.electriccoin.zcash.ui.screen.connecthw.date.HWDateArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported.AlreadyImportedVKArgs
import co.electriccoin.zcash.ui.screen.viewingkey.scan.ScanVKVM
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The scanner takes the first importable key and replaces itself with the birthday date screen; a
 * code that is not one opens the invalid-code sheet, which pauses further results until it is
 * closed. A key the SDK fails to read stops the scanner and opens the general error screen on the
 * paste screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanVKVMTest {
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private val results = mutableMapOf<String, SubmitViewOnlyKeyResult>()

    private val submit =
        mockk<SubmitViewOnlyKeyUseCase> {
            coEvery { this@mockk.invoke(any(), any()) } answers {
                if (firstArg<String>() == UNREADABLE) throw ViewOnlyKeyCheckException()
                results.getValue(firstArg())
            }
        }

    private val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        results[KEY] = SubmitViewOnlyKeyResult.Accepted
        results[OTHER_KEY] = SubmitViewOnlyKeyResult.Accepted
        results[HELD_KEY] = SubmitViewOnlyKeyResult.AlreadyImported
        results[GARBAGE] = SubmitViewOnlyKeyResult.Invalid
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theFirstImportableKeyReplacesTheScannerWithTheBirthdayDate() =
        runTest {
            val vm = vm()
            collect(vm)

            vm.onScanned(KEY)
            vm.onScanned(OTHER_KEY)
            runCurrent()

            verify(exactly = 1) { navigationRouter.replace(HWDateArgs(HWWalletEnrollment.ViewOnly)) }
            coVerify(exactly = 1) { submit.invoke(any(), any()) }
            assertEquals(ScanValidationState.VALID, vm.state.value.validation)
        }

    @Test
    fun aKeyTheWalletHoldsReplacesTheScannerWithTheAlreadyImportedSheet() =
        runTest {
            val vm = vm()
            collect(vm)

            vm.onScanned(HELD_KEY)
            runCurrent()

            verify(exactly = 1) { navigationRouter.replace(AlreadyImportedVKArgs) }
        }

    @Test
    fun anInvalidCodeOpensTheSheetAndPausesScanningUntilScanAgain() =
        runTest {
            val vm = vm()
            collect(vm)

            vm.onScanned(GARBAGE)
            runCurrent()
            val sheet = assertNotNull(vm.invalidQrSheet.value)

            vm.onScanned(KEY)
            runCurrent()
            verify(exactly = 0) { navigationRouter.replace(any()) }

            sheet.primaryAction.onClick()
            runCurrent()
            assertNull(vm.invalidQrSheet.value)

            vm.onScanned(KEY)
            runCurrent()
            verify(exactly = 1) { navigationRouter.replace(HWDateArgs(HWWalletEnrollment.ViewOnly)) }
        }

    @Test
    fun aKeyTheSdkCannotReadReturnsToThePasteScreenWithTheErrorScreenNotTheSheet() =
        runTest {
            val vm = vm()
            collect(vm)

            vm.onScanned(UNREADABLE)
            runCurrent()

            assertNull(vm.invalidQrSheet.value)
            verify(exactly = 1) { navigationRouter.back() }
            verify(exactly = 1) {
                navigateToError(match { assertIs<ErrorArgs.General>(it).exception is ViewOnlyKeyCheckException }, any())
            }

            vm.onScanned(KEY)
            runCurrent()
            verify(exactly = 0) { navigationRouter.replace(any()) }
            coVerify(exactly = 1) { submit.invoke(any(), any()) }
        }

    @Test
    fun pasteInsteadHidesTheSheetThenReturnsToThePasteScreen() =
        runTest {
            val vm = vm()
            collect(vm)
            vm.onScanned(GARBAGE)
            runCurrent()

            assertNotNull(assertNotNull(vm.invalidQrSheet.value).secondaryAction).onClick()
            runCurrent()
            assertNull(vm.invalidQrSheet.value)
            verify(exactly = 0) { navigationRouter.back() }

            advanceTimeBy(HIDE_DELAY_MS)
            runCurrent()
            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun imagesWithoutExactlyOneCodeReportItInline() =
        runTest {
            val vm = vm()
            collect(vm)

            vm.onImageScanned(ImageToQrCodeResult.MultipleCodes)
            runCurrent()
            assertEquals(ScanValidationState.SEVERAL_CODES_FOUND, vm.state.value.validation)

            vm.onImageScanned(ImageToQrCodeResult.NoCode)
            runCurrent()
            assertEquals(ScanValidationState.INVALID_IMAGE, vm.state.value.validation)
        }

    @Test
    fun anImageWithAKeyIsHandledLikeAScan() =
        runTest {
            val vm = vm()
            collect(vm)

            vm.onImageScanned(ImageToQrCodeResult.SingleCode(KEY))
            runCurrent()

            verify(exactly = 1) { navigationRouter.replace(HWDateArgs(HWWalletEnrollment.ViewOnly)) }
        }

    @Test
    fun backLeavesTheScanner() =
        runTest {
            val vm = vm()
            collect(vm)

            vm.state.value.onBack()

            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun backDuringTheCheckCancelsItSoNothingNavigatesLater() =
        runTest {
            val gate = CompletableDeferred<SubmitViewOnlyKeyResult>()
            coEvery { submit.invoke(HELD_KEY, any()) } coAnswers { gate.await() }
            val vm = vm()
            collect(vm)
            vm.onScanned(HELD_KEY)
            runCurrent()

            vm.state.value.onBack()
            gate.complete(SubmitViewOnlyKeyResult.Accepted)
            runCurrent()

            verify(exactly = 1) { navigationRouter.back() }
            verify(exactly = 0) { navigationRouter.replace(any()) }
            verify(exactly = 0) { navigateToError(any(), any()) }
        }

    private fun vm() =
        ScanVKVM(
            submitViewOnlyKey = submit,
            navigationRouter = navigationRouter,
            navigateToError = navigateToError,
        )

    private fun TestScope.collect(vm: ScanVKVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.invalidQrSheet.collect { } }
        runCurrent()
    }

    private companion object {
        const val KEY = "uview1scannedfixture"
        const val OTHER_KEY = "uview1otherfixture"
        const val HELD_KEY = "uview1heldfixture"
        const val GARBAGE = "https://example.com"
        const val UNREADABLE = "uview1unreadablefixture"
        const val HIDE_DELAY_MS = 351L
    }
}
