package co.electriccoin.zcash.ui.screen.redeemgift.paste

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.common.usecase.ClearClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.ReadClipboardTextUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.redeemgift.RecordingNavigationRouter
import co.electriccoin.zcash.ui.screen.redeemgift.navigateToRedeemGiftCard
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The paste link screen only lets gift card links through, hands them to the redeem screen by id only, and clears a
 * pasted link from the clipboard once it has been taken.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasteGiftCardLinkVMTest {
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
    fun anEmptyFieldOffersPasteAndCannotContinue() =
        runTest(dispatcher) {
            val env = Env(this)

            val state = env.state
            assertEquals(stringRes(""), state.field.value)
            assertNull(state.field.error)
            assertNull(state.invalidHint)
            assertEquals(stringRes(R.string.redeemGift_paste_paste), state.fieldButton.text)
            assertEquals(R.drawable.ic_copy, state.fieldButton.icon)
            assertFalse(state.continueButton.isEnabled)

            state.continueButton.onClick()
            runCurrent()
            assertTrue(env.router.replacedFrom.isEmpty())
        }

    @Test
    fun pastingAGiftLinkEnablesContinueAndOffersClear() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = "  $LINK\n")

            env.state.fieldButton.onClick()
            runCurrent()

            val state = env.state
            assertEquals(stringRes(LINK), state.field.value)
            assertNull(state.field.error)
            assertNull(state.invalidHint)
            assertEquals(stringRes(R.string.redeemGift_paste_clear), state.fieldButton.text)
            assertNull(state.fieldButton.icon)
            assertTrue(state.continueButton.isEnabled)
        }

    @Test
    fun somethingElseIsInvalidAndCannotContinue() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = "zodl.com/gift/8f2k")

            env.state.fieldButton.onClick()
            runCurrent()

            val state = env.state
            assertNotNull(state.field.error)
            assertEquals(stringRes(R.string.redeemGift_paste_invalid), state.invalidHint)
            assertEquals(stringRes(R.string.redeemGift_paste_clear), state.fieldButton.text)
            assertFalse(state.continueButton.isEnabled)

            state.continueButton.onClick()
            runCurrent()
            assertTrue(env.router.replacedFrom.isEmpty())
            verify(exactly = 0) { env.clearClipboard() }
        }

    @Test
    fun aLinkBeingTypedIsNotInvalidYet() =
        runTest(dispatcher) {
            val env = Env(this)

            listOf("h", "HTTPS://", " https://gift.zo", "https://gift.zodl.com/", "https://link.vizor.cash/pay")
                .forEach { partial ->
                    env.state.field.onValueChange(partial)
                    runCurrent()

                    val state = env.state
                    assertNull(state.field.error, partial)
                    assertNull(state.invalidHint, partial)
                    assertFalse(state.continueButton.isEnabled, partial)
                }
        }

    @Test
    fun typingPastAnyGiftLinkPrefixIsInvalid() =
        runTest(dispatcher) {
            val env = Env(this)

            listOf("https://gift.zodl.org", "x", "https://link.vizor.cash/payment-links/close#").forEach { text ->
                env.state.field.onValueChange(text)
                runCurrent()

                assertNotNull(env.state.field.error, text)
                assertEquals(stringRes(R.string.redeemGift_paste_invalid), env.state.invalidHint, text)
            }
        }

    @Test
    fun clearEmptiesTheField() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = "not a link")
            env.state.fieldButton.onClick()
            runCurrent()

            env.state.fieldButton.onClick()
            runCurrent()

            val state = env.state
            assertEquals(stringRes(""), state.field.value)
            assertNull(state.invalidHint)
            assertEquals(stringRes(R.string.redeemGift_paste_paste), state.fieldButton.text)
            assertFalse(state.continueButton.isEnabled)
        }

    @Test
    fun anEmptyClipboardLeavesTheFieldEmpty() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = null)

            env.state.fieldButton.onClick()
            runCurrent()

            assertEquals(stringRes(""), env.state.field.value)
            assertFalse(env.state.continueButton.isEnabled)
        }

    @Test
    fun continueOpensTheRedeemScreenWithThePastedLinkAndClearsTheClipboard() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = " $LINK ")
            env.state.fieldButton.onClick()
            runCurrent()

            env.state.continueButton.onClick()
            runCurrent()

            val args = env.router.redeemReplacingGiftCardScan().single()
            assertEquals(LINK, env.store.take(args.linkId))
            verify(exactly = 1) { env.clearClipboard() }
        }

    @Test
    fun aPastedLinkEditedBeforeContinueStillClearsTheClipboard() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = "$LINK&extra")
            env.state.fieldButton.onClick()
            runCurrent()

            env.state.field.onValueChange(LINK)
            runCurrent()
            env.state.continueButton.onClick()
            runCurrent()

            val args = env.router.redeemReplacingGiftCardScan().single()
            assertEquals(LINK, env.store.take(args.linkId))
            verify(exactly = 1) { env.clearClipboard() }
        }

    @Test
    fun theStateNeverPrintsTheLink() =
        runTest(dispatcher) {
            val env = Env(this)

            env.state.field.onValueChange(LINK)
            runCurrent()

            assertFalse(env.state.toString().contains(LINK_SECRET))
            assertFalse(env.state.toString().contains("gift.zodl.com"))
        }

    @Test
    fun aTypedLinkIsTrimmedAndLeavesTheClipboardAlone() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = "something else")

            env.state.field.onValueChange("  $LINK ")
            runCurrent()
            assertTrue(env.state.continueButton.isEnabled)
            env.state.continueButton.onClick()
            runCurrent()

            val args = env.router.redeemReplacingGiftCardScan().single()
            assertEquals(LINK, env.store.take(args.linkId))
            verify(exactly = 0) { env.clearClipboard() }
        }

    @Test
    fun aDoubleTapOnContinueOpensTheRedeemScreenOnce() =
        runTest(dispatcher) {
            val env = Env(this)
            env.state.field.onValueChange(LINK)
            runCurrent()

            val continueButton = env.state.continueButton
            continueButton.onClick()
            continueButton.onClick()
            runCurrent()

            assertEquals(1, env.router.redeemReplacingGiftCardScan().size)
        }

    @Test
    fun backReturnsToTheScanner() =
        runTest(dispatcher) {
            val env = Env(this)

            env.state.onBack()

            assertEquals(1, env.router.backCount)
            assertTrue(env.router.replacedFrom.isEmpty())
            verify(exactly = 0) { env.clearClipboard() }
        }

    @Test
    fun backAfterPastingClearsTheClipboard() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = LINK)
            env.state.fieldButton.onClick()
            runCurrent()

            env.state.onBack()

            assertEquals(1, env.router.backCount)
            assertTrue(env.router.redeemReplacingGiftCardScan().isEmpty())
            verify(exactly = 1) { env.clearClipboard() }
        }

    @Test
    fun backAfterPastingSomethingElseAndClearingTheFieldStillClearsTheClipboard() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = "not a link")
            env.state.fieldButton.onClick()
            runCurrent()
            env.state.fieldButton.onClick()
            runCurrent()

            env.state.onBack()

            verify(exactly = 1) { env.clearClipboard() }
        }

    @Test
    fun backAfterTypingLeavesTheClipboardAlone() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = "something else")
            env.state.field.onValueChange(LINK)
            runCurrent()

            env.state.onBack()

            assertEquals(1, env.router.backCount)
            verify(exactly = 0) { env.clearClipboard() }
        }

    @Test
    fun continueClearsTheFieldSoTheLinkDoesNotLinger() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = LINK)
            env.state.fieldButton.onClick()
            runCurrent()

            env.state.continueButton.onClick()
            runCurrent()

            assertEquals(stringRes(""), env.state.field.value)
            assertFalse(env.state.continueButton.isEnabled)
            assertEquals(1, env.router.redeemReplacingGiftCardScan().size)
        }

    @Test
    fun backAfterContinueDoesNotClearTheClipboardAgain() =
        runTest(dispatcher) {
            val env = Env(this, clipboard = LINK)
            env.state.fieldButton.onClick()
            runCurrent()
            env.state.continueButton.onClick()
            runCurrent()

            env.state.onBack()

            verify(exactly = 1) { env.clearClipboard() }
        }

    private class Env(
        scope: TestScope,
        clipboard: String? = null,
    ) {
        val store = GiftCardLinkStoreImpl()
        val router = RecordingNavigationRouter()
        val clearClipboard = mockk<ClearClipboardUseCase>(relaxed = true)
        val vm =
            PasteGiftCardLinkVM(
                navigateToRedeemGiftCard = navigateToRedeemGiftCard(store, router),
                readClipboardText = mockk<ReadClipboardTextUseCase> { every { this@mockk.invoke() } returns clipboard },
                clearClipboard = clearClipboard,
                navigationRouter = router
            )

        val state: PasteGiftCardLinkState
            get() = vm.state.value

        init {
            scope.backgroundScope.launch { vm.state.collect { } }
            scope.runCurrent()
        }
    }

    private companion object {
        const val LINK_SECRET = "zgift1test"
        const val LINK = "https://gift.zodl.com/#v=1&key=$LINK_SECRET&height=3100000"
    }
}
