package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.datasource.GiftCardDataSource
import co.electriccoin.zcash.ui.common.datasource.ParsedGiftCard
import co.electriccoin.zcash.ui.common.datasource.StoredCardWallet
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardFailure
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardPhase
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.fixture.FakeGiftCardDataSource
import co.electriccoin.zcash.ui.fixture.GiftCardSummaryFixture
import co.electriccoin.zcash.ui.fixture.GiftCardSummaryFixture.pendingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The edges of a [GiftCardRepositoryImpl] session's life: which user intents are ignored in which phase, how each
 * failure is shown, re-checks and polling of a pending card, a dismiss that lands while the SDK is still parsing or
 * checking, and a startup sweep that cannot list or erase every card wallet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GiftCardRepositorySessionLifecycleTest {
    private val dataSource = FakeGiftCardDataSource()

    private val store = GiftCardLinkStoreImpl()

    @Test
    fun giftCardLinksAreRecognisedByTheirShapeOnly() =
        runTest {
            val repository = repository()

            listOf(
                LINK,
                "https://gift.zodl.com#v=1&key=zgift1test&height=3100000",
                "https://link.vizor.cash/payment-links/open#v3=abc",
                "  HTTPS://GIFT.ZODL.COM/#anything\n",
                "https://gift.zodl.com/#"
            ).forEach { assertTrue(repository.isGiftCardLink(it), it) }
            listOf(
                "",
                "zcash:u1someaddress?amount=1",
                "https://gift.zodl.com/",
                "https://gift.zodl.com/v=1&key=zgift1test",
                "https://link.vizor.cash/#v3=abc",
                "https://evil.example/https://gift.zodl.com/#v=1"
            ).forEach { assertFalse(repository.isGiftCardLink(it), it) }
            assertTrue(dataSource.parsedLinks.isEmpty())
        }

    @Test
    fun theStartOfAGiftCardLinkPrefixIsRecognisedWhileTyping() =
        runTest {
            val repository = repository()

            listOf(
                "",
                "  ",
                "h",
                "HTTPS://GIFT",
                " https://gift.zodl.com",
                "https://gift.zodl.com/",
                "https://gift.zodl.com/#",
                "https://link.vizor.cash/payment-links/op"
            ).forEach { assertTrue(repository.isGiftCardLinkStart(it), it) }
            listOf(
                "x",
                "zcash:u1",
                "https://gift.zodl.org",
                "https://gift.zodl.com/#v=1",
                "https://evil.example/https://gift.zodl.com/#"
            ).forEach { assertFalse(repository.isGiftCardLinkStart(it), it) }
        }

    @Test
    fun checkAgainOnAnEmptyCardChecksTheHeldCardAgainWithoutReparsing() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty, GiftCardSummaryFixture.readyStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Empty(), observer.latest().phase)

            repository.checkAgain(linkId)
            runCurrent()

            assertIs<GiftCardPhase.Ready>(observer.latest().phase)
            assertEquals(1, dataSource.parsedLinks.size)
            assertEquals(listOf(GiftCardHandle("fixture-1"), GiftCardHandle("fixture-1")), dataSource.checkedHandles)
            observer.job.cancel()
        }

    @Test
    fun checkAgainIsIgnoredForAReadyCardAndForAnUnknownLink() =
        runTest {
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.checkAgain(linkId)
            repository.checkAgain("missing")
            runCurrent()

            assertEquals(1, dataSource.checkCount)
            assertIs<GiftCardPhase.Ready>(observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun checkAgainIsIgnoredWhileACheckRuns() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty)
            dataSource.checkGate = CompletableDeferred()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.checkAgain(linkId)
            dataSource.checkGate?.complete(Unit)
            runCurrent()

            assertEquals(1, dataSource.checkCount)
            assertEquals(GiftCardPhase.Empty(), observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun checkAgainIsIgnoredWhileTheCardIsRedeemed() =
        runTest {
            dataSource.redeemGate = CompletableDeferred()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            repository.redeem(linkId) { ADDRESS }
            runCurrent()

            repository.checkAgain(linkId)
            runCurrent()

            assertEquals(1, dataSource.checkCount)
            assertEquals(GiftCardPhase.Redeeming, observer.latest().phase)
            dataSource.redeemGate?.complete(Unit)
            runCurrent()
            assertIs<GiftCardPhase.Redeemed>(observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun redeemIsIgnoredUnlessTheCardIsReady() =
        runTest {
            dataSource.statuses = listOf(pendingStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.redeem(linkId) { ADDRESS }
            repository.redeem("missing") { ADDRESS }
            runCurrent()

            assertTrue(dataSource.redeemedTo.isEmpty())
            assertIs<GiftCardPhase.Pending>(observer.latest().phase)
            repository.dismiss(linkId)
            observer.job.cancel()
        }

    @Test
    fun aFailedCheckSaysWhyAndWhetherItCanBeRetried() =
        runTest {
            val expected =
                listOf(
                    GiftCardException.WrongNetwork() to GiftCardPhase.Failed(GiftCardFailure.WRONG_NETWORK),
                    GiftCardException.NotAvailable() to GiftCardPhase.Failed(GiftCardFailure.NOT_AVAILABLE),
                    GiftCardException.UnknownHandle() to GiftCardPhase.Failed(GiftCardFailure.LINK_UNAVAILABLE),
                    GiftCardException.InvalidLink() to GiftCardPhase.Failed(GiftCardFailure.INVALID_LINK),
                    GiftCardException.InUse() to GiftCardPhase.Failed(GiftCardFailure.CHECK_FAILED),
                    GiftCardException.NotChecked() to GiftCardPhase.Failed(GiftCardFailure.CHECK_FAILED),
                    GiftCardException.NothingToRedeem() to GiftCardPhase.Empty(),
                    IllegalStateException("sync") to GiftCardPhase.Failed(GiftCardFailure.CHECK_FAILED)
                )
            expected.forEach { (error, phase) ->
                dataSource.checkError = error
                val repository = repository()
                val linkId = store.stash(LINK)
                val observer = observe(repository.observeSession(linkId))
                runCurrent()

                assertEquals(phase, observer.latest().phase, error::class.simpleName)
                observer.job.cancel()
            }
        }

    @Test
    fun aFailedRecheckTheUserAskedForKeepsTheCardPending() =
        runTest {
            dataSource.statuses = listOf(pendingStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            listOf(GiftCardException.InUse(), IllegalStateException("network")).forEach { error ->
                dataSource.checkError = error
                repository.checkAgain(linkId)
                runCurrent()

                assertEquals(
                    GiftCardPhase.Pending(pendingStatus().pending, isRechecking = false),
                    observer.latest().phase
                )
            }
            assertEquals(3, dataSource.checkCount)
            repository.dismiss(linkId)
            observer.job.cancel()
        }

    @Test
    fun checkAgainOnAnEmptyCardIsAQuietRecheckThatKeepsTheEmptyPhase() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty, GiftCardSummaryFixture.readyStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            val seenBefore = observer.values.size
            dataSource.checkGate = CompletableDeferred()

            repository.checkAgain(linkId)
            runCurrent()

            assertEquals(GiftCardPhase.Empty(isRechecking = true), observer.latest().phase)
            dataSource.checkGate?.complete(Unit)
            runCurrent()
            assertIs<GiftCardPhase.Ready>(observer.latest().phase)
            assertFalse(observer.values.drop(seenBefore).any { it.phase == GiftCardPhase.Checking })
            assertEquals(1, dataSource.parsedLinks.size)
            observer.job.cancel()
        }

    /**
     * The card wallet of a live session answers a check again within milliseconds. Its progress stays on screen for
     * [GiftCardRepositoryImpl.quietRecheckMinDuration] anyway, so that the button visibly does something.
     */
    @Test
    fun aQuietRecheckKeepsItsProgressOnScreenForTheMinimumDuration() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty, GiftCardStatus.Empty)
            val repository = repository()
            repository.quietRecheckMinDuration = 700.milliseconds
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.checkAgain(linkId)
            runCurrent()
            assertEquals(GiftCardPhase.Empty(isRechecking = true), observer.latest().phase)

            advanceTimeBy(699.milliseconds)
            runCurrent()
            assertEquals(GiftCardPhase.Empty(isRechecking = true), observer.latest().phase)

            advanceTimeBy(2.milliseconds)
            runCurrent()
            assertEquals(GiftCardPhase.Empty(isRechecking = false), observer.latest().phase)
            observer.job.cancel()
        }

    /** A check again that takes longer than the minimum is shown as soon as it ends, with no wait added. */
    @Test
    fun aSlowQuietRecheckResolvesAsSoonAsItsCheckEnds() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty, GiftCardStatus.Empty)
            val repository = repository()
            repository.quietRecheckMinDuration = 700.milliseconds
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            dataSource.checkGate = gate

            val start = testScheduler.currentTime
            repository.checkAgain(linkId)
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(GiftCardPhase.Empty(isRechecking = true), observer.latest().phase)

            gate.complete(Unit)
            runCurrent()

            assertEquals(GiftCardPhase.Empty(isRechecking = false), observer.latest().phase)
            assertEquals(2.seconds.inWholeMilliseconds, testScheduler.currentTime - start)
            observer.job.cancel()
        }

    /** A check again that ends before the minimum is shown when the minimum is up, counted from its start. */
    @Test
    fun aFastQuietRecheckResolvesWhenTheMinimumIsUp() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty, GiftCardStatus.Empty)
            val repository = repository()
            repository.quietRecheckMinDuration = 700.milliseconds
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            dataSource.checkGate = gate

            repository.checkAgain(linkId)
            advanceTimeBy(500.milliseconds)
            gate.complete(Unit)
            runCurrent()
            assertEquals(2, dataSource.checkCount)
            assertEquals(GiftCardPhase.Empty(isRechecking = true), observer.latest().phase)

            advanceTimeBy(199.milliseconds)
            runCurrent()
            assertEquals(GiftCardPhase.Empty(isRechecking = true), observer.latest().phase)

            advanceTimeBy(2.milliseconds)
            runCurrent()
            assertEquals(GiftCardPhase.Empty(isRechecking = false), observer.latest().phase)
            observer.job.cancel()
        }

    /** A dismiss while a finished check again waits out its minimum ends the session; no result lands after it. */
    @Test
    fun aDismissDuringTheMinimumOfAQuietRecheckWritesNoPhase() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty, GiftCardStatus.Empty)
            val repository = repository()
            repository.quietRecheckMinDuration = 700.milliseconds
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.checkAgain(linkId)
            advanceTimeBy(300.milliseconds)
            runCurrent()
            assertEquals(2, dataSource.checkCount)
            assertEquals(GiftCardPhase.Empty(isRechecking = true), observer.latest().phase)

            repository.dismiss(linkId)
            runCurrent()
            val seenBeforeTheMinimum = observer.values.size
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(seenBeforeTheMinimum, observer.values.size, "no phase is written after the dismiss")
            assertEquals(GiftCardPhase.Empty(isRechecking = true), observer.latest().phase)
            assertEquals(1, dataSource.closed.size)
            observer.job.cancel()
        }

    @Test
    fun aQuietRecheckOfAnEmptyCardCanFindItPending() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty, pendingStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.checkAgain(linkId)
            runCurrent()

            assertEquals(GiftCardPhase.Pending(pendingStatus().pending), observer.latest().phase)
            repository.dismiss(linkId)
            observer.job.cancel()
        }

    @Test
    fun aFailedRecheckOfAnEmptyCardKeepsItEmptyInsteadOfShowingCheckFailed() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty)
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            listOf(GiftCardException.CheckFailed(), GiftCardException.InUse(), IllegalStateException("network"))
                .forEach { error ->
                    dataSource.checkError = error
                    repository.checkAgain(linkId)
                    runCurrent()

                    assertEquals(GiftCardPhase.Empty(), observer.latest().phase, error::class.simpleName)
                }
            assertFalse(observer.values.any { it.phase is GiftCardPhase.Failed })
            assertEquals(4, dataSource.checkCount)
            observer.job.cancel()
        }

    @Test
    fun checkAgainIsIgnoredWhileAnEmptyCardIsRechecked() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty)
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            dataSource.checkGate = CompletableDeferred()

            repository.checkAgain(linkId)
            repository.checkAgain(linkId)
            runCurrent()
            dataSource.checkGate?.complete(Unit)
            runCurrent()

            assertEquals(2, dataSource.checkCount)
            assertEquals(GiftCardPhase.Empty(), observer.latest().phase)
            observer.job.cancel()
        }

    /**
     * A card checked as redeemable whose redemption is refused as holding nothing above the fee is dust: the fee takes
     * all of it, so Check again is ignored and the phase is final.
     */
    @Test
    fun aRedemptionRefusedForNothingAboveTheFeeLeavesADustCard() =
        runTest {
            dataSource.redeemError = GiftCardException.NothingToRedeem()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.redeem(linkId) { ADDRESS }
            runCurrent()
            assertEquals(GiftCardPhase.Empty(isDust = true), observer.latest().phase)

            repository.checkAgain(linkId)
            runCurrent()
            assertEquals(1, dataSource.checkCount)
            assertEquals(GiftCardPhase.Empty(isDust = true), observer.latest().phase)

            observer.job.cancel()
            runCurrent()
            assertEquals(1, dataSource.closed.size, "a dust card is final, so it is closed once nobody observes it")
            val again = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Empty(isDust = true), again.latest().phase)
            again.job.cancel()
        }

    @Test
    fun anEmptyCardFromACheckIsNotDust() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty)
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            assertEquals(GiftCardPhase.Empty(isDust = false), observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun aRecheckThatIsStillPendingKeepsPolling() =
        runTest {
            dataSource.statuses = listOf(pendingStatus(), pendingStatus(), GiftCardSummaryFixture.readyStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.checkAgain(linkId)
            runCurrent()
            assertEquals(GiftCardPhase.Pending(pendingStatus().pending), observer.latest().phase)

            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL + 1.seconds)

            assertEquals(3, dataSource.checkCount)
            assertIs<GiftCardPhase.Ready>(observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun aPollThatFailsWithAKnownErrorKeepsTheCardPending() =
        runTest {
            dataSource.statuses = listOf(pendingStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            dataSource.checkError = GiftCardException.InUse()
            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL + 1.seconds)

            assertEquals(2, dataSource.checkCount)
            assertEquals(GiftCardPhase.Pending(pendingStatus().pending), observer.latest().phase)
            repository.dismiss(linkId)
            observer.job.cancel()
        }

    @Test
    fun dismissDuringAPollCancelsItsCheck() =
        runTest {
            dataSource.statuses = listOf(pendingStatus(), GiftCardSummaryFixture.readyStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            dataSource.checkGate = CompletableDeferred()
            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL + 1.seconds)
            assertEquals(2, dataSource.checkCount)

            repository.dismiss(linkId)
            dataSource.checkGate?.complete(Unit)
            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL * 3)

            assertEquals(listOf(GiftCardHandle("fixture-1")), dataSource.closed)
            assertEquals(2, dataSource.checkCount)
            assertIs<GiftCardPhase.Pending>(observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun dismissWhileTheLinkIsParsedCancelsTheParse() =
        runTest {
            val source = GatedDataSource(dataSource, isCancellable = true)
            val repository = repository(source)
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.dismiss(linkId)
            source.parseGate.complete(Unit)
            runCurrent()

            assertTrue(dataSource.parsedLinks.isEmpty())
            assertTrue(dataSource.closed.isEmpty())
            assertEquals(0, dataSource.checkCount)
            assertEquals(GiftCardPhase.Checking, observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun aParseThatFinishesAfterDismissClosesTheCardItParsed() =
        runTest {
            val source = GatedDataSource(dataSource, isCancellable = false)
            val repository = repository(source)
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.dismiss(linkId)
            source.parseGate.complete(Unit)
            runCurrent()

            assertEquals(listOf(LINK), dataSource.parsedLinks)
            assertEquals(listOf(GiftCardHandle("fixture-1")), dataSource.closed)
            assertEquals(0, dataSource.checkCount)
            assertEquals(GiftCardPhase.Checking, observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun aCheckThatFinishesAfterDismissChangesNothing() =
        runTest {
            dataSource.statuses = listOf(pendingStatus())
            val source = GatedDataSource(dataSource, isCancellable = false, gateParse = false)
            source.checkGate = CompletableDeferred()
            val repository = repository(source)
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.dismiss(linkId)
            source.checkGate?.complete(Unit)
            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL * 3)

            assertEquals(1, dataSource.checkCount)
            assertEquals(GiftCardPhase.Checking, observer.latest().phase)
            assertEquals(listOf(GiftCardHandle("fixture-1")), dataSource.closed)
            observer.job.cancel()
        }

    @Test
    fun aSecondSweepRequestDoesNotSweepAgain() =
        runTest {
            dataSource.storedWallets = listOf(GiftCardSummaryFixture.storedWallet())
            val repository = repository()

            repository.sweepOrphanedCardWallets()
            repository.sweepOrphanedCardWallets()

            assertEquals(listOf(GiftCardSummaryFixture.storedWallet()), dataSource.erased)
        }

    @Test
    fun aSweepThatCannotListTheWalletsLetsChecksGoOn() =
        runTest {
            val source = GatedDataSource(dataSource, gateParse = false, findError = IllegalStateException("disk"))
            val repository = repository(source)

            repository.sweepOrphanedCardWallets()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            assertTrue(dataSource.erased.isEmpty())
            assertIs<GiftCardPhase.Ready>(observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun aWalletThatCannotBeErasedDoesNotStopTheSweep() =
        runTest {
            val stuck = GiftCardSummaryFixture.storedWallet(alias = "giftcard_stuck")
            val orphan = GiftCardSummaryFixture.storedWallet(alias = "giftcard_orphan")
            dataSource.storedWallets = listOf(stuck, orphan)
            val source = GatedDataSource(dataSource, gateParse = false, eraseErrorFor = stuck)
            val repository = repository(source)

            repository.sweepOrphanedCardWallets()

            assertEquals(listOf(orphan), dataSource.erased)
        }

    private fun TestScope.repository(source: GiftCardDataSource = dataSource) =
        GiftCardRepositoryImpl(
            giftCardDataSource = source,
            giftCardLinkStore = store,
        ).also {
            it.scope = backgroundScope
            it.quietRecheckMinDuration = Duration.ZERO
        }

    /**
     * [delegate] with [parse] held until [parseGate] completes and [check] until [checkGate] does, if set. A
     * non-cancellable gate stands for SDK work that runs to its end once started, like a JNI call. Listing or erasing
     * stored card wallets can be made to fail.
     */
    private class GatedDataSource(
        private val delegate: FakeGiftCardDataSource,
        private val isCancellable: Boolean = true,
        private val gateParse: Boolean = true,
        private val findError: Exception? = null,
        private val eraseErrorFor: StoredCardWallet? = null,
    ) : GiftCardDataSource by delegate {
        val parseGate = CompletableDeferred<Unit>()
        var checkGate: CompletableDeferred<Unit>? = null

        override suspend fun parse(link: String): ParsedGiftCard {
            if (gateParse) pass(parseGate)
            return delegate.parse(link)
        }

        override suspend fun check(handle: GiftCardHandle): GiftCardStatus {
            checkGate?.let { pass(it) }
            return delegate.check(handle)
        }

        override suspend fun findStoredCardWallets(): List<StoredCardWallet> {
            findError?.let { throw it }
            return delegate.findStoredCardWallets()
        }

        override suspend fun eraseCardWallet(wallet: StoredCardWallet) {
            if (wallet == eraseErrorFor) error("busy")
            delegate.eraseCardWallet(wallet)
        }

        private suspend fun pass(gate: CompletableDeferred<Unit>) {
            if (isCancellable) {
                gate.await()
            } else {
                suspendCoroutine { continuation -> gate.invokeOnCompletion { continuation.resume(Unit) } }
            }
        }
    }

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
        const val ADDRESS = "u1orchardonly"
    }
}
