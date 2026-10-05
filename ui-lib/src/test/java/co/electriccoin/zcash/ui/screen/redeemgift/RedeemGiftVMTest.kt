package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.common.usecase.GetGiftCardDestinationAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.fixture.FakeGiftCardRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The gift card redeem flow against [FakeGiftCardRepository]: each card state maps to its screen, redeeming sends
 * to the selected account's Orchard address, pending cards are re-checked automatically, failures can be retried,
 * and the card is always cleaned up when the screen goes away.
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

    @Test
    fun readyCardShowsAmountAndSenderMessage() =
        runTest(dispatcher) {
            val vm = startedVm()

            val state = assertIs<RedeemGiftState.Ready>(vm.state.value)
            assertEquals(stringRes(Zatoshi(GiftCardSummaryFixture.AMOUNT)), state.amount)
            assertEquals(stringRes(GiftCardSummaryFixture.MESSAGE), state.message)
            assertNull(state.fiatAmount)
        }

    @Test
    fun readyCardWithoutMessageShowsNone() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository(summary = GiftCardSummaryFixture.new(message = " "))
            val vm = startedVm(repository = repository)

            assertNull(assertIs<RedeemGiftState.Ready>(vm.state.value).message)
        }

    @Test
    fun readyCardShowsFiatWhenAnExchangeRateIsAvailable() =
        runTest(dispatcher) {
            // The currency symbol comes from android.icu, which is a stub on the JVM.
            mockkObject(FiatCurrency.USD)
            every { FiatCurrency.USD.symbol } returns "$"
            val vm = startedVm(exchangeRate = ObserveFiatCurrencyResultFixture.new())

            assertNotNull(assertIs<RedeemGiftState.Ready>(vm.state.value).fiatAmount)
        }

    @Test
    fun redeemSendsToTheOrchardAddressAndShowsSuccess() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository()
            val vm = startedVm(repository = repository)

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            advanceUntilIdle()

            assertEquals(listOf(ORCHARD_ADDRESS), repository.redeemedTo)
            val progress = statusOf(vm)
            assertEquals(TransactionProgressState.Background.SUCCESS, progress.background)
            assertEquals(stringRes(R.string.redeemGift_success_title), progress.title)
        }

    @Test
    fun backIsIgnoredWhileRedeemingAndSuccessCloseGoesHome() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository().apply { redeemGate = CompletableDeferred() }
            val router = RecordingNavigationRouter()
            val vm = startedVm(repository = repository, router = router)

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            advanceUntilIdle()
            assertEquals(stringRes(R.string.redeemGift_redeeming_title), statusOf(vm).title)
            vm.state.value.onBack()
            assertEquals(0, router.backCount)

            repository.redeemGate?.complete(Unit)
            advanceUntilIdle()
            requireNotNull(statusOf(vm).primaryButton).onClick()
            assertEquals(1, router.backToRootCount)
        }

    @Test
    fun checkProgressIsShownWhileChecking() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository().apply { checkGate = CompletableDeferred() }
            val vm = startedVm(repository = repository)

            assertEquals(stringRes(R.string.redeemGift_checking_title), statusOf(vm).title)
            repository.progress.value = 0.42f
            advanceUntilIdle()
            assertEquals(stringRes(R.string.redeemGift_checking_progress, 42).withStyle(), statusOf(vm).subtitle)

            repository.checkGate?.complete(Unit)
            advanceUntilIdle()
            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun pendingCardIsRecheckedAutomaticallyUntilReady() =
        runTest(dispatcher) {
            val repository =
                FakeGiftCardRepository(
                    statuses =
                        listOf(
                            GiftCardStatus.Pending(Zatoshi(GiftCardSummaryFixture.AMOUNT)),
                            GiftCardStatus.Ready(Zatoshi(GiftCardSummaryFixture.AMOUNT))
                        )
                )
            val vm = startedVm(repository = repository)

            assertEquals(stringRes(R.string.redeemGift_pending_title), statusOf(vm).title)
            assertEquals(1, repository.checkCount)

            advanceTimeBy(RedeemGiftVM.PENDING_RETRY_INTERVAL + 1.seconds)
            runCurrent()

            assertEquals(2, repository.checkCount)
            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun checkAgainOnAPendingCardShowsProgressOnTheButton() =
        runTest(dispatcher) {
            val repository =
                FakeGiftCardRepository(
                    statuses =
                        listOf(
                            GiftCardStatus.Pending(Zatoshi(GiftCardSummaryFixture.AMOUNT)),
                            GiftCardStatus.Pending(Zatoshi(GiftCardSummaryFixture.AMOUNT)),
                            GiftCardStatus.Ready(Zatoshi(GiftCardSummaryFixture.AMOUNT))
                        )
                )
            val vm = startedVm(repository = repository)

            assertEquals(
                stringRes(R.string.redeemGift_pending_subtitle, stringRes(Zatoshi(GiftCardSummaryFixture.AMOUNT)))
                    .withStyle(),
                statusOf(vm).subtitle
            )
            val checkAgain = assertNotNull(statusOf(vm).primaryButton)
            assertEquals(stringRes(R.string.redeemGift_checkAgain), checkAgain.text)

            // "Check again" re-checks in place: the pending screen stays, the button shows it is working.
            repository.checkGate = CompletableDeferred()
            checkAgain.onClick()
            runCurrent()
            assertEquals(stringRes(R.string.redeemGift_pending_title), statusOf(vm).title)
            assertTrue(assertNotNull(statusOf(vm).primaryButton).isLoading)
            assertEquals(2, repository.checkCount)

            repository.checkGate?.complete(Unit)
            runCurrent()
            assertEquals(stringRes(R.string.redeemGift_pending_title), statusOf(vm).title)
            assertTrue(!assertNotNull(statusOf(vm).primaryButton).isLoading)

            // The quiet re-check loop resumes after the manual check.
            advanceTimeBy(RedeemGiftVM.PENDING_RETRY_INTERVAL + 1.seconds)
            runCurrent()
            assertEquals(3, repository.checkCount)
            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun failingQuietRecheckKeepsThePendingScreen() =
        runTest(dispatcher) {
            val repository =
                FakeGiftCardRepository(
                    statuses = listOf(GiftCardStatus.Pending(Zatoshi(GiftCardSummaryFixture.AMOUNT)))
                )
            val vm = startedVm(repository = repository)

            repository.checkError = IllegalStateException("network")
            advanceTimeBy(RedeemGiftVM.PENDING_RETRY_INTERVAL + 1.seconds)
            runCurrent()

            assertEquals(2, repository.checkCount)
            assertEquals(stringRes(R.string.redeemGift_pending_title), statusOf(vm).title)

            // The card stays pending forever here; stop the re-check loop so the test can finish.
            clear(vm)
        }

    @Test
    fun emptyCardShowsNothingToRedeem() =
        runTest(dispatcher) {
            val vm = startedVm(repository = FakeGiftCardRepository(statuses = listOf(GiftCardStatus.Empty)))

            assertEquals(stringRes(R.string.redeemGift_empty_title), statusOf(vm).title)
        }

    @Test
    fun wrongNetworkCardShowsWrongNetwork() =
        runTest(dispatcher) {
            val vm = startedVm(repository = FakeGiftCardRepository(parseError = GiftCardException.WrongNetwork()))

            assertEquals(stringRes(R.string.redeemGift_wrongNetwork_title), statusOf(vm).title)
        }

    @Test
    fun invalidLinkShowsInvalidCard() =
        runTest(dispatcher) {
            val vm = startedVm(repository = FakeGiftCardRepository(parseError = GiftCardException.InvalidLink()))

            assertEquals(stringRes(R.string.redeemGift_invalid_title), statusOf(vm).title)
        }

    @Test
    fun unexpectedParseFailureShowsInvalidCard() =
        runTest(dispatcher) {
            val vm = startedVm(repository = FakeGiftCardRepository(parseError = IllegalStateException("boom")))

            assertEquals(stringRes(R.string.redeemGift_invalid_title), statusOf(vm).title)
        }

    @Test
    fun unavailableSdkShowsNotAvailable() =
        runTest(dispatcher) {
            val vm = startedVm(repository = FakeGiftCardRepository(parseError = GiftCardException.NotAvailable()))

            assertEquals(stringRes(R.string.redeemGift_notAvailable_title), statusOf(vm).title)
        }

    @Test
    fun unknownLinkIdShowsLinkUnavailableWithoutParsing() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository()
            val vm = startedVm(repository = repository, stashLink = false)

            assertEquals(stringRes(R.string.redeemGift_linkUnavailable_title), statusOf(vm).title)
            assertTrue(repository.parsedLinks.isEmpty())
        }

    @Test
    fun linkIsTakenOutOfTheStore() =
        runTest(dispatcher) {
            val store = GiftCardLinkStoreImpl()
            val id = store.stash(LINK)
            val repository = FakeGiftCardRepository()
            startedVm(repository = repository, store = store, linkId = id)

            assertEquals(listOf(LINK), repository.parsedLinks)
            assertNull(store.take(id))
        }

    @Test
    fun failedCheckCanBeRetried() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository(checkError = IllegalStateException("offline"))
            val vm = startedVm(repository = repository)

            assertEquals(stringRes(R.string.redeemGift_checkFailed_title), statusOf(vm).title)

            repository.checkError = null
            requireNotNull(statusOf(vm).primaryButton).onClick()
            advanceUntilIdle()

            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun failedRedeemIsRetriedThroughAFreshCheck() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository(redeemError = IllegalStateException("rejected"))
            val vm = startedVm(repository = repository)

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            advanceUntilIdle()
            assertEquals(stringRes(R.string.redeemGift_failure_title), statusOf(vm).title)

            repository.statuses = listOf(GiftCardStatus.Empty)
            requireNotNull(statusOf(vm).primaryButton).onClick()
            advanceUntilIdle()

            assertEquals(2, repository.checkCount)
            assertEquals(1, repository.redeemedTo.size)
            assertEquals(stringRes(R.string.redeemGift_empty_title), statusOf(vm).title)
        }

    @Test
    fun cardInUseDuringCheckCanBeRetried() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository(checkError = GiftCardException.InUse())
            val vm = startedVm(repository = repository)

            assertEquals(stringRes(R.string.redeemGift_checkFailed_title), statusOf(vm).title)
            assertNotNull(statusOf(vm).secondaryButton)

            repository.checkError = null
            requireNotNull(statusOf(vm).primaryButton).onClick()
            advanceUntilIdle()

            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun cardInUseDuringRedeemShowsARetryableFailure() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository(redeemError = GiftCardException.InUse())
            val vm = startedVm(repository = repository)

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            advanceUntilIdle()

            assertEquals(stringRes(R.string.redeemGift_failure_title), statusOf(vm).title)
            assertNotNull(statusOf(vm).primaryButton)
        }

    @Test
    fun redeemOfAnUncheckedCardChecksItAgain() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository(redeemError = GiftCardException.NotChecked())
            val vm = startedVm(repository = repository)

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            advanceUntilIdle()

            assertEquals(2, repository.checkCount)
            assertIs<RedeemGiftState.Ready>(vm.state.value)
        }

    @Test
    fun redeemOfACardWithNothingAboveTheFeeShowsEmpty() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository(redeemError = GiftCardException.NothingToRedeem())
            val vm = startedVm(repository = repository)

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            advanceUntilIdle()

            assertEquals(stringRes(R.string.redeemGift_empty_title), statusOf(vm).title)
        }

    @Test
    fun unsubmittedRedeemShowsFailure() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository(redeemError = GiftCardException.SubmitFailed())
            val vm = startedVm(repository = repository)

            assertIs<RedeemGiftState.Ready>(vm.state.value).redeemButton.onClick()
            advanceUntilIdle()

            assertEquals(stringRes(R.string.redeemGift_failure_title), statusOf(vm).title)
            assertNotNull(statusOf(vm).primaryButton)
        }

    @Test
    fun backLeavesTheFlow() =
        runTest(dispatcher) {
            val router = RecordingNavigationRouter()
            val vm = startedVm(router = router)

            vm.state.value.onBack()

            assertEquals(1, router.backCount)
        }

    @Test
    fun cardIsCleanedUpWhenTheScreenGoesAway() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository()
            clear(startedVm(repository = repository))

            assertEquals(listOf(GiftCardSummaryFixture.new().handle), repository.cleanedUp)
        }

    @Test
    fun nothingToCleanUpWhenTheLinkNeverParsed() =
        runTest(dispatcher) {
            val repository = FakeGiftCardRepository(parseError = GiftCardException.InvalidLink())
            clear(startedVm(repository = repository))

            assertTrue(repository.cleanedUp.isEmpty())
        }

    private fun clear(vm: RedeemGiftVM) {
        val store = ViewModelStore()
        ViewModelProvider(store, SingleVmFactory(vm))[RedeemGiftVM::class.java]
        store.clear()
    }

    private fun statusOf(vm: RedeemGiftVM) = assertIs<RedeemGiftState.Status>(vm.state.value).progress

    @Suppress("LongParameterList")
    private fun TestScope.startedVm(
        repository: FakeGiftCardRepository = FakeGiftCardRepository(),
        router: RecordingNavigationRouter = RecordingNavigationRouter(),
        exchangeRate: ExchangeRateState = ExchangeRateState.OptedOut,
        store: GiftCardLinkStoreImpl = GiftCardLinkStoreImpl(),
        stashLink: Boolean = true,
        linkId: String? = null,
    ): RedeemGiftVM {
        val id = linkId ?: if (stashLink) store.stash(LINK) else "missing"
        val exchangeRateRepository =
            mockk<ExchangeRateRepository> {
                every { state } returns MutableStateFlow(exchangeRate)
            }
        val getSelectedWalletAccount =
            mockk<GetSelectedWalletAccountUseCase> {
                every { observe() } returns flowOf(zashiAccount())
            }
        val getDestinationAddress =
            mockk<GetGiftCardDestinationAddressUseCase> {
                coEvery { this@mockk.invoke() } returns ORCHARD_ADDRESS
            }
        val vm =
            RedeemGiftVM(
                args = RedeemGiftArgs(id),
                giftCardLinkStore = store,
                giftCardRepository = repository,
                getDestinationAddress = getDestinationAddress,
                navigationRouter = router,
                exchangeRateRepository = exchangeRateRepository,
                getSelectedWalletAccount = getSelectedWalletAccount,
            )
        backgroundScope.launch { vm.state.collect { } }
        runCurrent()
        return vm
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
