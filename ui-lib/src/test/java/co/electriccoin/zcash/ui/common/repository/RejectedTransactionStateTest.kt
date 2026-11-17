package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.TransactionId
import cash.z.ecc.android.sdk.model.TransactionOverview
import cash.z.ecc.android.sdk.model.TransactionState
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip318Kind
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A transaction a server refused for good shows as failed from then on, not as pending until it expires. */
class RejectedTransactionStateTest {
    private val rejectedId = TransactionId.new(ByteArray(32) { 1 })
    private val otherId = TransactionId.new(ByteArray(32) { 2 })

    private fun overview(
        id: TransactionId,
        state: TransactionState,
        minedHeight: BlockHeight? = null
    ) = TransactionOverview(
        txId = id,
        minedHeight = minedHeight,
        expiryHeight = null,
        index = null,
        raw = null,
        isSentTransaction = true,
        netValue = Zatoshi(1),
        totalSpent = Zatoshi(1),
        totalReceived = Zatoshi(0),
        feePaid = null,
        isChange = false,
        receivedNoteCount = 0,
        sentNoteCount = 1,
        memoCount = 0,
        blockTimeEpochSeconds = null,
        transactionState = state,
        isShielding = false,
        spentNoteCount = 1,
        poolCrossingValue = null,
        isTrusted = false,
        zip318Kind = Zip318Kind.NOT_CLASSIFIED
    )

    @Test
    fun rejectedPendingTransactionIsExpired() {
        val result = overview(rejectedId, TransactionState.Pending).asRejectedIfIn(setOf(rejectedId))

        assertEquals(TransactionState.Expired, result.transactionState)
    }

    @Test
    fun transactionNotRejectedStaysPending() {
        val pending = overview(otherId, TransactionState.Pending)

        assertSame(pending, pending.asRejectedIfIn(setOf(rejectedId)))
    }

    @Test
    fun nothingRejectedChangesNothing() {
        val pending = overview(rejectedId, TransactionState.Pending)

        assertSame(pending, pending.asRejectedIfIn(emptySet()))
    }

    @Test
    fun rejectedTransactionThatWasMinedAnywayStaysConfirmed() {
        val mined =
            overview(rejectedId, TransactionState.Confirmed, minedHeight = BlockHeight.new(1_000_000L))

        assertSame(mined, mined.asRejectedIfIn(setOf(rejectedId)))
    }

    @Test
    fun rejectedTransactionAlreadyExpiredStaysExpired() {
        val result = overview(rejectedId, TransactionState.Expired).asRejectedIfIn(setOf(rejectedId))

        assertEquals(TransactionState.Expired, result.transactionState)
    }

    @Test
    fun aRejectedSentTransactionIsAFailedOneFlaggedRejected() {
        val repository = TransactionRepositoryImpl(mockk(relaxed = true), mockk(relaxed = true))
        val expired = overview(rejectedId, TransactionState.Expired)

        val rejected = repository.createTransaction(expired, TxDetails(emptyList(), null), isRejected = true)
        val onlyExpired = repository.createTransaction(expired, TxDetails(emptyList(), null))

        assertIs<SendTransaction.Failed>(rejected)
        assertTrue(rejected.isRejected)
        assertIs<SendTransaction.Failed>(onlyExpired)
        assertFalse(onlyExpired.isRejected)
    }
}
