package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.CreatedTransaction
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.util.CloseableScopeHolderImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class MultiEndpointTransactionSubmitterTest {
    @Test
    fun manualModeSubmitsOnlyToSelectedEndpoint() =
        runTest {
            val endpoint = endpoint("manual.example.com")
            val submissions = Collections.synchronizedList(mutableListOf<LightWalletEndpoint>())
            val transaction = transaction(1)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    logger = noOpLogger,
                    submit = { _, submittedEndpoint ->
                        submissions += submittedEndpoint
                        TransactionSubmitResult.Success(transaction.txId)
                    }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(transaction),
                    endpoints = listOf(endpoint),
                    logTag = LOG_TAG
                )

            assertEquals(listOf(TransactionSubmitResult.Success(transaction.txId)), results)
            assertEquals(listOf(endpoint), submissions.toList())
        }

    @Test
    fun manualModeTimesOutHungSubmission() =
        runTest {
            val transaction = transaction(8)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    globalTimeout = 100.milliseconds,
                    timeoutDrain = 100.milliseconds,
                    logger = noOpLogger,
                    submit = { _, _ -> awaitCancellation() }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(transaction),
                    endpoints = listOf(endpoint("manual.example.com")),
                    logTag = LOG_TAG
                )

            val result = assertIs<TransactionSubmitResult.Failure>(results.single())
            assertEquals(true, result.grpcError)
            assertEquals(
                "Timed out waiting for endpoint response; transaction may still have been broadcast",
                result.description
            )
        }

    @Test
    fun timeoutDrainPreservesLateEndpointSuccess() =
        runTest {
            val transaction = transaction(9)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    globalTimeout = 100.milliseconds,
                    timeoutDrain = 100.milliseconds,
                    logger = noOpLogger,
                    submit = { _, submittedEndpoint ->
                        when (submittedEndpoint.host) {
                            "late.example.com" -> {
                                delay(150.milliseconds)
                                TransactionSubmitResult.Success(transaction.txId)
                            }

                            "hung.example.com" -> {
                                awaitCancellation()
                            }

                            else -> {
                                error("Unexpected endpoint $submittedEndpoint")
                            }
                        }
                    }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(transaction),
                    endpoints = listOf(endpoint("late.example.com"), endpoint("hung.example.com")),
                    logTag = LOG_TAG
                )

            assertEquals(listOf(TransactionSubmitResult.Success(transaction.txId)), results)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun successfulBroadcastKeepsOtherEndpointsRunningDuringGracePeriod() =
        runTest {
            val transaction = transaction(23)
            val lateEndpointCompleted = CompletableDeferred<Unit>()
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    gracePeriod = 100.milliseconds,
                    logger = noOpLogger,
                    submit = { _, submittedEndpoint ->
                        when (submittedEndpoint.host) {
                            "fast.example.com" -> {
                                TransactionSubmitResult.Success(transaction.txId)
                            }

                            "late.example.com" -> {
                                delay(50.milliseconds)
                                lateEndpointCompleted.complete(Unit)
                                TransactionSubmitResult.Success(transaction.txId)
                            }

                            else -> {
                                error("Unexpected endpoint $submittedEndpoint")
                            }
                        }
                    }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(transaction),
                    endpoints = listOf(endpoint("fast.example.com"), endpoint("late.example.com")),
                    logTag = LOG_TAG
                )

            assertEquals(listOf(TransactionSubmitResult.Success(transaction.txId)), results)
            assertEquals(false, lateEndpointCompleted.isCompleted)

            advanceTimeBy(50.milliseconds)
            runCurrent()

            assertEquals(true, lateEndpointCompleted.isCompleted)
        }

    @Test
    fun automaticModeReturnsFirstSuccessfulEndpoint() =
        runTest {
            val first = endpoint("first.example.com")
            val second = endpoint("second.example.com")
            val third = endpoint("third.example.com")
            val transaction = transaction(2)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    logger = noOpLogger,
                    submit = { _, submittedEndpoint ->
                        when (submittedEndpoint) {
                            first -> {
                                delay(1_000.milliseconds)
                                failure(transaction, code = 1, grpcError = true)
                            }

                            second -> {
                                TransactionSubmitResult.Success(transaction.txId)
                            }

                            third -> {
                                delay(1_000.milliseconds)
                                TransactionSubmitResult.Success(transaction.txId)
                            }

                            else -> {
                                error("Unexpected endpoint $submittedEndpoint")
                            }
                        }
                    }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(transaction),
                    endpoints = listOf(first, second, third),
                    logTag = LOG_TAG
                )

            assertEquals(listOf(TransactionSubmitResult.Success(transaction.txId)), results)
        }

    @Test
    fun automaticModeReturnsFirstNonGrpcFailureWhenAllEndpointsFailFast() =
        runTest {
            val transaction = transaction(3)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    logger = noOpLogger,
                    submit = { _, submittedEndpoint ->
                        when (submittedEndpoint.host) {
                            "first.example.com" -> failure(transaction, code = 1, grpcError = true)
                            "second.example.com" -> failure(transaction, code = 18, grpcError = false)
                            "third.example.com" -> failure(transaction, code = 19, grpcError = false)
                            else -> error("Unexpected endpoint $submittedEndpoint")
                        }
                    }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(transaction),
                    endpoints =
                        listOf(
                            endpoint("first.example.com"),
                            endpoint("second.example.com"),
                            endpoint("third.example.com")
                        ),
                    logTag = LOG_TAG
                )

            val result = assertIs<TransactionSubmitResult.Failure>(results.single())
            assertEquals(false, result.grpcError)
            assertEquals(18, result.code)
            assertEquals("failure 18", result.description)
        }

    @Test
    fun timeoutWithIncompleteEndpointReportsGrpcFailure() =
        runTest {
            val transaction = transaction(4)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    globalTimeout = 100.milliseconds,
                    timeoutDrain = 100.milliseconds,
                    logger = noOpLogger,
                    submit = { _, submittedEndpoint ->
                        when (submittedEndpoint.host) {
                            "failed.example.com" -> failure(transaction, code = 18, grpcError = false)
                            "hung.example.com" -> awaitCancellation()
                            else -> error("Unexpected endpoint $submittedEndpoint")
                        }
                    }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(transaction),
                    endpoints = listOf(endpoint("failed.example.com"), endpoint("hung.example.com")),
                    logTag = LOG_TAG
                )

            val result = assertIs<TransactionSubmitResult.Failure>(results.single())
            assertEquals(true, result.grpcError)
            assertEquals(-1, result.code)
            assertEquals(MULTI_SUBMIT_TIMEOUT_DESCRIPTION, result.description)
            assertEquals(
                SubmitResult.GrpcFailure(
                    txIds = listOf(transaction.txIdString()),
                    description = MULTI_SUBMIT_TIMEOUT_DESCRIPTION,
                    reason = SubmitResult.GrpcFailure.Reason.TIMEOUT
                ),
                results.toSubmitResult()
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun callerCancellationCancelsEndpointSubmissions() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val cancelledCount = AtomicInteger(0)
            val startedCount = AtomicInteger(0)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    logger = noOpLogger,
                    submit = { _, _ ->
                        if (startedCount.incrementAndGet() == 2) {
                            started.complete(Unit)
                        }
                        try {
                            awaitCancellation()
                        } finally {
                            cancelledCount.incrementAndGet()
                        }
                    }
                )

            val job =
                launch {
                    submitter.submitTransactions(
                        transactions = listOf(transaction(5)),
                        endpoints = listOf(endpoint("first.example.com"), endpoint("second.example.com")),
                        logTag = LOG_TAG
                    )
                }

            started.await()
            job.cancel()
            job.join()
            runCurrent()

            assertEquals(2, cancelledCount.get())
        }

    @Test
    fun everyTransactionIsSubmittedEvenAfterEarlierFailure() =
        runTest {
            val firstTransaction = transaction(6)
            val secondTransaction = transaction(7)
            val thirdTransaction = transaction(24)
            val submittedTxIds = Collections.synchronizedList(mutableListOf<FirstClassByteArray>())
            val firstFailure = failure(firstTransaction, code = 18, grpcError = false)
            val secondFailure = failure(secondTransaction, code = -1, grpcError = true)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    logger = noOpLogger,
                    submit = { transaction, _ ->
                        submittedTxIds += transaction.txId
                        when (transaction.txId) {
                            firstTransaction.txId -> firstFailure
                            secondTransaction.txId -> secondFailure
                            else -> TransactionSubmitResult.Success(transaction.txId)
                        }
                    }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(firstTransaction, secondTransaction, thirdTransaction),
                    endpoints = listOf(endpoint("manual.example.com")),
                    logTag = LOG_TAG
                )

            // A failure must not stop later transactions from being submitted; an unsubmitted
            // transaction never gets a retry plan in the SDK and would be stranded forever.
            assertEquals(
                listOf(firstTransaction.txId, secondTransaction.txId, thirdTransaction.txId),
                submittedTxIds.toList()
            )
            assertEquals(firstFailure, results[0])
            assertEquals(secondFailure, results[1])
            assertEquals(TransactionSubmitResult.Success(thirdTransaction.txId), results[2])
        }

    @Test
    fun endpointHostAndPortAreNotLoggedOrAddedToFailureDescription() =
        runTest {
            val logger = RecordingLogger()
            val transaction = transaction(19)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    logger = logger,
                    submit = { _, _ ->
                        failure(transaction, code = 18, grpcError = false)
                    }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(transaction),
                    endpoints = listOf(endpoint("private.example.com")),
                    logTag = LOG_TAG
                )

            val result = assertIs<TransactionSubmitResult.Failure>(results.single())
            assertEquals("failure 18", result.description)
            assertEquals(false, logger.messages.any { it.contains("private.example.com") || it.contains(":443") })
        }

    @Test
    fun endpointExceptionMessageIsNotLoggedOrReported() =
        runTest {
            val logger = RecordingLogger()
            val transaction = transaction(20)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    closeableScopeHolder = CloseableScopeHolderImpl(backgroundScope),
                    logger = logger,
                    submit = { _, _ ->
                        error("private.example.com:443 failed")
                    }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(transaction),
                    endpoints = listOf(endpoint("private.example.com")),
                    logTag = LOG_TAG
                )

            val result = assertIs<TransactionSubmitResult.Failure>(results.single())
            assertEquals(true, result.grpcError)
            assertEquals("Endpoint submission failed", result.description)
            assertEquals(false, logger.messages.any { it.contains("private.example.com") || it.contains(":443") })
        }

    @Test
    fun acceptedTransactionThenGrpcFailureMapsToPartialResult() {
        val firstTransaction = transaction(10)
        val secondTransaction = transaction(11)
        val thirdTransaction = transaction(12)

        val result =
            listOf(
                TransactionSubmitResult.Success(firstTransaction.txId),
                failure(secondTransaction, code = -1, grpcError = true),
                TransactionSubmitResult.NotAttempted(thirdTransaction.txId)
            ).toSubmitResult()

        assertEquals(
            SubmitResult.Partial(
                txIds =
                    listOf(
                        firstTransaction.txIdString(),
                        secondTransaction.txIdString(),
                        thirdTransaction.txIdString()
                    ),
                statuses = listOf("success", "grpcFailure", "notAttempted")
            ),
            result
        )
    }

    @Test
    fun nonGrpcFailuresMapToFirstFailureDetail() {
        val firstTransaction = transaction(13)
        val secondTransaction = transaction(14)

        val result =
            listOf(
                failure(firstTransaction, code = 18, grpcError = false),
                failure(secondTransaction, code = 19, grpcError = false)
            ).toSubmitResult()

        assertEquals(
            SubmitResult.Failure(
                txIds = listOf(firstTransaction.txIdString(), secondTransaction.txIdString()),
                code = 18,
                description = "failure 18"
            ),
            result
        )
    }

    @Test
    fun nonTimeoutGrpcFailuresMapToDefaultPendingResult() {
        val firstTransaction = transaction(15)
        val secondTransaction = transaction(16)

        val result =
            listOf(
                failure(firstTransaction, code = -1, grpcError = true),
                failure(secondTransaction, code = -1, grpcError = true)
            ).toSubmitResult()

        assertEquals(
            SubmitResult.GrpcFailure(
                txIds = listOf(firstTransaction.txIdString(), secondTransaction.txIdString())
            ),
            result
        )
    }

    @Test
    fun grpcFailureThenNotAttemptedMapsToPartialResult() {
        val firstTransaction = transaction(17)
        val secondTransaction = transaction(18)

        val result =
            listOf(
                failure(firstTransaction, code = -1, grpcError = true),
                TransactionSubmitResult.NotAttempted(secondTransaction.txId)
            ).toSubmitResult()

        assertEquals(
            SubmitResult.Partial(
                txIds = listOf(firstTransaction.txIdString(), secondTransaction.txIdString()),
                statuses = listOf("grpcFailure", "notAttempted")
            ),
            result
        )
    }

    @Test
    fun grpcFailurePartialStatusDoesNotExposeDescription() {
        val firstTransaction = transaction(21)
        val secondTransaction = transaction(22)

        val result =
            listOf(
                TransactionSubmitResult.Failure(
                    txId = firstTransaction.txId,
                    grpcError = true,
                    code = -1,
                    description = "private.example.com:443 failed"
                ),
                TransactionSubmitResult.NotAttempted(secondTransaction.txId)
            ).toSubmitResult()

        assertEquals(
            SubmitResult.Partial(
                txIds = listOf(firstTransaction.txIdString(), secondTransaction.txIdString()),
                statuses = listOf("grpcFailure", "notAttempted")
            ),
            result
        )
    }

    @Test
    fun emptyCreatedTransactionsMapToFailureResult() {
        val result = emptyList<TransactionSubmitResult>().toSubmitResult()

        assertEquals(
            SubmitResult.Failure(
                txIds = emptyList(),
                code = -1,
                description = "No transactions created"
            ),
            result
        )
    }

    @Test
    fun allSuccessesMapToSuccessResult() {
        val firstTransaction = transaction(25)
        val secondTransaction = transaction(26)

        val result =
            listOf(
                TransactionSubmitResult.Success(firstTransaction.txId),
                TransactionSubmitResult.Success(secondTransaction.txId)
            ).toSubmitResult()

        assertEquals(
            SubmitResult.Success(
                txIds = listOf(firstTransaction.txIdString(), secondTransaction.txIdString())
            ),
            result
        )
    }

    @Test
    fun partialWithNonGrpcFailureRedactsServerDescription() {
        // A multi-transaction proposal where one transaction broadcast and another was rejected for a
        // real (non-gRPC) reason is Partial. The rejected transaction's status carries only the code:
        // the server-provided description must not leak into the partial-failure support email.
        // Matches the iOS Broadcaster integration, which redacts to "rejected code: <code>".
        val firstTransaction = transaction(27)
        val secondTransaction = transaction(28)

        val result =
            listOf(
                TransactionSubmitResult.Success(firstTransaction.txId),
                failure(secondTransaction, code = 18, grpcError = false)
            ).toSubmitResult()

        assertEquals(
            SubmitResult.Partial(
                txIds = listOf(firstTransaction.txIdString(), secondTransaction.txIdString()),
                statuses = listOf("success", "rejected code: 18")
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
        grpcError: Boolean
    ) = TransactionSubmitResult.Failure(
        txId = transaction.txId,
        grpcError = grpcError,
        code = code,
        description = "failure $code"
    )

    private fun endpoint(host: String) =
        LightWalletEndpoint(
            host = host,
            port = 443,
            isSecure = true
        )

    private companion object {
        const val LOG_TAG = "[MultiSubmitTest]"

        val noOpLogger =
            object : MultiEndpointTransactionSubmitterLogger {
                override fun info(message: () -> String) = Unit

                override fun warn(message: () -> String) = Unit

                override fun error(message: () -> String) = Unit
            }
    }
}

private class RecordingLogger : MultiEndpointTransactionSubmitterLogger {
    val messages: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun info(message: () -> String) {
        messages += message()
    }

    override fun warn(message: () -> String) {
        messages += message()
    }

    override fun error(message: () -> String) {
        messages += message()
    }
}
