package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.TransactionState.Confirmed
import cash.z.ecc.android.sdk.model.TransactionState.Expired
import cash.z.ecc.android.sdk.model.TransactionState.Pending
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression coverage for MOB-1577: expired-unmined transactions were flipping between
 * "Sending..." and "Sending failed" once per sync cycle because `createTransactionState`
 * overrode the terminal `Expired` SDK state with `Pending` whenever a sync round was in
 * progress. The fix makes `Expired` sticky by never normalizing it away.
 */
class TransactionStateNormalizationTest {
    private val repository = TransactionRepositoryImpl(mockk(relaxed = true), mockk(relaxed = true))

    private val minedHeight = BlockHeight.new(1_000_000L)

    @Test
    fun expiredAndSyncingStaysExpired() {
        val result =
            repository.createTransactionState(
                minedHeight = null,
                transactionState = Expired,
                isSyncing = true
            )

        assertNull(result)
    }

    @Test
    fun expiredAndNotSyncingStaysExpired() {
        val result =
            repository.createTransactionState(
                minedHeight = null,
                transactionState = Expired,
                isSyncing = false
            )

        assertNull(result)
    }

    @Test
    fun unminedPendingAndSyncingBecomesPending() {
        val result =
            repository.createTransactionState(
                minedHeight = null,
                transactionState = Pending,
                isSyncing = true
            )

        assertEquals(Pending, result)
    }

    @Test
    fun unminedPendingAndNotSyncingStaysUnchanged() {
        val result =
            repository.createTransactionState(
                minedHeight = null,
                transactionState = Pending,
                isSyncing = false
            )

        assertNull(result)
    }

    @Test
    fun minedTransactionBecomesConfirmedRegardlessOfStateOrSyncFlag() {
        assertEquals(
            Confirmed,
            repository.createTransactionState(
                minedHeight = minedHeight,
                transactionState = Pending,
                isSyncing = true
            )
        )
        assertEquals(
            Confirmed,
            repository.createTransactionState(
                minedHeight = minedHeight,
                transactionState = Expired,
                isSyncing = false
            )
        )
        assertEquals(
            Confirmed,
            repository.createTransactionState(
                minedHeight = minedHeight,
                transactionState = Confirmed,
                isSyncing = true
            )
        )
    }
}
