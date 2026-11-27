package co.electriccoin.zcash.ui.screen.redeemgift

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.redeemgift.paste.PasteGiftCardLinkArgs
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

            val args = env.router.redeemReplacingGiftCardScan().single()
            assertEquals(LINK, env.store.take(args.linkId))
            assertEquals(ScanValidationState.VALID, env.vm.state.value.validation)
            assertTrue(env.router.replacedRoutes.isEmpty())
        }

    /** Routes are serialized into the saved instance state, so the serialized route must not hold the link. */
    @Test
    fun routeNeverCarriesTheLink() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned(LINK)
            advanceUntilIdle()

            val args = env.router.redeemReplacingGiftCardScan().single()
            assertFalse(Json.encodeToString(RedeemGiftArgs.serializer(), args).contains(LINK_SECRET))
        }

    @Test
    fun vizorLinkIsAccepted() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned(VIZOR_LINK)
            advanceUntilIdle()

            val args = env.router.redeemReplacingGiftCardScan().single()
            assertEquals(VIZOR_LINK, env.store.take(args.linkId))
        }

    @Test
    fun otherQrCodesAreRejected() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned("zcash:u1someaddress?amount=1")
            advanceUntilIdle()

            assertEquals(ScanValidationState.INVALID, env.vm.state.value.validation)
            assertTrue(env.router.replacedFrom.isEmpty())
        }

    @Test
    fun pasteLinkOpensThePasteScreenWithoutReadingTheClipboard() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onPaste()
            advanceUntilIdle()

            assertEquals(listOf<Any>(PasteGiftCardLinkArgs), env.router.forwardedRoutes)
            assertTrue(env.router.replacedFrom.isEmpty())
            assertEquals(ScanValidationState.NONE, env.vm.state.value.validation)
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

            assertEquals(1, env.router.redeemReplacingGiftCardScan().size)
        }

    @Test
    fun pasteIsIgnoredOnceALinkWasTaken() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned(LINK)
            advanceUntilIdle()
            env.vm.onPaste()

            assertEquals(1, env.router.redeemReplacingGiftCardScan().size)
            assertTrue(env.router.forwardedRoutes.isEmpty())
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
            assertTrue(env.router.replacedFrom.isEmpty())
        }

    @Test
    fun anImageOfAGiftCardOpensTheRedeemScreenAndLaterImagesAreIgnored() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onImageScanned(ImageToQrCodeResult.SingleCode(" $LINK "))
            advanceUntilIdle()
            env.vm.onImageScanned(ImageToQrCodeResult.NoCode)
            advanceUntilIdle()

            val args = env.router.redeemReplacingGiftCardScan().single()
            assertEquals(LINK, env.store.take(args.linkId))
            assertEquals(ScanValidationState.VALID, env.vm.state.value.validation)
        }

    @Test
    fun scansAfterTheFirstLinkAreIgnored() =
        runTest(dispatcher) {
            val env = Env()

            env.vm.onScanned(LINK)
            env.vm.onScanned(VIZOR_LINK)
            env.vm.onScanned("zcash:u1someaddress")
            advanceUntilIdle()

            assertEquals(1, env.router.redeemReplacingGiftCardScan().size)
            assertEquals(ScanValidationState.VALID, env.vm.state.value.validation)
        }

    private class Env(
        isFromExternalLink: Boolean = false
    ) {
        val store = GiftCardLinkStoreImpl()
        val router = RecordingNavigationRouter()
        val vm =
            ScanGiftCardVM(
                args = ScanGiftCardArgs(isFromExternalLink = isFromExternalLink),
                navigateToRedeemGiftCard = navigateToRedeemGiftCard(store, router),
                navigationRouter = router
            )
    }

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
        const val LINK_SECRET = "zgift1test"
        const val VIZOR_LINK = "https://link.vizor.cash/payment-links/open#v3=abc"
    }
}
