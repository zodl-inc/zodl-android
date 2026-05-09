package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.CreatedTransaction
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MultiEndpointTransactionSubmitterTest {
    @Test
    fun manualModeSubmitsOnlyToSelectedEndpoint() =
        runTest {
            val endpoint = endpoint("manual.example.com")
            val submissions = Collections.synchronizedList(mutableListOf<LightWalletEndpoint>())
            val transaction = transaction(1)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    scope = backgroundScope,
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
    fun automaticModeReturnsFirstSuccessfulEndpoint() =
        runTest {
            val first = endpoint("first.example.com")
            val second = endpoint("second.example.com")
            val third = endpoint("third.example.com")
            val transaction = transaction(2)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    scope = backgroundScope,
                    logger = noOpLogger,
                    submit = { _, submittedEndpoint ->
                        when (submittedEndpoint) {
                            first -> {
                                delay(1_000)
                                failure(transaction, code = 1, grpcError = true)
                            }
                            second -> TransactionSubmitResult.Success(transaction.txId)
                            third -> {
                                delay(1_000)
                                TransactionSubmitResult.Success(transaction.txId)
                            }
                            else -> error("Unexpected endpoint $submittedEndpoint")
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
    fun automaticModeReturnsBestFailureWhenAllEndpointsFailFast() =
        runTest {
            val transaction = transaction(3)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    scope = backgroundScope,
                    logger = noOpLogger,
                    submit = { _, submittedEndpoint ->
                        when (submittedEndpoint.host) {
                            "first.example.com" -> failure(transaction, code = 1, grpcError = true)
                            "second.example.com" -> failure(transaction, code = 18, grpcError = false)
                            "third.example.com" -> failure(transaction, code = 19, grpcError = true)
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
        }

    @Test
    fun timeoutPreservesCompletedEndpointFailure() =
        runTest {
            val transaction = transaction(4)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    scope = backgroundScope,
                    globalTimeoutMillis = 100,
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
            assertEquals(false, result.grpcError)
            assertEquals(18, result.code)
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
                    scope = backgroundScope,
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
    fun laterTransactionsAreNotAttemptedAfterFirstFailure() =
        runTest {
            val firstTransaction = transaction(6)
            val secondTransaction = transaction(7)
            val submissions = AtomicInteger(0)
            val firstFailure = failure(firstTransaction, code = 18, grpcError = false)
            val submitter =
                MultiEndpointTransactionSubmitter(
                    scope = backgroundScope,
                    logger = noOpLogger,
                    submit = { _, _ ->
                        submissions.incrementAndGet()
                        firstFailure
                    }
                )

            val results =
                submitter.submitTransactions(
                    transactions = listOf(firstTransaction, secondTransaction),
                    endpoints = listOf(endpoint("manual.example.com")),
                    logTag = LOG_TAG
                )

            assertEquals(firstFailure, results[0])
            assertEquals(TransactionSubmitResult.NotAttempted(secondTransaction.txId), results[1])
            assertEquals(1, submissions.get())
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

                override fun warn(
                    throwable: Throwable,
                    message: () -> String
                ) = Unit

                override fun error(message: () -> String) = Unit
            }
    }
}
