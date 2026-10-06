package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardRedemption
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.common.repository.GiftCardRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.GetGiftCardDestinationAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.fixture.FakeGiftCardDataSource
import co.electriccoin.zcash.ui.fixture.GiftCardSummaryFixture
import co.electriccoin.zcash.ui.fixture.ObserveFiatCurrencyResultFixture
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
            assertEquals(stringRes(Zatoshi(GiftCardSummaryFixture.RECEIVED)), state.amount)
            assertEquals(stringRes(R.string.redeemGift_ready_feeHint), state.feeHint)
            assertEquals(stringRes(GiftCardSummaryFixture.MESSAGE), state.message)
            assertEquals(stringRes(R.string.redeemGift_title), state.title)
            assertEquals(stringRes(R.string.redeemGift_ready_title), state.heading)
            assertEquals(stringRes(R.string.redeemGift_ready_messageLabel), state.messageLabel)
            assertEquals(
                stringRes(R.string.redeemGift_ready_destination, stringRes(R.string.accounts_zashi)),
                state.destination
            )
            assertNull(state.fiatAmount)
        }

    @Test
    fun theDestinationIsUnknownUntilTheSelectedAccountIsButTheFeeHintIsShown() =
        runTest(dispatcher) {
            val vm = Env(this, account = null).startedVm()

            val state = assertIs<RedeemGiftState.Ready>(vm.state.value)
            assertNull(state.destination)
            assertEquals(stringRes(R.string.redeemGift_ready_feeHint), state.feeHint)
        }

    @Test
    fun readyCardWithoutMessageShowsNone() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.summary = GiftCardSummaryFixture.new(message = " ")

            assertNull(assertIs<RedeemGiftState.Ready>(env.startedVm().state.value).message)
        }

    @Test
    fun readyCardShowsTheFiatValueOfWhatTheUserReceives() =
        runTest(dispatcher) {
            mockkObject(FiatCurrency.USD)
            every { FiatCurrency.USD.symbol } returns "$"
            val env = Env(this, exchangeRate = ObserveFiatCurrencyResultFixture.new())
            val netFiat = assertNotNull(assertIs<RedeemGiftState.Ready>(env.startedVm().state.value).fiatAmount)

            val grossEnv = Env(this, exchangeRate = ObserveFiatCurrencyResultFixture.new())
            grossEnv.dataSource.statuses =
                listOf(
                    GiftCardStatus.Ready(
                        spendable = Zatoshi(GiftCardSummaryFixture.AMOUNT),
                        redeemable = Zatoshi(GiftCardSummaryFixture.AMOUNT)
                    )
                )
            val grossFiat = assertNotNull(assertIs<RedeemGiftState.Ready>(grossEnv.startedVm().state.value).fiatAmount)

            assertNotEquals(grossFiat, netFiat, "the fiat amount follows the net amount, not the card's gross funds")
        }

    @Test
    fun checkingHasACloseThatCancelsTheCheckAndEndsTheSession() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.checkGate = CompletableDeferred()
            val vm = env.startedVm()

            val checking = statusOf(vm)
            assertEquals(stringRes(R.string.redeemGift_checking_title), checking.title)
            assertTrue(checking.showAppBar)

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
            val progress = statusOf(vm)
            assertEquals(TransactionProgressState.Background.SUCCESS, progress.background)
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

            assertEquals(stringRes(R.string.redeemGift_success_subtitle_noAmount).withStyle(), statusOf(vm).subtitle)
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
            assertEquals(stringRes(R.string.redeemGift_redeeming_title), statusOf(vm).title)
            vm.state.value
                ?.onBack
                ?.invoke()
            assertEquals(0, env.router.backCount)
            assertTrue(env.dataSource.closed.isEmpty())

            env.dataSource.redeemGate?.complete(Unit)
            runCurrent()
            requireNotNull(statusOf(vm).primaryButton).onClick()
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
            assertEquals(stringRes(R.string.redeemGift_success_title), statusOf(reopened).title)
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
            env.dataSource.statuses = listOf(pending(), GiftCardSummaryFixture.readyStatus())
            val vm = env.startedVm()

            assertEquals(stringRes(R.string.redeemGift_pending_title), statusOf(vm).title)
            assertEquals(1, env.dataSource.checkCount)

            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL + 1.seconds)
            runCurrent()

            assertEquals(2, env.dataSource.checkCount)
            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun checkAgainOnAPendingCardShowsProgressOnTheButton() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.statuses = listOf(pending(), GiftCardSummaryFixture.readyStatus())
            val vm = env.startedVm()

            assertEquals(
                stringRes(R.string.redeemGift_pending_subtitle, stringRes(Zatoshi(GiftCardSummaryFixture.AMOUNT)))
                    .withStyle(),
                statusOf(vm).subtitle
            )
            val checkAgain = assertNotNull(statusOf(vm).primaryButton)
            assertEquals(stringRes(R.string.redeemGift_checkAgain), checkAgain.text)

            env.dataSource.checkGate = CompletableDeferred()
            checkAgain.onClick()
            runCurrent()
            assertEquals(stringRes(R.string.redeemGift_pending_title), statusOf(vm).title)
            assertTrue(assertNotNull(statusOf(vm).primaryButton).isLoading)

            env.dataSource.checkGate?.complete(Unit)
            runCurrent()
            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun emptyCardShowsNothingToRedeem() =
        runTest(dispatcher) {
            val env = Env(this)
            env.dataSource.statuses = listOf(GiftCardStatus.Empty)

            assertEquals(stringRes(R.string.redeemGift_empty_title), statusOf(env.startedVm()).title)
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

            assertEquals(stringRes(R.string.redeemGift_empty_title), statusOf(vm).title)
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

    private fun pending() = GiftCardStatus.Pending(Zatoshi(GiftCardSummaryFixture.AMOUNT))

    /**
     * One redeem flow: the link stashed under [linkId], and the repository every view model of this flow shares.
     */
    private inner class Env(
        private val scope: TestScope,
        private val exchangeRate: ExchangeRateState = ExchangeRateState.OptedOut,
        private val account: WalletAccount? = zashiAccount(),
    ) {
        val dataSource = FakeGiftCardDataSource()
        val store = GiftCardLinkStoreImpl()
        val router = RecordingNavigationRouter()
        val linkId = store.stash(LINK)
        private val repository =
            GiftCardRepositoryImpl(dataSource, store).also { it.scope = scope.backgroundScope }

        fun startedVm(linkId: String = this.linkId): RedeemGiftVM {
            val exchangeRateRepository =
                mockk<ExchangeRateRepository> {
                    every { state } returns MutableStateFlow(exchangeRate)
                }
            val accounts: Flow<WalletAccount?> = flowOf(account)
            val getSelectedWalletAccount =
                mockk<GetSelectedWalletAccountUseCase> {
                    every { observe() } returns accounts
                }
            val getDestinationAddress =
                mockk<GetGiftCardDestinationAddressUseCase> {
                    coEvery { this@mockk.invoke() } returns ORCHARD_ADDRESS
                }
            val vm =
                RedeemGiftVM(
                    args = RedeemGiftArgs(linkId),
                    giftCardRepository = repository,
                    getDestinationAddress = getDestinationAddress,
                    navigationRouter = router,
                    exchangeRateRepository = exchangeRateRepository,
                    getSelectedWalletAccount = getSelectedWalletAccount,
                )
            scope.backgroundScope.launch { vm.state.collect { } }
            scope.runCurrent()
            return vm
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
