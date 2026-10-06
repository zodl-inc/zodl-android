package co.electriccoin.zcash.ui.screen.redeemgift

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkPrefixes
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.common.repository.GiftCardRepository
import co.electriccoin.zcash.ui.common.usecase.ClearClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.common.usecase.ReadClipboardTextUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The gift card scanner only lets gift card links through, and hands them to the redeem screen by id only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanGiftCardVMTest {
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
    fun scannedGiftLinkReplacesTheScannerWithTheRedeemScreen() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned(LINK)
            advanceUntilIdle()

            val args = assertIs<RedeemGiftArgs>(env.router.replacedRoutes.single())
            assertEquals(LINK, env.store.take(args.linkId))
            assertEquals(ScanValidationState.VALID, env.vm.state.value.validation)
        }

    @Test
    fun routeNeverCarriesTheLink() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned(LINK)
            advanceUntilIdle()

            val args = assertIs<RedeemGiftArgs>(env.router.replacedRoutes.single())
            assertTrue(!args.toString().contains("zgift1"))
        }

    @Test
    fun vizorLinkIsAccepted() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned(VIZOR_LINK)
            advanceUntilIdle()

            assertIs<RedeemGiftArgs>(env.router.replacedRoutes.single())
        }

    @Test
    fun otherQrCodesAreRejected() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned("zcash:u1someaddress?amount=1")
            advanceUntilIdle()

            assertEquals(ScanValidationState.INVALID, env.vm.state.value.validation)
            assertTrue(env.router.replacedRoutes.isEmpty())
        }

    @Test
    fun pastedGiftLinkOpensTheRedeemScreen() =
        runTest(dispatcher) {
            val env = Env(clipboard = "  $LINK\n")

            env.vm.onPaste()
            advanceUntilIdle()

            val args = assertIs<RedeemGiftArgs>(env.router.replacedRoutes.single())
            assertEquals(LINK, env.store.take(args.linkId))
            verify(exactly = 1) { env.clearClipboard() }
        }

    @Test
    fun pastingSomethingElseIsRejected() =
        runTest(dispatcher) {
            val env = Env(clipboard = "zcash:u1someaddress")

            env.vm.onPaste()
            advanceUntilIdle()

            assertEquals(ScanValidationState.INVALID, env.vm.state.value.validation)
            assertTrue(env.router.replacedRoutes.isEmpty())
            verify(exactly = 0) { env.clearClipboard() }
        }

    @Test
    fun anEmptyClipboardIsRejected() =
        runTest(dispatcher) {
            val env = Env(clipboard = null)

            env.vm.onPaste()
            advanceUntilIdle()

            assertEquals(ScanValidationState.INVALID, env.vm.state.value.validation)
            verify(exactly = 0) { env.clearClipboard() }
        }

    @Test
    fun theStateCarriesTheScannersCopyAndBack() =
        runTest(dispatcher) {
            val env = Env()

            assertEquals(stringRes(R.string.redeemGift_scan_invalid), env.vm.state.value.invalidQrText)
            assertNull(env.vm.state.value.infoText)
            env.vm.state.value
                .onBack()
            assertEquals(1, env.router.backCount)

            val external = Env(isFromExternalLink = true)
            assertEquals(stringRes(R.string.redeemGift_scan_externalLink), external.vm.state.value.infoText)
        }

    @Test
    fun onlyTheFirstLinkIsHandled() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned(LINK)
            env.vm.onImageScanned(ImageToQrCodeResult.SingleCode(LINK))
            advanceUntilIdle()

            assertEquals(1, env.router.replacedRoutes.size)
        }

    @Test
    fun aScanErrorMarksTheScanInvalidUntilALinkWasTaken() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScannedError()
            advanceUntilIdle()
            assertEquals(ScanValidationState.INVALID, env.vm.state.value.validation)

            env.vm.onScanned(LINK)
            env.vm.onScannedError()
            advanceUntilIdle()
            assertEquals(ScanValidationState.VALID, env.vm.state.value.validation)
        }

    @Test
    fun anImageWithSeveralCodesOrNoneIsReportedAsSuch() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onImageScanned(ImageToQrCodeResult.MultipleCodes)
            advanceUntilIdle()
            assertEquals(ScanValidationState.SEVERAL_CODES_FOUND, env.vm.state.value.validation)

            env.vm.onImageScanned(ImageToQrCodeResult.NoCode)
            advanceUntilIdle()
            assertEquals(ScanValidationState.INVALID_IMAGE, env.vm.state.value.validation)
            assertTrue(env.router.replacedRoutes.isEmpty())
        }

    @Test
    fun anImageOfAGiftCardOpensTheRedeemScreenAndLaterImagesAreIgnored() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onImageScanned(ImageToQrCodeResult.SingleCode(" $LINK "))
            advanceUntilIdle()
            env.vm.onImageScanned(ImageToQrCodeResult.NoCode)
            advanceUntilIdle()

            val args = assertIs<RedeemGiftArgs>(env.router.replacedRoutes.single())
            assertEquals(LINK, env.store.take(args.linkId))
            assertEquals(ScanValidationState.VALID, env.vm.state.value.validation)
        }

    @Test
    fun scansAndPastesAfterTheFirstLinkAreIgnoredAndLeaveTheClipboardAlone() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned(LINK)
            env.vm.onScanned(VIZOR_LINK)
            env.vm.onPaste()
            env.vm.onScanned("zcash:u1someaddress")
            advanceUntilIdle()

            assertEquals(1, env.router.replacedRoutes.size)
            assertEquals(ScanValidationState.VALID, env.vm.state.value.validation)
            verify(exactly = 0) { env.clearClipboard() }
        }

    private class Env(
        clipboard: String? = LINK,
        isFromExternalLink: Boolean = false
    ) {
        val store = GiftCardLinkStoreImpl()
        val router = RecordingNavigationRouter()
        val clearClipboard = mockk<ClearClipboardUseCase>(relaxed = true)
        private val giftCardRepository =
            mockk<GiftCardRepository> {
                every { isGiftCardLink(any()) } answers { GiftCardLinkPrefixes.matches(firstArg()) }
            }
        val vm =
            ScanGiftCardVM(
                args = ScanGiftCardArgs(isFromExternalLink = isFromExternalLink),
                navigateToRedeemGiftCard =
                    NavigateToRedeemGiftCardUseCase(
                        giftCardRepository = giftCardRepository,
                        giftCardLinkStore = store,
                        navigationRouter = router
                    ),
                readClipboardText = mockk<ReadClipboardTextUseCase> { every { this@mockk.invoke() } returns clipboard },
                clearClipboard = clearClipboard,
                navigationRouter = router
            )
    }

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
        const val VIZOR_LINK = "https://link.vizor.cash/payment-links/open#v3=abc"
    }
}
