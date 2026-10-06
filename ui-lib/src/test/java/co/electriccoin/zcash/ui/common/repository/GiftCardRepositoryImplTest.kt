package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardFailure
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardPhase
import co.electriccoin.zcash.ui.common.model.GiftCardRedemption
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.fixture.FakeGiftCardDataSource
import co.electriccoin.zcash.ui.fixture.GiftCardSummaryFixture
import co.electriccoin.zcash.ui.fixture.GiftCardSummaryFixture.pendingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * [GiftCardRepositoryImpl] owns each redemption as a session in its own scope: the session survives its observers
 * going away, a screen opened again for the same card joins it, pending cards are polled only while observed, and
 * the card wallet is erased when the session is dismissed (never while redeeming), when nobody observes it any more
 * (see [GiftCardRepositoryIdlePolicyTest]) or when found orphaned at startup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GiftCardRepositoryImplTest {
    private val dataSource = FakeGiftCardDataSource()

    private val store = GiftCardLinkStoreImpl()

    @Test
    fun aSessionParsesAndChecksTheCardInTheRepositoryScope() =
        runTest {
            val repository = repository()
            val linkId = store.stash(LINK)

            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            assertEquals(listOf(LINK), dataSource.parsedLinks)
            assertNull(store.take(linkId))
            val session = observer.latest()
            assertEquals(GiftCardSummaryFixture.MESSAGE, session.summary?.message)
            assertEquals(
                GiftCardPhase.Ready(
                    spendable = Zatoshi(GiftCardSummaryFixture.AMOUNT),
                    redeemable = Zatoshi(GiftCardSummaryFixture.RECEIVED)
                ),
                session.phase
            )
            observer.job.cancel()
        }

    @Test
    fun workSurvivesItsObserverGoingAway() =
        runTest {
            dataSource.checkGate = CompletableDeferred()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Checking, observer.latest().phase)

            observer.job.cancel()
            dataSource.checkGate?.complete(Unit)
            runCurrent()

            val again = observe(repository.observeSession(linkId))
            runCurrent()
            assertIs<GiftCardPhase.Ready>(again.latest().phase)
            assertEquals(1, dataSource.checkCount)
            assertTrue(dataSource.closed.isEmpty())
            again.job.cancel()
        }

    @Test
    fun observingTheSameLinkAgainReattachesWithoutStartingOver() =
        runTest {
            val repository = repository()
            val linkId = store.stash(LINK)
            val first = observe(repository.observeSession(linkId))
            runCurrent()
            first.job.cancel()

            val second = observe(repository.observeSession(linkId))
            runCurrent()

            assertEquals(1, dataSource.parsedLinks.size)
            assertEquals(1, dataSource.checkCount)
            assertIs<GiftCardPhase.Ready>(second.latest().phase)
            second.job.cancel()
        }

    @Test
    fun aNewLinkForACardWithALiveSessionJoinsIt() =
        runTest {
            val repository = repository()
            val firstId = store.stash(LINK)
            val first = observe(repository.observeSession(firstId))
            runCurrent()

            val secondId = store.stash(LINK)
            val second = observe(repository.observeSession(secondId))
            runCurrent()

            assertEquals(2, dataSource.parsedLinks.size)
            assertEquals(1, dataSource.checkCount)
            assertEquals(listOf(GiftCardHandle("fixture-2")), dataSource.closed)
            assertEquals(first.latest(), second.latest())

            repository.dismiss(secondId)
            assertEquals(listOf(GiftCardHandle("fixture-2"), GiftCardHandle("fixture-1")), dataSource.closed)
            first.job.cancel()
            second.job.cancel()
        }

    @Test
    fun aPendingCardIsPolledOnlyWhileTheSessionIsObserved() =
        runTest {
            dataSource.statuses = listOf(pendingStatus(), pendingStatus(), GiftCardSummaryFixture.readyStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            assertIs<GiftCardPhase.Pending>(observer.latest().phase)

            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL + 1.seconds)
            assertEquals(2, dataSource.checkCount)

            observer.job.cancel()
            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL * 3)
            assertEquals(2, dataSource.checkCount, "no polling without observers")

            val again = observe(repository.observeSession(linkId))
            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL + 1.seconds)
            assertEquals(3, dataSource.checkCount)
            assertIs<GiftCardPhase.Ready>(again.latest().phase)
            again.job.cancel()
        }

    @Test
    fun aFailingQuietRecheckKeepsTheCardPending() =
        runTest {
            dataSource.statuses = listOf(pendingStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            dataSource.checkError = IllegalStateException("network")
            advanceTimeBy(GiftCardRepositoryImpl.PENDING_RETRY_INTERVAL + 1.seconds)

            assertEquals(2, dataSource.checkCount)
            assertIs<GiftCardPhase.Pending>(observer.latest().phase)
            repository.dismiss(linkId)
            observer.job.cancel()
        }

    @Test
    fun checkAgainOnAPendingCardRechecksInPlace() =
        runTest {
            dataSource.statuses = listOf(pendingStatus(), GiftCardSummaryFixture.readyStatus())
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            dataSource.checkGate = CompletableDeferred()
            repository.checkAgain(linkId)
            runCurrent()
            assertEquals(pendingStatus().pending, assertIs<GiftCardPhase.Pending>(observer.latest().phase).pending)
            assertTrue(assertIs<GiftCardPhase.Pending>(observer.latest().phase).isRechecking)

            dataSource.checkGate?.complete(Unit)
            runCurrent()
            assertIs<GiftCardPhase.Ready>(observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun dismissClosesTheCardAndForgetsTheSession() =
        runTest {
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.dismiss(linkId)
            repository.dismiss(linkId)

            assertEquals(listOf(GiftCardHandle("fixture-1")), dataSource.closed)
            observer.job.cancel()
            val reopened = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Failed(GiftCardFailure.LINK_UNAVAILABLE), reopened.latest().phase)
            reopened.job.cancel()
        }

    @Test
    fun dismissWhileCheckingCancelsTheCheckAndClosesTheCard() =
        runTest {
            dataSource.checkGate = CompletableDeferred()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.dismiss(linkId)
            dataSource.checkGate?.complete(Unit)
            runCurrent()

            assertEquals(listOf(GiftCardHandle("fixture-1")), dataSource.closed)
            assertEquals(GiftCardPhase.Checking, observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun dismissBeforeTheLinkIsParsedDropsTheLink() =
        runTest {
            val repository = repository()
            val linkId = store.stash(LINK)

            repository.observeSession(linkId)
            repository.dismiss(linkId)
            runCurrent()

            assertNull(store.take(linkId))
            assertTrue(dataSource.parsedLinks.isEmpty())
            assertTrue(dataSource.closed.isEmpty())
        }

    /**
     * Nobody observes the session once the redemption ends, so its card is closed then, not before; observing it
     * again still shows the result, without parsing or checking the card again.
     */
    @Test
    fun aRedemptionIsNotCancelledByItsObserverGoingAwayNorByDismiss() =
        runTest {
            dataSource.redeemGate = CompletableDeferred()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.redeem(linkId) { ADDRESS }
            runCurrent()
            assertEquals(GiftCardPhase.Redeeming, observer.latest().phase)
            observer.job.cancel()
            repository.dismiss(linkId)
            runCurrent()
            assertTrue(dataSource.closed.isEmpty())
            dataSource.redeemGate?.complete(Unit)
            runCurrent()

            assertEquals(listOf(GiftCardHandle("fixture-1")), dataSource.closed)
            val again = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Redeemed(dataSource.redemption), again.latest().phase)
            assertEquals(1, dataSource.parsedLinks.size)
            assertEquals(1, dataSource.checkCount)
            again.job.cancel()
        }

    @Test
    fun aSecondRedeemRequestIsIgnored() =
        runTest {
            dataSource.redeemGate = CompletableDeferred()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.redeem(linkId) { ADDRESS }
            repository.redeem(linkId) { ADDRESS }
            runCurrent()
            dataSource.redeemGate?.complete(Unit)
            runCurrent()

            assertEquals(listOf(ADDRESS), dataSource.redeemedTo)
            observer.job.cancel()
        }

    @Test
    fun aRedeemedSessionReportsWhatTheWalletReceives() =
        runTest {
            dataSource.redemption = GiftCardRedemption(txId = GiftCardSummaryFixture.TX_ID, received = Zatoshi(42))
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.redeem(linkId) { ADDRESS }
            runCurrent()

            val redeemed = assertIs<GiftCardPhase.Redeemed>(observer.latest().phase)
            assertEquals(Zatoshi(42), redeemed.redemption.received)
            repository.dismiss(linkId)
            assertEquals(listOf(GiftCardHandle("fixture-1")), dataSource.closed)
            observer.job.cancel()
        }

    @Test
    fun aFailedRedemptionIsRetriedThroughAFreshCheck() =
        runTest {
            dataSource.redeemError = GiftCardException.SubmitFailed()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.redeem(linkId) { ADDRESS }
            runCurrent()
            assertEquals(GiftCardPhase.Failed(GiftCardFailure.REDEEM_FAILED), observer.latest().phase)

            dataSource.statuses = listOf(GiftCardStatus.Empty)
            repository.checkAgain(linkId)
            runCurrent()

            assertEquals(2, dataSource.checkCount)
            assertEquals(1, dataSource.redeemedTo.size)
            assertEquals(GiftCardPhase.Empty, observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun aRedemptionOfAnUncheckedCardChecksItAgain() =
        runTest {
            dataSource.redeemError = GiftCardException.NotChecked()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.redeem(linkId) { ADDRESS }
            runCurrent()

            assertEquals(2, dataSource.checkCount)
            assertIs<GiftCardPhase.Ready>(observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun anUnexpectedRedeemFailureIsARetryableRedeemFailure() =
        runTest {
            dataSource.redeemError = IllegalStateException("rejected")
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.redeem(linkId) { ADDRESS }
            runCurrent()

            assertEquals(GiftCardPhase.Failed(GiftCardFailure.REDEEM_FAILED), observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun anUnexpectedParseFailureIsARetryableCheckFailure() =
        runTest {
            dataSource.parseError = IllegalStateException("boom")
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()
            assertEquals(GiftCardPhase.Failed(GiftCardFailure.CHECK_FAILED), observer.latest().phase)

            dataSource.parseError = null
            repository.checkAgain(linkId)
            runCurrent()

            assertEquals(listOf(LINK, LINK), dataSource.parsedLinks)
            assertIs<GiftCardPhase.Ready>(observer.latest().phase)
            observer.job.cancel()
        }

    @Test
    fun anInvalidLinkIsNotRetryable() =
        runTest {
            dataSource.parseError = GiftCardException.InvalidLink()
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.checkAgain(linkId)
            runCurrent()

            assertEquals(GiftCardPhase.Failed(GiftCardFailure.INVALID_LINK), observer.latest().phase)
            assertEquals(1, dataSource.parsedLinks.size)
            observer.job.cancel()
        }

    @Test
    fun anUnknownLinkIdIsUnavailableWithoutParsing() =
        runTest {
            val repository = repository()
            val observer = observe(repository.observeSession("missing"))
            runCurrent()

            assertEquals(GiftCardPhase.Failed(GiftCardFailure.LINK_UNAVAILABLE), observer.latest().phase)
            assertTrue(dataSource.parsedLinks.isEmpty())
            observer.job.cancel()
        }

    @Test
    fun theSweepErasesOrphanedCardWalletsButNotALiveSessionsOne() =
        runTest {
            val orphan = GiftCardSummaryFixture.storedWallet(alias = "giftcard_orphan")
            val live = GiftCardSummaryFixture.storedWallet()
            dataSource.storedWallets = listOf(orphan, live)
            val repository = repository()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            repository.sweepOrphanedCardWallets()

            assertEquals(listOf(orphan), dataSource.erased)
            observer.job.cancel()
        }

    @Test
    fun aCheckWaitsForTheSweep() =
        runTest {
            dataSource.storedWallets = listOf(GiftCardSummaryFixture.storedWallet())
            val repository = repository()
            repository.sweepOrphanedCardWallets()
            val linkId = store.stash(LINK)
            val observer = observe(repository.observeSession(linkId))
            runCurrent()

            assertEquals(listOf(GiftCardSummaryFixture.storedWallet()), dataSource.erased)
            assertIs<GiftCardPhase.Ready>(observer.latest().phase)
            observer.job.cancel()
        }

    private fun TestScope.repository() =
        GiftCardRepositoryImpl(
            giftCardDataSource = dataSource,
            giftCardLinkStore = store,
        ).also { it.scope = backgroundScope }

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
        const val ADDRESS = "u1orchardonly"
    }
}
