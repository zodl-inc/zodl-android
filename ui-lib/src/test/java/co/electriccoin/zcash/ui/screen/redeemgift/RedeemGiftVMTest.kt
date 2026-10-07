package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardRedemption
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.common.repository.GiftCardRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.GetGiftCardDestinationAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.TickerLocation
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.fixture.FakeGiftCardDataSource
import co.electriccoin.zcash.ui.fixture.GiftCardSummaryFixture
import co.electriccoin.zcash.ui.fixture.GiftCardSummaryFixture.pendingStatus
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The redeem screen over a real [GiftCardRepositoryImpl] and a [FakeGiftCardDataSource]: each session phase maps to
 * its screen, intents reach the repository, back ends the session (except while redeeming), and the screen going
 * away does not cancel the redemption; the card is cleaned up once the session ends with nobody observing it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RedeemGiftVMTest {
    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    /** The amount shown is what the user receives, net of the fee, as on the success screen. */
    @Test
    fun readyCardShowsAmountSenderMessageAndDestination() =
        runTest(dispatcher) {
            val vm = Env(this).startedVm()

            val state = assertIs<RedeemGiftState.Ready>(vm.state.value)
            val amountWithoutTicker =
                stringRes(Zatoshi(GiftCardSummaryFixture.RECEIVED), TickerLocation.HIDDEN).withStyle()
            val muted = StyledStringStyle(color = StringResourceColor.QUARTERNARY)
            val mutedTicker = (stringRes(" ") + stringRes("ZEC")).withStyle(muted)
            assertEquals(amountWithoutTicker + mutedTicker, state.amount)
            assertNotEquals(
                RedeemGiftState.amount(Zatoshi(GiftCardSummaryFixture.AMOUNT), stringRes(CURRENCY_TICKER)),
                state.amount,
                "the amount is net of the fee, not the card's spendable funds"
            )
            assertEquals(
                stringRes(R.string.redeemGift_memo) + " · " + GiftCardSummaryFixture.MESSAGE,
                state.message
            )
            assertEquals(stringRes(R.string.redeemGift_title), state.title)
            assertEquals(stringRes(R.string.redeemGift_ready_title), state.heading)
            assertEquals(stringRes(R.string.redeemGift_ready_messageLabel), state.messageLabel)
            assertEquals(ImageResource.ByDrawable(R.drawable.ic_gift_closed), state.image)
            assertEquals(
                stringRes(R.string.redeemGift_ready_disclaimer, stringRes(R.string.accounts_zashi)),
                state.disclaimer
            )
        }

    @Test
    fun theDisclaimerNamesNoWalletUntilTheSelectedAccountIsKnown() =
        runTest(dispatcher) {
            val vm = Env(this, account = null).startedVm()

            val state = assertIs<RedeemGiftState.Ready>(vm.state.value)
            assertEquals(stringRes(R.string.redeemGift_ready_disclaimer_noWallet), state.disclaimer)
        }

    /** Reopening a ready card never shows the no-wallet disclaimer first when the wallet is already known. */
    @Test
    fun aKnownWalletIsNamedFromTheFirstReadyState() =
        runTest(dispatcher) {
            val env = Env(this)
            env.startedVm()

            val vm = env.newVm()
            val states = mutableListOf<RedeemGiftState?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(states) }
            runCurrent()

            val readyStates = states.filterIsInstance<RedeemGiftState.Ready>()
            assertTrue(readyStates.isNotEmpty())
            readyStates.forEach {
                assertEquals(
                    stringRes(R.string.redeemGift_ready_disclaimer, stringRes(R.string.accounts_zashi)),
                    it.disclaimer
                )
            }
        }

    @Test
    fun readyCardWithoutMessageShowsNone() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.summary = GiftCardSummaryFixture.new(message = " ")

            assertNull(assertIs<RedeemGiftState.Ready>(env.startedVm().state.value).message)
        }

    @Test
    fun checkingCanBeLeftWithBackWhichCancelsTheCheckAndEndsTheSession() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.checkGate = CompletableDeferred()
            val vm = env.startedVm()

            val checking = cardStatusOf(vm)
            assertEquals(stringRes(R.string.redeemGift_checking_title), checking.title)
            assertEquals(stringRes(R.string.redeemGift_checking_subtitle).withStyle(), checking.subtitle)

            checking.onBack()
            runCurrent()

            assertEquals(1, env.router.backCount)
            assertEquals(1, env.dataSource.closed.size)
        }

    @Test
    fun redeemSendsToTheOrchardAddressAndShowsTheAmountReceived() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.redemption =
                GiftCardRedemption(
                    txId = GiftCardSummaryFixture.TX_ID,
                    received = Zatoshi(GiftCardSummaryFixture.RECEIVED)
                )
            val vm = env.startedVm()

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            runCurrent()

            assertEquals(listOf(ORCHARD_ADDRESS), env.dataSource.redeemedTo)
            val progress = cardStatusOf(vm)
            assertEquals(GiftCardStatusState.Background.SUCCESS, progress.background)
            assertEquals(imageRes(R.drawable.ic_gift_open), progress.image)
            assertEquals(stringRes(R.string.redeemGift_success_title), progress.title)
            assertEquals(
                stringRes(
                    R.string.redeemGift_success_subtitle,
                    stringRes(Zatoshi(GiftCardSummaryFixture.RECEIVED))
                ).withStyle(),
                progress.subtitle
            )
        }

    @Test
    fun anUnknownAmountReceivedShowsTheCopyWithoutAnAmount() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.redemption = GiftCardRedemption(txId = GiftCardSummaryFixture.TX_ID, received = null)
            val vm = env.startedVm()

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            runCurrent()

            assertEquals(
                stringRes(R.string.redeemGift_success_subtitle_noAmount).withStyle(),
                cardStatusOf(vm).subtitle
            )
        }

    @Test
    fun aDoubleTapOnRedeemRedeemsOnce() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.redeemGate = CompletableDeferred()
            val vm = env.startedVm()

            val redeem = assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton
            redeem.onClick()
            redeem.onClick()
            runCurrent()
            env.dataSource.redeemGate?.complete(Unit)
            runCurrent()

            assertEquals(listOf(ORCHARD_ADDRESS), env.dataSource.redeemedTo)
        }

    @Test
    fun backIsIgnoredWhileRedeemingAndSuccessCloseEndsTheSessionAndGoesHome() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.redeemGate = CompletableDeferred()
            val vm = env.startedVm()

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            runCurrent()
            assertEquals(stringRes(R.string.redeemGift_redeeming_title), cardStatusOf(vm).title)
            vm.state.value
                ?.onBack
                ?.invoke()
            assertEquals(0, env.router.backCount)
            assertTrue(env.dataSource.closed.isEmpty())

            env.dataSource.redeemGate?.complete(Unit)
            runCurrent()
            requireNotNull(cardStatusOf(vm).primaryButton).onClick()
            assertEquals(1, env.router.backToRootCount)
            assertEquals(1, env.dataSource.closed.size)
        }

    /**
     * Redeem tapped and the screen gone in the same moment: the redemption is not cancelled, and once it ends with
     * nobody observing it the card wallet is closed rather than left syncing until the process dies. Reopening the
     * screen still shows the result.
     */
    @Test
    fun theRedemptionGoesOnWhenTheScreenGoesAway() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.redeemGate = CompletableDeferred()
            val vm = env.startedVm()

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            runCurrent()
            clear(vm)
            runCurrent()
            assertTrue(env.dataSource.closed.isEmpty(), "the redemption is not cancelled")
            env.dataSource.redeemGate?.complete(Unit)
            runCurrent()

            assertEquals(listOf(ORCHARD_ADDRESS), env.dataSource.redeemedTo)
            assertEquals(1, env.dataSource.closed.size)
            val reopened = env.startedVm(linkId = env.linkId)
            assertEquals(stringRes(R.string.redeemGift_success_title), cardStatusOf(reopened).title)
        }

    @Test
    fun clearingTheScreenCleansUpTheCardOnlyAfterTheIdleTimeout() =
        runTest(dispatcher) {
            val env = Env(this)
            clear(env.startedVm())
            runCurrent()
            assertTrue(env.dataSource.closed.isEmpty())

            advanceTimeBy(GiftCardRepositoryImpl.IDLE_TIMEOUT + 1.seconds)

            assertEquals(1, env.dataSource.closed.size)
        }

    @Test
    fun pendingCardIsRecheckedAutomaticallyWhileShown() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.statuses = listOf(pendingStatus(), GiftCardSummaryFixture.readyStatus())
            val vm = env.startedVm()

            assertEquals(stringRes(R.string.redeemGift_pending_title), cardStatusOf(vm).title)
            assertEquals(1, env.dataSource.checkCount)

            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL + 1.seconds)
            runCurrent()

            assertEquals(2, env.dataSource.checkCount)
            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun pendingCardShowsPreparingWithCheckAgainAboveCloseAndNoAppBar() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.statuses = listOf(pendingStatus())
            val vm = env.startedVm()

            val pending = cardStatusOf(vm)
            assertEquals(GiftCardStatusState.Background.PENDING, pending.background)
            assertEquals(imageRes(R.drawable.ic_gift_preparing), pending.image)
            assertEquals(stringRes(R.string.redeemGift_pending_title), pending.title)
            val checkAgain = assertNotNull(pending.secondaryButton)
            assertEquals(stringRes(R.string.redeemGift_checkAgain), checkAgain.text)
            assertEquals(ButtonStyle.SECONDARY, checkAgain.style)
            val close = assertNotNull(pending.primaryButton)
            assertEquals(stringRes(R.string.general_close), close.text)
            assertEquals(ButtonStyle.PRIMARY, close.style)

            pending.onBack()
            runCurrent()

            assertEquals(1, env.router.backCount)
            assertEquals(1, env.dataSource.closed.size)
        }

    @Test
    fun checkAgainOnAPendingCardShowsProgressOnTheButton() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.statuses = listOf(pendingStatus(), GiftCardSummaryFixture.readyStatus())
            val vm = env.startedVm()

            assertEquals(
                stringRes(R.string.redeemGift_pending_subtitle, stringRes(Zatoshi(GiftCardSummaryFixture.AMOUNT)))
                    .withStyle(),
                cardStatusOf(vm).subtitle
            )
            val checkAgain = assertNotNull(cardStatusOf(vm).secondaryButton)
            assertEquals(stringRes(R.string.redeemGift_checkAgain), checkAgain.text)

            env.dataSource.checkGate = CompletableDeferred()
            checkAgain.onClick()
            runCurrent()
            assertEquals(stringRes(R.string.redeemGift_pending_title), cardStatusOf(vm).title)
            assertTrue(assertNotNull(cardStatusOf(vm).secondaryButton).isLoading)

            env.dataSource.checkGate?.complete(Unit)
            runCurrent()
            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun emptyCardShowsNothingToRedeemWithCheckAgainAboveCloseAndNoAppBar() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.statuses = listOf(GiftCardStatus.Empty)
            val vm = env.startedVm()
            assertTrue(env.dataSource.closed.isEmpty(), "an observed empty card stays open, so it can be checked again")

            val empty = cardStatusOf(vm)
            assertEquals(GiftCardStatusState.Background.EMPTY, empty.background)
            assertEquals(imageRes(R.drawable.ic_gift_empty), empty.image)
            assertEquals(stringRes(R.string.redeemGift_empty_title), empty.title)
            assertEquals(stringRes(R.string.redeemGift_empty_subtitle).withStyle(), empty.subtitle)
            val checkAgain = assertNotNull(empty.secondaryButton)
            assertEquals(stringRes(R.string.redeemGift_checkAgain), checkAgain.text)
            assertEquals(ButtonStyle.SECONDARY, checkAgain.style)
            val close = assertNotNull(empty.primaryButton)
            assertEquals(stringRes(R.string.general_close), close.text)
            assertEquals(ButtonStyle.PRIMARY, close.style)

            empty.onBack()
            runCurrent()

            assertEquals(1, env.router.backCount)
            assertEquals(1, env.dataSource.closed.size)
        }

    /** The ticker follows the network: mainnet builds, which these tests run as, show ZEC; testnet builds TAZ. */
    @Test
    fun theReadyAmountTickerComesFromTheNetwork() =
        runTest(dispatcher) {
            val state = assertIs<RedeemGiftState.Ready>(Env(this).startedVm().state.value)

            assertEquals("ZEC", CURRENCY_TICKER)
            assertEquals(
                RedeemGiftState.amount(Zatoshi(GiftCardSummaryFixture.RECEIVED), stringRes(CURRENCY_TICKER)),
                state.amount
            )
            assertNotEquals(
                RedeemGiftState.amount(Zatoshi(GiftCardSummaryFixture.RECEIVED), stringRes("TAZ")),
                state.amount
            )
        }

    @Test
    fun checkAgainOnAnEmptyCardShowsProgressOnTheButtonAndKeepsTheEmptyScreen() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.statuses = listOf(GiftCardStatus.Empty, GiftCardSummaryFixture.readyStatus())
            val vm = env.startedVm()

            env.dataSource.checkGate = CompletableDeferred()
            assertNotNull(cardStatusOf(vm).secondaryButton).onClick()
            runCurrent()

            val rechecking = cardStatusOf(vm)
            assertEquals(stringRes(R.string.redeemGift_empty_title), rechecking.title)
            val checkAgain = assertNotNull(rechecking.secondaryButton)
            assertTrue(checkAgain.isLoading)
            assertFalse(checkAgain.isEnabled)

            env.dataSource.checkGate?.complete(Unit)
            runCurrent()
            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun aFailedCheckAgainOnAnEmptyCardKeepsTheEmptyScreen() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.statuses = listOf(GiftCardStatus.Empty)
            val vm = env.startedVm()

            env.dataSource.checkError = GiftCardException.CheckFailed()
            assertNotNull(cardStatusOf(vm).secondaryButton).onClick()
            runCurrent()

            val empty = cardStatusOf(vm)
            assertEquals(stringRes(R.string.redeemGift_empty_title), empty.title)
            assertEquals(stringRes(R.string.redeemGift_empty_subtitle).withStyle(), empty.subtitle)
            val checkAgain = assertNotNull(empty.secondaryButton)
            assertFalse(checkAgain.isLoading)
            assertTrue(checkAgain.isEnabled)
            assertEquals(2, env.dataSource.checkCount)
        }

    @Test
    fun aDustCardShowsTooLittleForTheFeeWithOnlyClose() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.redeemError = GiftCardException.NothingToRedeem()
            val vm = env.startedVm()

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            runCurrent()

            val dust = cardStatusOf(vm)
            assertEquals(GiftCardStatusState.Background.EMPTY, dust.background)
            assertEquals(imageRes(R.drawable.ic_gift_empty), dust.image)
            assertEquals(stringRes(R.string.redeemGift_empty_title), dust.title)
            assertEquals(stringRes(R.string.redeemGift_empty_subtitle_dust).withStyle(), dust.subtitle)
            assertNull(dust.secondaryButton, "retrying cannot help a dust card, so there is no Check again")
            val close = assertNotNull(dust.primaryButton)
            assertEquals(stringRes(R.string.general_close), close.text)
            assertEquals(ButtonStyle.PRIMARY, close.style)

            close.onClick()
            runCurrent()

            assertEquals(1, env.router.backCount)
            assertEquals(1, env.dataSource.closed.size)
        }

    @Test
    fun checkAgainOnAnEmptyCardChecksTheCardAgain() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.statuses = listOf(GiftCardStatus.Empty, GiftCardSummaryFixture.readyStatus())
            val vm = env.startedVm()
            assertEquals(stringRes(R.string.redeemGift_empty_title), cardStatusOf(vm).title)
            assertEquals(1, env.dataSource.checkCount)

            assertNotNull(cardStatusOf(vm).secondaryButton).onClick()
            runCurrent()

            assertEquals(2, env.dataSource.checkCount)
            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    /**
     * The empty screen is left in the background long enough for its state to stop being collected, but shorter
     * than the idle timeout: back on the screen, Check again still checks the card instead of asking to open it again.
     */
    @Test
    fun checkAgainOnAnEmptyCardShownAgainAfterTheAppWasInTheBackground() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.statuses = listOf(GiftCardStatus.Empty, GiftCardSummaryFixture.readyStatus())
            val vm = env.newVm()
            val collector = backgroundScope.launch { vm.state.collect { } }
            runCurrent()
            assertEquals(stringRes(R.string.redeemGift_empty_title), cardStatusOf(vm).title)

            collector.cancel()
            advanceTimeBy(ANDROID_STATE_FLOW_TIMEOUT + 1.seconds)
            assertTrue(env.dataSource.closed.isEmpty())
            advanceTimeBy(GiftCardRepositoryImpl.IDLE_TIMEOUT - ANDROID_STATE_FLOW_TIMEOUT - 2.seconds)
            backgroundScope.launch { vm.state.collect { } }
            runCurrent()
            assertEquals(stringRes(R.string.redeemGift_empty_title), cardStatusOf(vm).title)

            assertNotNull(cardStatusOf(vm).secondaryButton).onClick()
            runCurrent()

            assertEquals(2, env.dataSource.checkCount)
            assertIs<RedeemGiftState.Ready>(vm.state.value)
            assertTrue(env.dataSource.closed.isEmpty())
        }

    @Test
    fun wrongNetworkCardShowsWrongNetwork() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.parseError = GiftCardException.WrongNetwork()

            assertEquals(stringRes(R.string.redeemGift_wrongNetwork_title), statusOf(env.startedVm()).title)
        }

    @Test
    fun invalidLinkShowsInvalidCard() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.parseError = GiftCardException.InvalidLink()

            assertEquals(stringRes(R.string.redeemGift_invalid_title), statusOf(env.startedVm()).title)
        }

    @Test
    fun unexpectedParseFailureShowsARetryableCheckFailure() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.parseError = IllegalStateException("boom")
            val vm = env.startedVm()

            assertEquals(stringRes(R.string.redeemGift_checkFailed_title), statusOf(vm).title)

            env.dataSource.parseError = null
            requireNotNull(statusOf(vm).primaryButton).onClick()
            runCurrent()

            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun unavailableSdkShowsNotAvailable() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.parseError = GiftCardException.NotAvailable()

            assertEquals(stringRes(R.string.redeemGift_notAvailable_title), statusOf(env.startedVm()).title)
        }

    @Test
    fun unknownLinkIdShowsLinkUnavailableWithoutParsing() =
        runTest(dispatcher) {
            val env = Env(this)
            val vm = env.startedVm(linkId = "missing")

            assertEquals(stringRes(R.string.redeemGift_linkUnavailable_title), statusOf(vm).title)
            assertTrue(env.dataSource.parsedLinks.isEmpty())
        }

    @Test
    fun failedCheckCanBeRetried() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.checkError = IllegalStateException("offline")
            val vm = env.startedVm()

            assertEquals(stringRes(R.string.redeemGift_checkFailed_title), statusOf(vm).title)

            env.dataSource.checkError = null
            requireNotNull(statusOf(vm).primaryButton).onClick()
            runCurrent()

            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun cardInUseDuringRedeemShowsARetryableFailure() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.redeemError = GiftCardException.InUse()
            val vm = env.startedVm()

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            runCurrent()

            assertEquals(stringRes(R.string.redeemGift_failure_title), statusOf(vm).title)
            assertNotNull(statusOf(vm).primaryButton)
        }

    @Test
    fun redeemOfACardWithNothingAboveTheFeeShowsEmpty() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.redeemError = GiftCardException.NothingToRedeem()
            val vm = env.startedVm()

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            runCurrent()

            assertEquals(stringRes(R.string.redeemGift_empty_title), cardStatusOf(vm).title)
        }

    @Test
    fun backEndsTheSessionAndLeaves() =
        runTest(dispatcher) {
            val env = Env(this)
            val vm = env.startedVm()

            vm.state.value
                ?.onBack
                ?.invoke()

            assertEquals(1, env.router.backCount)
            assertEquals(1, env.dataSource.closed.size)
        }

    private fun clear(vm: RedeemGiftVM) {
        val store = ViewModelStore()
        ViewModelProvider(store, SingleVmFactory(vm))[RedeemGiftVM::class.java]
        store.clear()
    }

    private fun statusOf(vm: RedeemGiftVM) = assertIs<RedeemGiftState.Status>(vm.state.value).progress

    private fun cardStatusOf(vm: RedeemGiftVM) = assertIs<RedeemGiftState.CardStatus>(vm.state.value).status

    /**
     * One redeem flow: the link stashed under [linkId], and the repository every view model of this flow shares.
     */
    private inner class Env(
        private val scope: TestScope,
        private val account: WalletAccount? = zashiAccount(),
    ) {
        val dataSource = FakeGiftCardDataSource()
        val store = GiftCardLinkStoreImpl()
        val router = RecordingNavigationRouter()
        val linkId = store.stash(LINK)
        private val repository =
            GiftCardRepositoryImpl(dataSource, store).also {
                it.scope = scope.backgroundScope
                it.quietRecheckMinDuration = Duration.ZERO
            }

        fun startedVm(linkId: String = this.linkId): RedeemGiftVM {
            val vm = newVm(linkId)
            scope.backgroundScope.launch { vm.state.collect { } }
            scope.runCurrent()
            return vm
        }

        /**
         * A view model whose state nobody collects yet. The selected account comes from a [MutableStateFlow], as the
         * app's account flow is backed by an eagerly shared state flow.
         */
        fun newVm(linkId: String = this.linkId): RedeemGiftVM {
            val accounts: Flow<WalletAccount?> = MutableStateFlow(account)
            val getSelectedWalletAccount =
                mockk<GetSelectedWalletAccountUseCase> {
                    every { observe() } returns accounts
                }
            val getDestinationAddress =
                mockk<GetGiftCardDestinationAddressUseCase> {
                    coEvery { this@mockk.invoke() } returns ORCHARD_ADDRESS
                }
            return RedeemGiftVM(
                args = RedeemGiftArgs(linkId),
                giftCardRepository = repository,
                getDestinationAddress = getDestinationAddress,
                navigationRouter = router,
                getSelectedWalletAccount = getSelectedWalletAccount,
            )
        }
    }

    private fun zashiAccount() =
        ZashiAccount(
            sdkAccount = mockk<Account>(relaxed = true),
            unifiedAddress = "unified",
            transparentAddress = "transparent",
            saplingAddress = "sapling",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
        const val ORCHARD_ADDRESS = "u1orchardonly"
    }
}

private class SingleVmFactory(
    private val vm: ViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
}
