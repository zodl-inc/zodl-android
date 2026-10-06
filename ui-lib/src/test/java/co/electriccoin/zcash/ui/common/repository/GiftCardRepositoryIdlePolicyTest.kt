package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.GiftCardDataSource
import co.electriccoin.zcash.ui.common.datasource.ParsedGiftCard
import co.electriccoin.zcash.ui.common.datasource.StoredCardWallet
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardFailure
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardPhase
import co.electriccoin.zcash.ui.common.model.GiftCardSession
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.fixture.FakeGiftCardDataSource
import co.electriccoin.zcash.ui.fixture.GiftCardSummaryFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * A [GiftCardRepositoryImpl] session whose screen went away without a dismiss (a back press while a redemption
 * starts, a back stack reset, the wallet being deleted) must not keep its card wallet syncing and its key in memory
 * until the process dies: it closes its card once final and unobserved, or once unobserved for the idle timeout,
 * never while redeeming, and is forgotten after another idle timeout. Also: foreign cancellations from the data
 * source, and closing every session when the wallet is deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GiftCardRepositoryIdlePolicyTest {
    private val dataSource = FakeGiftCardDataSource()

    private val store = GiftCardLinkStoreImpl()

    @Test
    fun aRedemptionThatEndsUnobservedClosesItsCardRightAway() =
        runTest {
            dataSource.redeemGate = CompletableDeferred()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            repository.redeem(linkId) { ADDRESS }
            runCurrent()

            observer.job.cancel()
            advanceTimeBy(IDLE + 1.seconds)
            assertTrue(dataSource.closed.isEmpty(), "a card being redeemed is never closed")

            dataSource.redeemGate?.complete(Unit)
            runCurrent()

            assertEquals(listOf(HANDLE), dataSource.closed)
            val again = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Redeemed(dataSource.redemption), again.latest().phase)
            again.job.cancel()
        }

    /** A retryable redeem failure is not final, so its card is closed only after the idle timeout. */
    @Test
    fun aRedemptionThatFailsUnobservedClosesItsCardAfterTheIdleTimeout() =
        runTest {
            dataSource.redeemGate = CompletableDeferred()
            dataSource.redeemError = GiftCardException.SubmitFailed()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            repository.redeem(linkId) { ADDRESS }
            runCurrent()
            observer.job.cancel()
            advanceTimeBy(IDLE + 1.seconds)

            dataSource.redeemGate?.complete(Unit)
            runCurrent()
            assertTrue(dataSource.closed.isEmpty())

            advanceTimeBy(IDLE + 1.seconds)
            assertEquals(listOf(HANDLE), dataSource.closed)
        }

    @Test
    fun aFinalPhaseClosesTheCardOnceNobodyObservesIt() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty)
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            assertTrue(dataSource.closed.isEmpty(), "an observed card stays open, so that it can be checked again")

            observer.job.cancel()
            runCurrent()

            assertEquals(listOf(HANDLE), dataSource.closed)
        }

    /** Checking a closed card again cannot work: the screen says to open the card again instead of doing nothing. */
    @Test
    fun checkingAClosedCardAgainAsksToOpenItAgain() =
        runTest {
            dataSource.statuses = listOf(GiftCardStatus.Empty)
            val repository = repository()
            val linkId = store.stash(LINK)
            observe(repository.observeSession(linkId)).job.cancel()
            runCurrent()

            val again = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Empty, again.latest().phase)
            repository.checkAgain(linkId)
            runCurrent()

            assertEquals(GiftCardPhase.Failed(GiftCardFailure.LINK_UNAVAILABLE), again.latest().phase)
            assertEquals(1, dataSource.checkCount)
            again.job.cancel()
        }

    @Test
    fun anUnfinishedSessionClosesItsCardOnlyAfterTheIdleTimeout() =
        runTest {
            dataSource.statuses = listOf(pending())
            val repository = repository()
            val linkId = store.stash(LINK)
            observe(repository.observeSession(linkId)).job.cancel()
            runCurrent()

            advanceTimeBy(IDLE - 1.seconds)
            assertTrue(dataSource.closed.isEmpty())
            advanceTimeBy(2.seconds)
            assertEquals(listOf(HANDLE), dataSource.closed)

            val again = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Failed(GiftCardFailure.LINK_UNAVAILABLE), again.latest().phase)
            assertEquals(1, dataSource.checkCount, "a closed card is not polled")
            again.job.cancel()
        }

    @Test
    fun anObserverReturningInTimeKeepsTheCardOpen() =
        runTest {
            val repository = repository()
            val linkId = store.stash(LINK)
            observe(repository.observeSession(linkId)).job.cancel()
            runCurrent()

            advanceTimeBy(IDLE - 1.seconds)
            val again = observe(repository.observeSession(linkId))
            advanceTimeBy(IDLE * 2)

            assertTrue(dataSource.closed.isEmpty())
            assertIs<GiftCardPhase.Ready>(again.latest().phase)
            again.job.cancel()
        }

    @Test
    fun aCheckThatOutlastsTheIdleTimeoutUnobservedIsCancelled() =
        runTest {
            dataSource.checkGate = CompletableDeferred()
            val repository = repository()
            val linkId = store.stash(LINK)
            observe(repository.observeSession(linkId)).job.cancel()
            runCurrent()

            advanceTimeBy(IDLE + 1.seconds)
            dataSource.checkGate?.complete(Unit)
            runCurrent()

            assertEquals(listOf(HANDLE), dataSource.closed)
            val again = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Failed(GiftCardFailure.LINK_UNAVAILABLE), again.latest().phase)
            again.job.cancel()
        }

    /**
     * A closed session that nobody observes for another idle timeout is forgotten: observing its link id then finds
     * no session, rather than the redeemed one.
     */
    @Test
    fun aClosedSessionIsForgottenAfterAnotherIdleTimeout() =
        runTest {
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            repository.redeem(linkId) { ADDRESS }
            runCurrent()
            observer.job.cancel()
            runCurrent()
            assertEquals(listOf(HANDLE), dataSource.closed)

            advanceTimeBy(IDLE + 1.seconds)

            val again = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Failed(GiftCardFailure.LINK_UNAVAILABLE), again.latest().phase)
            again.job.cancel()
        }

    @Test
    fun closingAllSessionsClosesEveryCardIncludingOneBeingRedeemed() =
        runTest {
            dataSource.redeemGate = CompletableDeferred()
            val repository = repository()
            val redeemingId = store.stash(LINK)
            val redeeming = observe(repository.observeSession(redeemingId))
            runCurrent()
            repository.redeem(redeemingId) { ADDRESS }
            dataSource.walletAlias = "giftcard_other"
            val otherId = store.stash(OTHER_LINK)
            val other = observe(repository.observeSession(otherId))
            runCurrent()

            repository.closeAllSessions()
            dataSource.redeemGate?.complete(Unit)
            runCurrent()

            assertEquals(setOf(HANDLE, GiftCardHandle("fixture-2")), dataSource.closed.toSet())
            assertEquals(listOf(ADDRESS), dataSource.redeemedTo, "the redemption itself runs to its end")
            redeeming.job.cancel()
            other.job.cancel()
        }

    @Test
    fun aCancellationFromInsideTheDataSourceIsAFailureNotASilentEnd() =
        runTest {
            val expected =
                listOf(
                    Triple(FakeGiftCardDataSource(parseError = foreignCancellation()), false, CHECK_FAILED),
                    Triple(FakeGiftCardDataSource(checkError = foreignCancellation()), false, CHECK_FAILED),
                    Triple(FakeGiftCardDataSource(redeemError = foreignCancellation()), true, REDEEM_FAILED)
                )
            expected.forEach { (source, redeems, phase) ->
                val repository = repository(source)
                val linkId = store.stash(LINK)
                val observer = observe(repository.observeSession(linkId))
                runCurrent()
                if (redeems) {
                    repository.redeem(linkId) { ADDRESS }
                    runCurrent()
                }

                assertEquals(phase, observer.latest().phase)
                observer.job.cancel()
            }
        }

    @Test
    fun aCancellationFromInsideAPollKeepsTheCardPending() =
        runTest {
            dataSource.statuses = listOf(pending())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            dataSource.checkError = foreignCancellation()
            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL + 1.seconds)

            assertEquals(2, dataSource.checkCount)
            assertEquals(GiftCardPhase.Pending(pending().pending), observer.latest().phase)
            repository.dismiss(linkId)
            observer.job.cancel()
        }

    @Test
    fun aSweepCancelledFromInsideLetsChecksGoOn() =
        runTest {
            val listing = SweepingDataSource(dataSource, findError = foreignCancellation())
            val repository = repository(listing)

            repository.sweepOrphanedCardWallets()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            assertIs<GiftCardPhase.Ready>(observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun anEraseCancelledFromInsideDoesNotStopTheSweep() =
        runTest {
            val stuck = GiftCardSummaryFixture.storedWallet(alias = "giftcard_stuck")
            val orphan = GiftCardSummaryFixture.storedWallet(alias = "giftcard_orphan")
            dataSource.storedWallets = listOf(stuck, orphan)
            val repository = repository(SweepingDataSource(dataSource, eraseErrorFor = stuck))

            repository.sweepOrphanedCardWallets()

            assertEquals(listOf(orphan), dataSource.erased)
        }

    /**
     * The link is dismissed while it is parsed, and the parse finds a card that already has a live session: the card
     * this parse holds is closed, and the live session is left alone.
     */
    @Test
    fun aDismissWhileAJoiningLinkIsParsedLeavesTheLiveSessionAlone() =
        runTest {
            val source = ParseGatedDataSource(dataSource)
            val repository = repository(source)
            val liveId = store.stash(LINK)
            source.parseGate.complete(Unit)
            val live = observe(repository.observeSession(liveId))
            runCurrent()

            source.parseGate = CompletableDeferred()
            val joiningId = store.stash(LINK)
            val joining = observe(repository.observeSession(joiningId))
            runCurrent()
            repository.dismiss(joiningId)
            source.parseGate.complete(Unit)
            runCurrent()

            assertEquals(listOf(GiftCardHandle("fixture-2")), dataSource.closed)
            assertIs<GiftCardPhase.Ready>(live.latest().phase)
            repository.redeem(liveId) { ADDRESS }
            runCurrent()
            assertIs<GiftCardPhase.Redeemed>(live.latest().phase)
            live.job.cancel()
            joining.job.cancel()
        }

    private fun TestScope.repository(source: GiftCardDataSource = dataSource) =
        GiftCardRepositoryImpl(
            giftCardDataSource = source,
            giftCardLinkStore = store,
        ).also {
            it.scope = backgroundScope
            it.idleTimeout = IDLE
        }

    private fun TestScope.observe(flow: Flow<GiftCardSession>): Observer {
        val observer = Observer()
        observer.job = launch { flow.collect { observer.values += it } }
        return observer
    }

    private class Observer {
        val values = mutableListOf<GiftCardSession>()
        lateinit var job: Job

        fun latest(): GiftCardSession = assertNotNull(values.lastOrNull())
    }

    private fun pending() = GiftCardStatus.Pending(Zatoshi(GiftCardSummaryFixture.AMOUNT))

    /** Thrown by the data source while the caller is not cancelled. */
    private fun foreignCancellation() = CancellationException("thrown from inside")

    /** [delegate] whose listing, or erase of one wallet, fails. */
    private class SweepingDataSource(
        private val delegate: FakeGiftCardDataSource,
        private val findError: Throwable? = null,
        private val eraseErrorFor: StoredCardWallet? = null,
    ) : GiftCardDataSource by delegate {
        override suspend fun findStoredCardWallets(): List<StoredCardWallet> {
            findError?.let { throw it }
            return delegate.findStoredCardWallets()
        }

        override suspend fun eraseCardWallet(wallet: StoredCardWallet) {
            if (wallet == eraseErrorFor) throw CancellationException("thrown from inside")
            delegate.eraseCardWallet(wallet)
        }
    }

    /** [delegate] whose [parse] waits for [parseGate], without being cancellable, like a JNI call. */
    private class ParseGatedDataSource(
        private val delegate: FakeGiftCardDataSource,
    ) : GiftCardDataSource by delegate {
        var parseGate = CompletableDeferred<Unit>()

        override suspend fun parse(link: String): ParsedGiftCard {
            val gate = parseGate
            suspendCoroutine { continuation -> gate.invokeOnCompletion { continuation.resume(Unit) } }
            return delegate.parse(link)
        }
    }

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
        const val OTHER_LINK = "https://gift.zodl.com/#v=1&key=zgift1other&height=3100000"
        const val ADDRESS = "u1orchardonly"
        val HANDLE = GiftCardHandle("fixture-1")
        val IDLE = 120.seconds
        val CHECK_FAILED = GiftCardPhase.Failed(GiftCardFailure.CHECK_FAILED)
        val REDEEM_FAILED = GiftCardPhase.Failed(GiftCardFailure.REDEEM_FAILED)
    }
}
