package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.CreatedTransaction
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.util.CloseableScopeHolder
import co.electriccoin.zcash.ui.util.CloseableScopeHolderImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal interface MultiEndpointTransactionSubmitter {
    suspend fun submitTransactions(
        transactions: List<CreatedTransaction>,
        endpoints: List<LightWalletEndpoint>,
        logTag: String
    ): List<TransactionSubmitResult>

    companion object {
        operator fun invoke(
            closeableScopeHolder: CloseableScopeHolder = CloseableScopeHolderImpl(Dispatchers.IO),
            globalTimeout: Duration = 30.seconds,
            timeoutDrain: Duration = 2.seconds,
            gracePeriod: Duration = 5.seconds,
            logger: MultiEndpointTransactionSubmitterLogger = TwigMultiEndpointTransactionSubmitterLogger,
            submit: suspend (CreatedTransaction, LightWalletEndpoint) -> TransactionSubmitResult
        ): MultiEndpointTransactionSubmitter =
            MultiEndpointTransactionSubmitterImpl(
                closeableScopeHolder = closeableScopeHolder,
                globalTimeout = globalTimeout,
                timeoutDrain = timeoutDrain,
                gracePeriod = gracePeriod,
                logger = logger,
                submit = submit
            )
    }
}

private class MultiEndpointTransactionSubmitterImpl(
    closeableScopeHolder: CloseableScopeHolder,
    private val globalTimeout: Duration,
    private val timeoutDrain: Duration,
    private val gracePeriod: Duration,
    private val logger: MultiEndpointTransactionSubmitterLogger,
    private val submit: suspend (CreatedTransaction, LightWalletEndpoint) -> TransactionSubmitResult
) : MultiEndpointTransactionSubmitter,
    CloseableScopeHolder by closeableScopeHolder {
    override suspend fun submitTransactions(
        transactions: List<CreatedTransaction>,
        endpoints: List<LightWalletEndpoint>,
        logTag: String
    ): List<TransactionSubmitResult> {
        var hasSuccessfulBroadcast = false
        var completedNormally = false

        try {
            // Submit every created transaction even after an earlier one fails. The SDK only records a
            // retry plan (and keeps rebroadcasting in the background until the transaction mines or expires)
            // for transactions it was asked to submit; skipping later transactions on a failure would strand
            // them forever. Matches the iOS Broadcaster integration.
            val results =
                transactions.mapIndexed { index, transaction ->
                    val result =
                        submitTransaction(
                            transaction = transaction,
                            endpoints = endpoints,
                            index = index,
                            transactionCount = transactions.size,
                            logTag = logTag
                        )

                    if (result is TransactionSubmitResult.Success) {
                        hasSuccessfulBroadcast = true
                    }

                    result
                }
            completedNormally = true
            return results
        } finally {
            // Tear down the broadcast scope from a coroutine launched on it rather than inline: close()
            // is non-blocking (it only cancels the scope) but the Closeable contract trips the
            // blocking-call inspection when invoked directly in this suspend body. After a successful
            // broadcast we keep the remaining endpoints publishing for a short grace period; otherwise we
            // close immediately.
            val closeDelay = if (completedNormally && hasSuccessfulBroadcast) gracePeriod else Duration.ZERO
            scope.launch {
                delay(closeDelay)
                close()
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

    /**
     * Submits [transaction] to every endpoint concurrently. The first acceptance wins. Otherwise the result is a
     * refusal only once every endpoint has answered: a refusal (for good or transient) and a transport failure
     * are answers, while a submission that threw, was cancelled or is still in flight at the deadline is not,
     * because that endpoint may still accept the transaction. Any unanswered endpoint leaves the result pending.
     *
     * A refusal returned here is never by itself the transaction failing: whether it did is the SDK's verdict over
     * every endpoint ([cash.z.ecc.android.sdk.Synchronizer.rejectedTransactions]).
     */
    private suspend fun broadcastToEndpoints(
        transaction: CreatedTransaction,
        endpoints: List<LightWalletEndpoint>,
        transactionLabel: String,
        logTag: String
    ): TransactionSubmitResult {
        val completion = CompletableDeferred<BroadcastCompletion>()
        val settled = ConcurrentLinkedQueue<EndpointSubmission>()

        fun settle(submission: EndpointSubmission) {
            settled += submission
            val result = submission.result
            if (result is TransactionSubmitResult.Success) {
                completion.complete(
                    BroadcastCompletion.Accepted(endpointLabel = submission.endpointLabel, result = result)
                )
            } else if (settled.size >= endpoints.size) {
                completion.complete(
                    BroadcastCompletion.Refused(result = selectSettledResult(transaction, settled.toList()))
                )
            }
        }

        val jobs =
            endpoints.mapIndexed { endpointIndex, endpoint ->
                val endpointLabel = endpointLabel(endpointIndex, endpoints.size)

                scope.launch {
                    var result: TransactionSubmitResult? = null
                    try {
                        result =
                            submitToEndpoint(
                                transaction = transaction,
                                endpoint = endpoint,
                                endpointLabel = endpointLabel,
                                transactionLabel = transactionLabel,
                                logTag = logTag
                            )
                    } finally {
                        // A submission that threw or was cancelled settles unanswered: its request may have left.
                        settle(EndpointSubmission(endpointLabel = endpointLabel, result = result))
                    }
                }
            }
        val timeoutJob =
            scope.launch {
                // Let endpoint calls that completed at the deadline publish their result before reporting timeout.
                delay(globalTimeout + timeoutDrain)
                // An endpoint still in flight has not answered and may yet accept: the transaction is pending.
                if (completion.complete(
                        BroadcastCompletion.Refused(
                            result = createGrpcFailure(transaction, MULTI_SUBMIT_TIMEOUT_DESCRIPTION)
                        )
                    )
                ) {
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
                    state.cancelJobsAfterGracePeriod()
                    result.result
                }

                is BroadcastCompletion.Refused -> {
                    state.cancel()
                    logger.error {
                        "$logTag $transactionLabel not accepted by any of $endpointCount endpoint(s)."
                    }
                    result.result
                }
            }
        } catch (e: CancellationException) {
            state.cancel()
            throw e
        }

    private fun BroadcastSubmissionState.cancelJobsAfterGracePeriod() {
        scope.launch {
            delay(gracePeriod)
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
    ): TransactionSubmitResult? =
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
            // Unlike a transport failure the SDK returns, a thrown submission is no answer: its request may have
            // left, so the endpoint may still accept the transaction.
            logger.warn { "$logTag $endpointLabel FAILED $transactionLabel with exception; no answer." }
            null
        }

    /**
     * The result once every endpoint settled without accepting: pending when any of them has not answered (its
     * submission threw or was cancelled, or the SDK did not attempt it), otherwise the most telling refusal.
     */
    private fun selectSettledResult(
        transaction: CreatedTransaction,
        submissions: List<EndpointSubmission>
    ): TransactionSubmitResult {
        val failures = submissions.map { it.result as? TransactionSubmitResult.Failure }
        if (failures.any { it == null }) {
            return createGrpcFailure(transaction, MULTI_SUBMIT_UNANSWERED_DESCRIPTION)
        }
        val answers = failures.filterNotNull()
        return answers.firstOrNull { it.rejection != null }
            ?: answers.firstOrNull { !it.grpcError }
            ?: answers.first()
    }

    private fun createGrpcFailure(transaction: CreatedTransaction, description: String?) =
        TransactionSubmitResult.Failure(
            txId = transaction.txId,
            grpcError = true,
            code = MULTI_SUBMIT_GRPC_FAILURE_CODE,
            description = description
        )
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

private const val MULTI_SUBMIT_GRPC_FAILURE_CODE = -1
internal const val MULTI_SUBMIT_TIMEOUT_DESCRIPTION =
    "Timed out waiting for endpoint response; transaction may still have been broadcast"
internal const val MULTI_SUBMIT_UNANSWERED_DESCRIPTION =
    "An endpoint did not answer; transaction may still have been broadcast"

private data class BroadcastSubmissionState(
    val completion: CompletableDeferred<BroadcastCompletion>,
    val jobs: List<Job>,
    val timeoutJob: Job
)

/** One endpoint's settled submission; [result] is null when it did not answer (it threw or was cancelled). */
private data class EndpointSubmission(
    val endpointLabel: String,
    val result: TransactionSubmitResult?
)

private sealed interface BroadcastCompletion {
    data class Accepted(
        val endpointLabel: String,
        val result: TransactionSubmitResult.Success
    ) : BroadcastCompletion

    /** No endpoint accepted: every one refused, or the result is pending (see [selectSettledResult]). */
    data class Refused(
        val result: TransactionSubmitResult
    ) : BroadcastCompletion
}
