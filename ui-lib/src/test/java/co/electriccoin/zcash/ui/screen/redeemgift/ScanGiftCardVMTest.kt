package co.electriccoin.zcash.ui.screen.redeemgift

import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.common.usecase.ReadClipboardTextUseCase
import co.electriccoin.zcash.ui.fixture.FakeGiftCardRepository
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import io.mockk.every
import io.mockk.mockk
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
            assertEquals(ScanValidationState.VALID, env.vm.state.value)
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

            assertEquals(ScanValidationState.INVALID, env.vm.state.value)
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
        }

    @Test
    fun pastingSomethingElseIsRejected() =
        runTest(dispatcher) {
            val env = Env(clipboard = null)

            env.vm.onPaste()
            advanceUntilIdle()

            assertEquals(ScanValidationState.INVALID, env.vm.state.value)
            assertTrue(env.router.replacedRoutes.isEmpty())
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

    private class Env(
        clipboard: String? = LINK
    ) {
        val store = GiftCardLinkStoreImpl()
        val router = RecordingNavigationRouter()
        val vm =
            ScanGiftCardVM(
                navigateToRedeemGiftCard =
                    NavigateToRedeemGiftCardUseCase(
                        giftCardRepository = FakeGiftCardRepository(),
                        giftCardLinkStore = store,
                        navigationRouter = router
                    ),
                readClipboardText = mockk<ReadClipboardTextUseCase> { every { this@mockk.invoke() } returns clipboard },
                navigationRouter = router
            )
    }

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
        const val VIZOR_LINK = "https://link.vizor.cash/payment-links/open#v3=abc"
    }
}
