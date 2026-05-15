package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.CreatedTransaction
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.util.CloseableScopeHolder
import co.electriccoin.zcash.ui.util.CloseableScopeHolderImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

internal class MultiEndpointTransactionSubmitter(
    closeableScopeHolder: CloseableScopeHolder = CloseableScopeHolderImpl(Dispatchers.IO),
    private val globalTimeoutMillis: Long = MULTI_SUBMIT_GLOBAL_TIMEOUT_MILLIS,
    private val timeoutDrainMillis: Long = MULTI_SUBMIT_TIMEOUT_DRAIN_MILLIS,
    private val gracePeriodMillis: Long = MULTI_SUBMIT_GRACE_PERIOD_MILLIS,
    private val logger: MultiEndpointTransactionSubmitterLogger = TwigMultiEndpointTransactionSubmitterLogger,
    private val submit: suspend (CreatedTransaction, LightWalletEndpoint) -> TransactionSubmitResult
) : CloseableScopeHolder by closeableScopeHolder {
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
        val transactionLabel = "transaction ${index + 1}/$transactionCount"
        if (endpoints.isEmpty()) {
            logger.error { "$logTag No endpoints available for $transactionLabel." }
            return createGrpcFailure(transaction, "No endpoints available")
        }

        if (endpoints.size == 1) {
            logger.info {
                "$logTag Submitting $transactionLabel to endpoint 1/1."
            }
        } else {
            logger.info {
                "$logTag Broadcasting transaction ${index + 1}/$transactionCount to ${endpoints.size} endpoints."
            }
        }

        return broadcastToEndpoints(
            transaction = transaction,
            endpoints = endpoints,
            transactionLabel = transactionLabel,
            logTag = logTag
        )
    }

    private suspend fun broadcastToEndpoints(
        transaction: CreatedTransaction,
        endpoints: List<LightWalletEndpoint>,
        transactionLabel: String,
        logTag: String
    ): TransactionSubmitResult {
        val completion = CompletableDeferred<BroadcastCompletion>()
        val failureCount = AtomicInteger(0)
        val failures = ConcurrentLinkedQueue<EndpointSubmission>()
        val jobs =
            endpoints.mapIndexed { endpointIndex, endpoint ->
                val endpointLabel = endpointLabel(endpointIndex, endpoints.size)

                scope
                    .async {
                        val submission =
                            EndpointSubmission(
                                endpointLabel = endpointLabel,
                                result =
                                    submitToEndpoint(
                                        transaction = transaction,
                                        endpoint = endpoint,
                                        endpointLabel = endpointLabel,
                                        transactionLabel = transactionLabel,
                                        logTag = logTag
                                    )
                            )

                        when (val result = submission.result) {
                            is TransactionSubmitResult.Success -> {
                                completion.complete(
                                    BroadcastCompletion.Accepted(
                                        endpointLabel = submission.endpointLabel,
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

                        submission
                    }
            }
        val timeoutJob =
            scope.launch {
                delay(globalTimeoutMillis)
                // Let endpoint calls that completed at the deadline publish their result before reporting timeout.
                delay(timeoutDrainMillis)
                if (completion.isCompleted) {
                    return@launch
                }
                val completedSubmissions =
                    jobs.completedSubmissions()
                val timeoutResult =
                    selectTimeoutCompletion(
                        transaction = transaction,
                        submissions = completedSubmissions,
                        endpointCount = endpoints.size
                    )
                if (completion.complete(timeoutResult)) {
                    logger.error { "$logTag Timed out waiting for any endpoint to accept $transactionLabel." }
                    jobs.forEach { it.cancel() }
                }
            }

        return awaitBroadcastCompletion(
            endpointCount = endpoints.size,
            state =
                BroadcastSubmissionState(
                    completion = completion,
                    jobs = jobs,
                    timeoutJob = timeoutJob
                ),
            transactionLabel = transactionLabel,
            logTag = logTag
        )
    }

    private suspend fun awaitBroadcastCompletion(
        endpointCount: Int,
        state: BroadcastSubmissionState,
        transactionLabel: String,
        logTag: String
    ): TransactionSubmitResult =
        try {
            when (val result = state.completion.await()) {
                is BroadcastCompletion.Accepted -> {
                    logger.info {
                        "$logTag $transactionLabel accepted by ${result.endpointLabel}."
                    }
                    state.cancelAfterGracePeriod()
                    result.result
                }

                is BroadcastCompletion.Rejected -> {
                    state.cancel()
                    logger.error {
                        "$logTag $transactionLabel rejected by all $endpointCount endpoint(s)."
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
        endpointLabel: String,
        transactionLabel: String,
        logTag: String
    ): TransactionSubmitResult =
        try {
            val result = submit(transaction, endpoint)
            when (result) {
                is TransactionSubmitResult.Success -> {
                    logger.info { "$logTag $endpointLabel SUCCESS $transactionLabel." }
                }

                is TransactionSubmitResult.Failure -> {
                    logger.warn {
                        "$logTag $endpointLabel FAILED $transactionLabel: code=${result.code} grpc=${result.grpcError}"
                    }
                }

                is TransactionSubmitResult.NotAttempted -> {
                    logger.warn { "$logTag $endpointLabel NOT_ATTEMPTED $transactionLabel." }
                }
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            logger.warn { "$logTag $endpointLabel FAILED $transactionLabel with exception." }
            createGrpcFailure(transaction, SUBMIT_EXCEPTION_DESCRIPTION)
        }

    private fun selectRejectedResult(
        transaction: CreatedTransaction,
        submissions: List<EndpointSubmission>
    ): TransactionSubmitResult {
        val failures =
            submissions.mapNotNull { submission ->
                submission.result as? TransactionSubmitResult.Failure
            }

        return failures
            .firstOrNull { !it.grpcError }
            ?: failures.firstOrNull()
            ?: createGrpcFailure(transaction, "All endpoints rejected transaction")
    }

    private fun selectTimeoutCompletion(
        transaction: CreatedTransaction,
        submissions: List<EndpointSubmission>,
        endpointCount: Int
    ): BroadcastCompletion =
        submissions
            .firstNotNullOfOrNull { submission ->
                (submission.result as? TransactionSubmitResult.Success)?.let {
                    BroadcastCompletion.Accepted(
                        endpointLabel = submission.endpointLabel,
                        result = it
                    )
                }
            }
            ?: submissions
                .takeIf { it.size >= endpointCount }
                ?.let {
                    BroadcastCompletion.Rejected(
                        result = selectRejectedResult(transaction, it)
                    )
                }
            ?: BroadcastCompletion.Rejected(
                result = createGrpcFailure(transaction, MULTI_SUBMIT_TIMEOUT_DESCRIPTION)
            )

    private fun createGrpcFailure(transaction: CreatedTransaction, description: String?) =
        TransactionSubmitResult.Failure(
            txId = transaction.txId,
            grpcError = true,
            code = MULTI_SUBMIT_GRPC_FAILURE_CODE,
            description = description
        )
}

private suspend fun List<Deferred<EndpointSubmission>>.completedSubmissions() =
    mapNotNull { job ->
        if (job.isCompleted && !job.isCancelled) {
            runCatching { job.await() }.getOrNull()
        } else {
            null
        }
    }

private fun endpointLabel(index: Int, count: Int) = "endpoint ${index + 1}/$count"

internal interface MultiEndpointTransactionSubmitterLogger {
    fun info(message: () -> String)

    fun warn(message: () -> String)

    fun error(message: () -> String)
}

private object TwigMultiEndpointTransactionSubmitterLogger : MultiEndpointTransactionSubmitterLogger {
    override fun info(message: () -> String) = Twig.info(message)

    override fun warn(message: () -> String) = Twig.warn(message)

    override fun error(message: () -> String) = Twig.error(message)
}

private const val MULTI_SUBMIT_GRACE_PERIOD_MILLIS = 5_000L
private const val MULTI_SUBMIT_GLOBAL_TIMEOUT_MILLIS = 30_000L
private const val MULTI_SUBMIT_TIMEOUT_DRAIN_MILLIS = 2_000L
private const val MULTI_SUBMIT_GRPC_FAILURE_CODE = -1
private const val SUBMIT_EXCEPTION_DESCRIPTION = "Endpoint submission failed"
internal const val MULTI_SUBMIT_TIMEOUT_DESCRIPTION =
    "Timed out waiting for endpoint response; transaction may still have been broadcast"

private data class BroadcastSubmissionState(
    val completion: CompletableDeferred<BroadcastCompletion>,
    val jobs: List<Deferred<EndpointSubmission>>,
    val timeoutJob: Job
)

private data class EndpointSubmission(
    val endpointLabel: String,
    val result: TransactionSubmitResult
)

private sealed interface BroadcastCompletion {
    data class Accepted(
        val endpointLabel: String,
        val result: TransactionSubmitResult.Success
    ) : BroadcastCompletion

    data class Rejected(
        val result: TransactionSubmitResult
    ) : BroadcastCompletion
}
