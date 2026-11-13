package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.CreatedTransaction
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.SubmitRejection
import cash.z.ecc.android.sdk.model.TransactionId
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.screen.transactionprogress.isTooLarge
import co.electriccoin.zcash.ui.util.CloseableScopeHolderImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * A transaction submitted to several endpoints fails only once every endpoint has answered, none accepted it and the
 * SDK recorded it as rejected ([cash.z.ecc.android.sdk.Synchronizer.rejectedTransactions]). An endpoint still in
 * flight at the deadline, or whose submission threw or was cancelled, has not answered: the result stays pending.
 */
class MultiEndpointSubmitVerdictTest {
    @Test
    fun aRefusalForGoodWhileAnotherEndpointIsInFlightAtTheDeadlineIsPending() =
        runTest {
            val transaction = transaction(1)
            val results =
                submit(
                    transaction,
                    A to { tooLarge(transaction) },
                    B to { awaitCancellation() }
                )

            val result = assertIs<TransactionSubmitResult.Failure>(results.single())
            assertEquals(MULTI_SUBMIT_TIMEOUT_DESCRIPTION, result.description)
            assertEquals(
                SubmitResult.GrpcFailure(
                    txIds = listOf(transaction.txIdString()),
                    description = MULTI_SUBMIT_TIMEOUT_DESCRIPTION,
                    reason = SubmitResult.GrpcFailure.Reason.TIMEOUT
                ),
                results.toSubmitResult(emptyMap())
            )
        }

    @Test
    fun aRefusalForGoodThenALateAcceptanceIsSuccess() =
        runTest {
            val transaction = transaction(2)
            val results =
                submit(
                    transaction,
                    A to { tooLarge(transaction) },
                    B to {
                        delay(80.milliseconds)
                        TransactionSubmitResult.Success(transaction.txId)
                    }
                )

            assertEquals(listOf(TransactionSubmitResult.Success(transaction.txId)), results)
            assertEquals(SubmitResult.Success(listOf(transaction.txIdString())), results.toSubmitResult(emptyMap()))
        }

    @Test
    fun aTransientThenATerminalRefusalWithEveryEndpointAnsweredFailsToTheSheet() =
        runTest {
            val transaction = transaction(3)
            val results =
                submit(
                    transaction,
                    A to { transient(transaction) },
                    B to {
                        delay(20.milliseconds)
                        tooLarge(transaction)
                    }
                )

            assertFailedTooLarge(transaction, results)
        }

    @Test
    fun aTerminalThenATransientRefusalWithEveryEndpointAnsweredFailsToTheSheet() =
        runTest {
            val transaction = transaction(4)
            val results =
                submit(
                    transaction,
                    A to { tooLarge(transaction) },
                    B to {
                        delay(20.milliseconds)
                        transportFailure(transaction)
                    }
                )

            assertFailedTooLarge(transaction, results)
        }

    @Test
    fun aPerEndpointRefusalBesideATransportFailureIsPendingWithoutAVerdict() =
        runTest {
            val transaction = transaction(20)
            val results =
                submit(
                    transaction,
                    A to { tooLarge(transaction) },
                    B to { transportFailure(transaction) }
                )

            assertIs<SubmitResult.GrpcFailure>(results.toSubmitResult(emptyMap()))
        }

    @Test
    fun aVerdictCarryingFailureBesideATransportFailureIsFailed() =
        runTest {
            val transaction = transaction(21)
            val results =
                submit(
                    transaction,
                    A to { tooLarge(transaction).copy(rejection = SubmitRejection.TOO_LARGE) },
                    B to { transportFailure(transaction) }
                )

            val failure = assertIs<SubmitResult.Failure>(results.toSubmitResult(emptyMap()))
            assertEquals(SubmitRejection.TOO_LARGE, failure.rejection)
        }

    @Test
    fun everyEndpointRefusingTransientlyIsPending() =
        runTest {
            val transaction = transaction(5)
            val results =
                submit(
                    transaction,
                    A to { transient(transaction) },
                    B to { transportFailure(transaction) }
                )

            assertIs<SubmitResult.GrpcFailure>(results.toSubmitResult(emptyMap()))
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun aSubmissionCancelledAfterItWasSentIsUnansweredAndPending() =
        runTest {
            val transaction = transaction(6)
            val results =
                submit(
                    transaction,
                    A to { tooLarge(transaction) },
                    B to {
                        delay(10.milliseconds)
                        throw CancellationException("cancelled after the request was sent")
                    }
                )

            // Settled at once, not at the deadline: every endpoint finished, one without an answer.
            assertTrue(currentTime < TIMEOUT_MS)
            val result = assertIs<TransactionSubmitResult.Failure>(results.single())
            assertEquals(MULTI_SUBMIT_UNANSWERED_DESCRIPTION, result.description)
            assertIs<SubmitResult.GrpcFailure>(results.toSubmitResult(emptyMap()))
        }

    @Test
    fun aSubmissionThatThrewIsUnansweredAndPending() =
        runTest {
            val transaction = transaction(7)
            val results =
                submit(
                    transaction,
                    A to { error("connection reset") },
                    B to { tooLarge(transaction) }
                )

            val result = assertIs<TransactionSubmitResult.Failure>(results.single())
            assertEquals(MULTI_SUBMIT_UNANSWERED_DESCRIPTION, result.description)
            assertIs<SubmitResult.GrpcFailure>(results.toSubmitResult(emptyMap()))
        }

    @Test
    fun anAcceptedAndAnSdkRejectedTransactionArePartial() =
        runTest {
            val accepted = transaction(8)
            val rejected = transaction(9)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    logger = NoOpLogger,
                    submit = { created, _ ->
                        if (created.txId == accepted.txId) {
                            TransactionSubmitResult.Success(created.txId)
                        } else {
                            tooLarge(created)
                        }
                    }
                )

            val results = submitter.submitTransactions(listOf(accepted, rejected), listOf(A, B), LOG_TAG)

            assertEquals(
                SubmitResult.Partial(
                    txIds = listOf(accepted.txIdString(), rejected.txIdString()),
                    statuses = listOf("success", "rejected code: -25")
                ),
                results.toSubmitResult(verdict(rejected, SubmitRejection.TOO_LARGE))
            )
            // Without the SDK's verdict the refused transaction is still being resubmitted: pending, not partial.
            assertIs<SubmitResult.GrpcFailure>(results.toSubmitResult(emptyMap()))
        }

    @Test
    fun aMigrationTransactionTheSdkNeverRejectsStaysPending() =
        runTest {
            // Orchard to Ironwood migration transactions are never in the SDK's rejections: every endpoint refusing
            // one for good still leaves it pending, resubmitted until it expires.
            val transaction = transaction(10)
            val results =
                submit(
                    transaction,
                    A to { tooLarge(transaction) },
                    B to { tooLarge(transaction) }
                )

            assertIs<SubmitResult.GrpcFailure>(results.toSubmitResult(emptyMap()))
        }

    private suspend fun TestScope.submit(
        transaction: CreatedTransaction,
        vararg answers: Pair<LightWalletEndpoint, suspend () -> TransactionSubmitResult>
    ): List<TransactionSubmitResult> {
        val byEndpoint = answers.toMap()
        return MultiEndpointTransactionSubmitter(
            closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
            globalTimeout = (TIMEOUT_MS / 2).milliseconds,
            timeoutDrain = (TIMEOUT_MS / 2).milliseconds,
            logger = NoOpLogger,
            submit = { _, endpoint -> byEndpoint.getValue(endpoint).invoke() }
        ).submitTransactions(listOf(transaction), byEndpoint.keys.toList(), LOG_TAG)
    }

    private fun assertFailedTooLarge(
        transaction: CreatedTransaction,
        results: List<TransactionSubmitResult>
    ) {
        // One endpoint's raw refusal carries no verdict and is not the transaction failing: only the SDK's is.
        assertEquals(null, assertIs<TransactionSubmitResult.Failure>(results.single()).rejection)
        assertIs<SubmitResult.GrpcFailure>(results.toSubmitResult(emptyMap()))
        val failure =
            assertIs<SubmitResult.Failure>(results.toSubmitResult(verdict(transaction, SubmitRejection.TOO_LARGE)))
        assertEquals(SubmitRejection.TOO_LARGE, failure.rejection)
        assertTrue(failure.isTooLarge())
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

    private fun tooLarge(transaction: CreatedTransaction) =
        TransactionSubmitResult.Failure(
            txId = transaction.txId,
            grpcError = false,
            code = -25,
            description = "transaction rejected: exceeding the configured mempool maximum"
        )

    private fun transient(transaction: CreatedTransaction) =
        TransactionSubmitResult.Failure(
            txId = transaction.txId,
            grpcError = false,
            code = -26,
            description = "mempool full"
        )

    private fun transportFailure(transaction: CreatedTransaction) =
        TransactionSubmitResult.Failure(
            txId = transaction.txId,
            grpcError = true,
            code = -1,
            description = "unreachable"
        )

    private object NoOpLogger : MultiEndpointTransactionSubmitterLogger {
        override fun info(message: () -> String) = Unit

        override fun warn(message: () -> String) = Unit

        override fun error(message: () -> String) = Unit
    }

    private companion object {
        const val LOG_TAG = "[MultiSubmitVerdictTest]"
        const val TIMEOUT_MS = 200L
        val A = LightWalletEndpoint(host = "a.example.com", port = 443, isSecure = true)
        val B = LightWalletEndpoint(host = "b.example.com", port = 443, isSecure = true)
    }
}
