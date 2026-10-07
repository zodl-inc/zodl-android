package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.CreatedTransaction
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import co.electriccoin.zcash.ui.common.model.SubmitResult
import kotlin.test.Test
import kotlin.test.assertEquals

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
            ).toSubmitResult()

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
            ).toSubmitResult()

        assertEquals(
            SubmitResult.Partial(
                txIds = listOf(firstTransaction.txIdString(), secondTransaction.txIdString()),
                statuses = listOf("rejected code: -25", "notAttempted")
            ),
            result
        )
    }

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
}
