package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.CreatedTransaction
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.spackle.Twig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

internal class MultiEndpointTransactionSubmitter(
    private val scope: CoroutineScope,
    private val globalTimeoutMillis: Long = MULTI_SUBMIT_GLOBAL_TIMEOUT_MILLIS,
    private val gracePeriodMillis: Long = MULTI_SUBMIT_GRACE_PERIOD_MILLIS,
    private val logger: MultiEndpointTransactionSubmitterLogger = TwigMultiEndpointTransactionSubmitterLogger,
    private val submit: suspend (CreatedTransaction, LightWalletEndpoint) -> TransactionSubmitResult
) {
    suspend fun submitTransactions(
        transactions: List<CreatedTransaction>,
        endpoints: List<LightWalletEndpoint>,
        logTag: String
    ): List<TransactionSubmitResult> {
        var anySubmissionFailed = false

        return transactions.mapIndexed { index, transaction ->
            if (anySubmissionFailed) {
                TransactionSubmitResult.NotAttempted(transaction.txId)
            } else {
                val result =
                    submitTransaction(
                        transaction = transaction,
                        endpoints = endpoints,
                        index = index,
                        transactionCount = transactions.size,
                        logTag = logTag
                    )

                if (result !is TransactionSubmitResult.Success) {
                    anySubmissionFailed = true
                }

                result
            }
        }
    }

    private suspend fun submitTransaction(
        transaction: CreatedTransaction,
        endpoints: List<LightWalletEndpoint>,
        index: Int,
        transactionCount: Int,
        logTag: String
    ): TransactionSubmitResult {
        if (endpoints.isEmpty()) {
            logger.error { "$logTag No endpoints available for transaction ${transaction.txIdString()}." }
            return createGrpcFailure(transaction, "No endpoints available")
        }

        if (endpoints.size == 1) {
            logger.info {
                "$logTag Submitting transaction ${index + 1}/$transactionCount to ${endpoints.first().serverString()}."
            }
        } else {
            logger.info {
                "$logTag Broadcasting transaction ${index + 1}/$transactionCount to ${endpoints.size} endpoints."
            }
        }

        return broadcastToEndpoints(
            transaction = transaction,
            endpoints = endpoints,
            logTag = logTag
        )
    }

    private suspend fun broadcastToEndpoints(
        transaction: CreatedTransaction,
        endpoints: List<LightWalletEndpoint>,
        logTag: String
    ): TransactionSubmitResult {
        val completion = CompletableDeferred<BroadcastCompletion>()
        val failureCount = AtomicInteger(0)
        val failures = ConcurrentLinkedQueue<EndpointSubmission>()
        val jobs =
            endpoints.map { endpoint ->
                scope
                    .async {
                        EndpointSubmission(
                            endpoint = endpoint,
                            result =
                                submitToEndpoint(
                                    transaction = transaction,
                                    endpoint = endpoint,
                                    logTag = logTag
                                )
                        )
                    }.also { job ->
                        job.invokeOnCompletion { throwable ->
                            if (throwable is CancellationException) return@invokeOnCompletion

                            scope.launch {
                                val submission =
                                    runCatching { job.await() }
                                        .getOrElse {
                                            EndpointSubmission(
                                                endpoint = endpoint,
                                                result = createGrpcFailure(transaction, it.message)
                                            )
                                        }

                                when (val result = submission.result) {
                                    is TransactionSubmitResult.Success -> {
                                        completion.complete(
                                            BroadcastCompletion.Accepted(
                                                endpoint = submission.endpoint,
                                                result = result
                                            )
                                        )
                                    }

                                    is TransactionSubmitResult.Failure,
                                    is TransactionSubmitResult.NotAttempted -> {
                                        failures += submission
                                        if (failureCount.incrementAndGet() >= endpoints.size) {
                                            completion.complete(
                                                BroadcastCompletion.Rejected(
                                                    result = selectRejectedResult(transaction, failures.toList())
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        val timeoutJob =
            scope.launch {
                delay(globalTimeoutMillis)
                val completedSubmissions =
                    jobs.mapNotNull { job ->
                        if (job.isCompleted && !job.isCancelled) {
                            runCatching { job.await() }.getOrNull()
                        } else {
                            null
                        }
                    }
                val timeoutResult =
                    selectTimeoutCompletion(
                        transaction = transaction,
                        submissions = completedSubmissions
                    )
                if (completion.complete(timeoutResult)) {
                    logger.error { "$logTag Timed out waiting for any endpoint to accept ${transaction.txIdString()}." }
                    jobs.forEach { it.cancel() }
                }
            }

        return awaitBroadcastCompletion(
            transaction = transaction,
            endpointCount = endpoints.size,
            state =
                BroadcastSubmissionState(
                    completion = completion,
                    jobs = jobs,
                    timeoutJob = timeoutJob
                ),
            logTag = logTag
        )
    }

    private suspend fun awaitBroadcastCompletion(
        transaction: CreatedTransaction,
        endpointCount: Int,
        state: BroadcastSubmissionState,
        logTag: String
    ): TransactionSubmitResult =
        try {
            when (val result = state.completion.await()) {
                is BroadcastCompletion.Accepted -> {
                    logger.info {
                        "$logTag Transaction ${transaction.txIdString()} accepted by ${result.endpoint.serverString()}."
                    }
                    state.cancelAfterGracePeriod()
                    result.result
                }

                is BroadcastCompletion.Rejected -> {
                    state.cancel()
                    logger.error {
                        "$logTag Transaction ${transaction.txIdString()} rejected by all $endpointCount endpoint(s)."
                    }
                    result.result
                }
            }
        } catch (e: CancellationException) {
            state.cancel()
            throw e
        }

    private fun BroadcastSubmissionState.cancelAfterGracePeriod() {
        scope.launch {
            delay(gracePeriodMillis)
            cancel()
        }
    }

    private fun BroadcastSubmissionState.cancel() {
        timeoutJob.cancel()
        jobs.forEach { it.cancel() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun submitToEndpoint(
        transaction: CreatedTransaction,
        endpoint: LightWalletEndpoint,
        logTag: String
    ): TransactionSubmitResult =
        try {
            val result = submit(transaction, endpoint)
            when (result) {
                is TransactionSubmitResult.Success -> {
                    logger.info { "$logTag ${endpoint.serverString()} SUCCESS ${transaction.txIdString()}." }
                }

                is TransactionSubmitResult.Failure -> {
                    logger.warn {
                        "$logTag ${endpoint.serverString()} FAILED ${transaction.txIdString()}: " +
                            "${result.code} ${result.description}"
                    }
                }

                is TransactionSubmitResult.NotAttempted -> {
                    logger.warn { "$logTag ${endpoint.serverString()} NOT_ATTEMPTED ${transaction.txIdString()}." }
                }
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "$logTag ${endpoint.serverString()} FAILED ${transaction.txIdString()}." }
            createGrpcFailure(transaction, e.message)
        }

    private fun selectRejectedResult(
        transaction: CreatedTransaction,
        submissions: List<EndpointSubmission>
    ): TransactionSubmitResult {
        val failures =
            submissions.mapNotNull { submission ->
                (submission.result as? TransactionSubmitResult.Failure)?.withEndpoint(submission.endpoint)
            }

        return failures
            .lastOrNull { !it.grpcError }
            ?: failures.lastOrNull()
            ?: createGrpcFailure(transaction, "All endpoints rejected transaction")
    }

    private fun selectTimeoutCompletion(
        transaction: CreatedTransaction,
        submissions: List<EndpointSubmission>
    ): BroadcastCompletion =
        submissions
            .firstNotNullOfOrNull { submission ->
                (submission.result as? TransactionSubmitResult.Success)?.let {
                    BroadcastCompletion.Accepted(
                        endpoint = submission.endpoint,
                        result = it
                    )
                }
            }
            ?: submissions
                .takeIf { it.isNotEmpty() }
                ?.let {
                    BroadcastCompletion.Rejected(
                        result = selectRejectedResult(transaction, it)
                    )
                }
            ?: BroadcastCompletion.Rejected(
                result = createGrpcFailure(transaction, "Timed out submitting to endpoints")
            )

    private fun createGrpcFailure(transaction: CreatedTransaction, description: String?) =
        TransactionSubmitResult.Failure(
            txId = transaction.txId,
            grpcError = true,
            code = MULTI_SUBMIT_GRPC_FAILURE_CODE,
            description = description
        )

}

private fun LightWalletEndpoint.serverString() = "$host:$port"

private fun TransactionSubmitResult.Failure.withEndpoint(endpoint: LightWalletEndpoint) =
    copy(
        description = description?.let { "${endpoint.serverString()}: $it" } ?: endpoint.serverString()
    )

internal interface MultiEndpointTransactionSubmitterLogger {
    fun info(message: () -> String)

    fun warn(message: () -> String)

    fun warn(
        throwable: Throwable,
        message: () -> String
    )

    fun error(message: () -> String)
}

private object TwigMultiEndpointTransactionSubmitterLogger : MultiEndpointTransactionSubmitterLogger {
    override fun info(message: () -> String) = Twig.info(message)

    override fun warn(message: () -> String) = Twig.warn(message)

    override fun warn(
        throwable: Throwable,
        message: () -> String
    ) = Twig.warn(throwable, message)

    override fun error(message: () -> String) = Twig.error(message)
}

private const val MULTI_SUBMIT_GRACE_PERIOD_MILLIS = 5_000L
private const val MULTI_SUBMIT_GLOBAL_TIMEOUT_MILLIS = 30_000L
private const val MULTI_SUBMIT_GRPC_FAILURE_CODE = -1

private data class BroadcastSubmissionState(
    val completion: CompletableDeferred<BroadcastCompletion>,
    val jobs: List<Deferred<EndpointSubmission>>,
    val timeoutJob: Job
)

private data class EndpointSubmission(
    val endpoint: LightWalletEndpoint,
    val result: TransactionSubmitResult
)

private sealed interface BroadcastCompletion {
    data class Accepted(
        val endpoint: LightWalletEndpoint,
        val result: TransactionSubmitResult.Success
    ) : BroadcastCompletion

    data class Rejected(
        val result: TransactionSubmitResult
    ) : BroadcastCompletion
}
