package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.CreatedTransaction
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.SubmitRejection
import cash.z.ecc.android.sdk.model.TransactionId
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import co.electriccoin.zcash.ui.common.model.SubmitResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SubmitResultMixedFailureMappingTest {
    @Test
    fun timeoutAndDefinitiveRejectionStayPendingWithTimeoutMetadata() {
        val firstTransaction = transaction(1)
        val secondTransaction = transaction(2)

        val result =
            listOf(
                failure(
                    firstTransaction,
                    code = -1,
                    grpcError = true,
                    description = MULTI_SUBMIT_TIMEOUT_DESCRIPTION
                ),
                failure(secondTransaction, code = -25, grpcError = false)
            ).toSubmitResult(emptyMap())

        assertEquals(
            SubmitResult.GrpcFailure(
                txIds = listOf(firstTransaction.txIdString(), secondTransaction.txIdString()),
                description = MULTI_SUBMIT_TIMEOUT_DESCRIPTION,
                reason = SubmitResult.GrpcFailure.Reason.TIMEOUT
            ),
            result
        )
    }

    @Test
    fun definitiveRejectionThenNotAttemptedRemainsPartial() {
        val firstTransaction = transaction(3)
        val secondTransaction = transaction(4)

        val result =
            listOf(
                failure(firstTransaction, code = -25, grpcError = false),
                TransactionSubmitResult.NotAttempted(secondTransaction.txId)
            ).toSubmitResult(emptyMap())

        assertEquals(
            SubmitResult.Partial(
                txIds = listOf(firstTransaction.txIdString(), secondTransaction.txIdString()),
                statuses = listOf("rejected code: -25", "notAttempted")
            ),
            result
        )
    }

    @Test
    fun tooLargeRejectionIsAnErrorCarryingTheRejection() {
        val transaction = transaction(5)

        val result =
            listOf(failure(transaction, code = -25, grpcError = false, description = TOO_LARGE))
                .toSubmitResult(verdict(transaction, SubmitRejection.TOO_LARGE))

        assertEquals(
            SubmitResult.Failure(
                txIds = listOf(transaction.txIdString()),
                code = -25,
                description = TOO_LARGE,
                rejection = SubmitRejection.TOO_LARGE
            ),
            result
        )
    }

    @Test
    fun aRefusalWithoutAKnownRejectionStaysPendingBecauseTheSdkKeepsRetrying() {
        val transaction = transaction(6)

        val result = listOf(failure(transaction, code = -25, grpcError = false)).toSubmitResult(emptyMap())

        assertEquals(SubmitResult.GrpcFailure(txIds = listOf(transaction.txIdString())), result)
    }

    @Test
    fun aMempoolFullRefusalStaysPending() {
        val transaction = transaction(14)

        val result =
            listOf(failure(transaction, code = -26, grpcError = false, description = "mempool full"))
                .toSubmitResult(emptyMap())

        assertEquals(SubmitResult.GrpcFailure(txIds = listOf(transaction.txIdString())), result)
    }

    @Test
    fun invalidRejectionOfEveryTransactionIsAnError() {
        val transaction = transaction(7)

        val result =
            listOf(failure(transaction, code = -26, grpcError = false, description = "bad-txns"))
                .toSubmitResult(verdict(transaction, SubmitRejection.INVALID))

        assertEquals(
            SubmitResult.Failure(
                txIds = listOf(transaction.txIdString()),
                code = -26,
                description = "bad-txns",
                rejection = SubmitRejection.INVALID
            ),
            result
        )
    }

    @Test
    fun rejectionForGoodWithNothingAcceptedIsAnErrorEvenNextToATransientFailure() {
        val rejected = transaction(8)
        val unreachable = transaction(9)

        val result =
            listOf(
                failure(unreachable, code = -1, grpcError = true),
                failure(rejected, code = -25, grpcError = false, description = TOO_LARGE)
            ).toSubmitResult(verdict(rejected, SubmitRejection.TOO_LARGE))

        assertEquals(
            SubmitResult.Failure(
                txIds = listOf(unreachable.txIdString(), rejected.txIdString()),
                code = -25,
                description = TOO_LARGE,
                rejection = SubmitRejection.TOO_LARGE
            ),
            result
        )
    }

    @Test
    fun transientFailuresWithoutAnyRejectionStayPending() {
        val transaction = transaction(12)

        val result =
            listOf(failure(transaction, code = -1, grpcError = true, description = "unreachable"))
                .toSubmitResult(emptyMap())

        assertEquals(
            SubmitResult.GrpcFailure(txIds = listOf(transaction.txIdString())),
            result
        )
    }

    @Test
    fun rejectionOfOneTransactionNextToAnAcceptedOneIsPartial() {
        val accepted = transaction(10)
        val rejected = transaction(11)

        val result =
            listOf(
                TransactionSubmitResult.Success(accepted.txId),
                failure(rejected, code = -26, grpcError = false, description = "bad-txns")
            ).toSubmitResult(verdict(rejected, SubmitRejection.INVALID))

        assertEquals(
            SubmitResult.Partial(
                txIds = listOf(accepted.txIdString(), rejected.txIdString()),
                statuses = listOf("success", "rejected code: -26")
            ),
            result
        )
    }

    @Test
    fun oneEndpointsRefusalForGoodWithoutTheSdkVerdictStaysPending() {
        val transaction = transaction(15)

        val result =
            listOf(failure(transaction, code = -25, grpcError = false, description = TOO_LARGE))
                .toSubmitResult(emptyMap())

        assertEquals(SubmitResult.GrpcFailure(txIds = listOf(transaction.txIdString())), result)
    }

    @Test
    fun theSdksRecordedRefusalIsTheOneReported() {
        val transaction = transaction(16)

        val result =
            listOf(failure(transaction, code = -26, grpcError = false, description = "bad-txns"))
                .toSubmitResult(verdict(transaction, SubmitRejection.EXPIRED))

        assertEquals(SubmitRejection.EXPIRED, assertIs<SubmitResult.Failure>(result).rejection)
    }

    @Test
    fun aFailureCarryingTheSdkVerdictIsFailedWithoutTheRejectedSet() {
        val transaction = transaction(20)

        val result =
            listOf(
                TransactionSubmitResult.Failure(
                    txId = transaction.txId,
                    grpcError = false,
                    code = -25,
                    description = TOO_LARGE,
                    rejection = SubmitRejection.TOO_LARGE
                )
            ).toSubmitResult(emptyMap())

        assertEquals(SubmitRejection.TOO_LARGE, assertIs<SubmitResult.Failure>(result).rejection)
    }

    @Test
    fun aVerdictForAnotherTransactionDoesNotFailThisOne() {
        val transaction = transaction(17)
        val other = transaction(18)

        val result =
            listOf(failure(transaction, code = -25, grpcError = false, description = TOO_LARGE))
                .toSubmitResult(verdict(other, SubmitRejection.TOO_LARGE))

        assertEquals(SubmitResult.GrpcFailure(txIds = listOf(transaction.txIdString())), result)
    }

    @Test
    fun anAcceptedTransactionIsNeverFailedByAStaleVerdict() {
        val transaction = transaction(19)

        val result =
            listOf(TransactionSubmitResult.Success(transaction.txId))
                .toSubmitResult(verdict(transaction, SubmitRejection.TOO_LARGE))

        assertEquals(SubmitResult.Success(txIds = listOf(transaction.txIdString())), result)
    }

    private fun verdict(
        transaction: CreatedTransaction,
        rejection: SubmitRejection
    ) = mapOf(TransactionId.new(transaction.txId) to rejection)

    private fun transaction(index: Int) =
        CreatedTransaction(
            txId = FirstClassByteArray(byteArrayOf(index.toByte())),
            raw = FirstClassByteArray(byteArrayOf(index.toByte(), index.toByte())),
            expiryHeight = null
        )

    private fun failure(
        transaction: CreatedTransaction,
        code: Int,
        grpcError: Boolean,
        description: String = "failure $code"
    ) = TransactionSubmitResult.Failure(
        txId = transaction.txId,
        grpcError = grpcError,
        code = code,
        description = description
    )

    private companion object {
        const val TOO_LARGE = "transaction rejected: exceeding the configured mempool maximum"
    }
}
