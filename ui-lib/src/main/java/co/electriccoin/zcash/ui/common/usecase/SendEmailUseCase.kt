package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.NEAR_SWAP_PROVIDER
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.model.ZEC_TICKER
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.support.model.SupportInfoType
import co.electriccoin.zcash.ui.screen.swap.mismatch.SwapQuoteMismatchArgs
import co.electriccoin.zcash.ui.util.EmailUtil

@Suppress("TooManyFunctions")
class SendEmailUseCase(
    private val context: Context,
    private val getSupport: GetSupportUseCase,
    private val blockchainProvider: BlockchainProvider,
) {
    /**
     * Sends a generic email with custom recipient, subject and message.
     */
    operator fun invoke(
        address: StringResource,
        subject: StringResource,
        message: StringResource
    ) {
        val recipientAddress = address.getString(context)
        val messageSubject = subject.getString(context)
        val messageBody = message.getString(context)
        sendSupportEmail(
            subject = messageSubject,
            messageBody =
                buildString {
                    appendLine("To: $recipientAddress")
                    appendLine()
                    appendLine(messageBody)
                }
        )
    }

    /**
     * Sends a support email for an exception with full stack trace and support info.
     */
    suspend operator fun invoke(exception: Exception) {
        sendSupportEmail(
            subject = context.getString(R.string.app_name),
            messageBody =
                EmailUtil.formatMessage(
                    body = exception.stackTraceToString(),
                    supportInfo = getSupport().toSupportString(SupportInfoType.entries.toSet())
                )
        )
    }

    /**
     * Sends a support email for a synchronizer error.
     */
    suspend operator fun invoke(synchronizerError: SynchronizerError) {
        sendSupportEmail(
            subject = context.getString(R.string.app_name),
            messageBody =
                EmailUtil.formatMessage(
                    body = synchronizerError.getStackTrace(null),
                    supportInfo = getSupport().toSupportString(SupportInfoType.entries.toSet())
                )
        )
    }

    /**
     * Sends a support email for partial transaction submission results.
     */
    suspend operator fun invoke(submitResult: SubmitResult.Partial) {
        sendSupportEmail(
            subject = context.getString(R.string.app_name),
            messageBody =
                EmailUtil.formatMessage(
                    prefix = context.getString(R.string.send_confirmation_multiple_report_text),
                    supportInfo = getSupport().toSupportString(SupportInfoType.entries.toSet()),
                    suffix =
                        buildString {
                            appendLine(context.getString(R.string.send_confirmation_multiple_report_statuses))
                            appendLine(submitResult.statuses.joinToString())
                        }
                )
        )
    }

    /**
     * Sends a support email for failed transaction submission.
     *
     * `index=0, grpcError=false` here are not placeholders: [SubmitResult.Failure] is only ever
     * constructed (see `List<TransactionSubmitResult>.toSubmitResult()`) from a real, non-gRPC
     * mempool rejection reported by the server, so `grpcError` genuinely is `false` and
     * [submitResult.code] is the real status code. Contrast with [SubmitResult.Error] below, which
     * never reached the server at all and must not be reported this way (MOB-1744).
     */
    operator fun invoke(submitResult: SubmitResult.Failure) {
        val status = submitFailureReportStatus(submitResult)
        sendSupportEmail(
            subject = context.getString(R.string.app_name),
            messageBody =
                EmailUtil.formatMessage(
                    body =
                        buildString {
                            appendLine("Error code: ${submitResult.code}")
                            appendLine(submitResult.description ?: "Unknown error")
                        },
                    supportInfo =
                        buildString {
                            appendLine(context.getString(R.string.send_confirmation_multiple_report_statuses))
                            appendLine(
                                context.getString(
                                    R.string.send_confirmation_multiple_report_status_failure,
                                    status.index,
                                    status.grpcError.toString(),
                                    status.code,
                                    status.description,
                                )
                            )
                        }
                )
        )
    }

    /**
     * Sends a support email for gRPC failure.
     */
    operator fun invoke(submitResult: SubmitResult.GrpcFailure) {
        val reportDescription =
            grpcFailureReportDescription(
                reason = submitResult.reason,
                description = submitResult.description,
                timeoutCopy = { context.getString(R.string.send_pendingTimeoutInfo) }
            )

        sendSupportEmail(
            subject = context.getString(R.string.app_name),
            messageBody =
                EmailUtil.formatMessage(
                    body = buildGrpcFailureEmailBody(reportDescription),
                    supportInfo = ""
                )
        )
    }

    /**
     * Sends a support email for a pre-submission transaction error.
     *
     * [SubmitResult.Error] is only ever constructed (see `ZashiProposalRepository.submit()` and
     * `KeystoneProposalRepository.submit()`) from an exception caught before or during transaction
     * creation -- e.g. a missing proposal/PCZT, or a failure thrown while proving/signing such as a
     * Sapling parameter download timeout -- never from an actual gRPC/mempool response. It used to
     * be reported with a hardcoded fake `gRPC: false, code: -1` status, which misled support into
     * thinking a real protocol status was involved when gRPC was never reached (MOB-1744). Report it
     * as a distinct pre-submission category instead, carrying the real exception type and detail.
     */
    operator fun invoke(submitResult: SubmitResult.Error) {
        val detail = submitErrorPreSubmissionDetail(submitResult.cause)
        sendSupportEmail(
            subject = context.getString(R.string.app_name),
            messageBody =
                EmailUtil.formatMessage(
                    body = "Error before transaction submission (no gRPC call was made)",
                    supportInfo =
                        context.getString(
                            R.string.send_confirmation_report_status_presubmission_failure,
                            detail.exceptionType,
                            detail.description,
                        )
                )
        )
    }

    /**
     * Sends a support email for swap issues.
     */
    @Suppress("MagicNumber")
    suspend operator fun invoke(swapData: SwapData) {
        val status = swapData.status ?: return
        sendSupportEmail(
            subject = context.getString(R.string.transaction_detail_support_email_subject),
            messageBody =
                EmailUtil.formatMessage(
                    body =
                        context.getString(
                            R.string.transaction_detail_support_email_body,
                            status.depositAddress.address,
                            status.originAsset.value(),
                            status.destinationAsset.value(),
                        ),
                    supportInfo =
                        getSupport().toSupportString(
                            setOf(
                                SupportInfoType.Time,
                                SupportInfoType.Os,
                                SupportInfoType.Device,
                                SupportInfoType.Environment,
                                SupportInfoType.Permission
                            )
                        )
                )
        )
    }

    /**
     * Sends a support email for a swap quote that failed the request-vs-response validation (MOB-1340).
     */
    suspend operator fun invoke(args: SwapQuoteMismatchArgs) {
        sendSupportEmail(
            subject = context.getString(R.string.swap_mismatch_support_email_subject),
            messageBody =
                EmailUtil.formatMessage(
                    body =
                        swapQuoteMismatchReportBody(args) { chainTicker ->
                            blockchainProvider.getBlockchain(chainTicker).chainName
                        }.getString(context),
                    supportInfo =
                        getSupport().toSupportString(
                            setOf(
                                SupportInfoType.Time,
                                SupportInfoType.Os,
                                SupportInfoType.Device,
                                SupportInfoType.Environment,
                                SupportInfoType.Permission
                            )
                        )
                )
        )
    }

    /**
     * Internal method to send support email with fallback to text sharing.
     */
    private fun sendSupportEmail(
        subject: String,
        messageBody: String
    ) {
        EmailUtil.sendEmailWithTextFallback(
            context = context,
            recipientAddress = context.getString(R.string.support_email_address),
            subject = subject,
            messageBody = messageBody
        )
    }

    private fun SwapAsset.value() =
        "$tokenTicker - " +
            blockchainProvider.getBlockchain(chainTicker).chainName.getString(context)
}

private fun Throwable.stackTraceToLimitedString(limit: Int) =
    if (stackTraceToString().isNotEmpty()) {
        stackTraceToString()
            .substring(
                0..(stackTraceToString().length - 1).coerceAtMost(limit)
            )
    } else {
        null
    }

/**
 * The human-readable detail attached to a gRPC-failure support email: dedicated timeout copy for a
 * timeout, otherwise the failure's own description. [timeoutCopy] is lazy so the localized string is
 * only resolved when the reason is actually a timeout.
 */
internal fun grpcFailureReportDescription(
    reason: SubmitResult.GrpcFailure.Reason?,
    description: String?,
    timeoutCopy: () -> String
): String? =
    when (reason) {
        SubmitResult.GrpcFailure.Reason.TIMEOUT -> timeoutCopy()
        null -> description
    }

/**
 * Builds the gRPC-failure support email body: a "Grpc failure" header, followed by [reportDescription]
 * on its own paragraph when it carries non-blank detail.
 */
internal fun buildGrpcFailureEmailBody(reportDescription: String?): String =
    buildString {
        appendLine("Grpc failure")
        reportDescription
            ?.takeIf { it.isNotBlank() }
            ?.let {
                appendLine()
                appendLine(it)
            }
    }

/**
 * The report line for a [SubmitResult.Failure]: `index`/`grpcError` are constant because this
 * subtype always represents a single, real, non-gRPC mempool rejection (see the KDoc on the
 * `SubmitResult.Failure` overload above) -- [code] and [description] are the real values reported
 * by the server, never hardcoded.
 */
internal data class SubmitFailureReportStatus(
    val index: Int,
    val grpcError: Boolean,
    val code: Int,
    val description: String
)

private const val SUBMIT_FAILURE_REPORT_INDEX = 0

internal fun submitFailureReportStatus(submitResult: SubmitResult.Failure): SubmitFailureReportStatus =
    SubmitFailureReportStatus(
        index = SUBMIT_FAILURE_REPORT_INDEX,
        grpcError = false,
        code = submitResult.code,
        description = submitResult.description ?: "Unknown error"
    )

/**
 * The real exception type and detail behind a [SubmitResult.Error] -- a failure that happened
 * before any gRPC call was made. Kept separate from the gRPC-status reporting above so a
 * pre-submission failure is never rendered with a fake gRPC status (MOB-1744).
 */
internal data class SubmitErrorPreSubmissionDetail(
    val exceptionType: String,
    val description: String
)

private const val PRE_SUBMISSION_STACK_TRACE_LIMIT = 250

internal fun submitErrorPreSubmissionDetail(cause: Exception): SubmitErrorPreSubmissionDetail =
    SubmitErrorPreSubmissionDetail(
        exceptionType = cause::class.java.simpleName.ifEmpty { cause::class.java.name },
        description = cause.stackTraceToLimitedString(PRE_SUBMISSION_STACK_TRACE_LIMIT) ?: "Unknown error"
    )

/**
 * The support-facing name of a swap provider, e.g. the `near` [co.electriccoin.zcash.ui.common.model.SwapQuote]
 * provider id renders as `NEAR`. A provider we carry no copy for falls back to its uppercased id.
 */
internal fun swapQuoteMismatchProviderLabel(provider: String): StringResource =
    when (provider.lowercase()) {
        NEAR_SWAP_PROVIDER -> stringRes(R.string.swap_mismatch_provider_near)
        else -> stringRes(provider.uppercase())
    }

/**
 * The support-facing body of a rejected-swap-quote report email, pinning the four placeholders of
 * `swap_mismatch_support_email_body`: the provider, the swap-type line, the failed check, and the
 * deposit address (or its "unknown" fallback when the quote never reached one).
 */
internal fun swapQuoteMismatchReportBody(
    args: SwapQuoteMismatchArgs,
    chainName: (String) -> StringResource
): StringResource =
    stringRes(
        R.string.swap_mismatch_support_email_body,
        swapQuoteMismatchProviderLabel(args.provider),
        swapQuoteMismatchSwapTypeLabel(args, chainName),
        stringRes(args.mismatchType.reportLabelRes),
        args.depositAddress
            ?.let { stringRes(it) }
            ?: stringRes(R.string.swap_mismatch_quoteId_unknown)
    )

/**
 * The support-facing swap-type line of a mismatch report, e.g. `CrossPay - ZEC > USDC (Arbitrum)`.
 * [chainName] resolves a chain ticker to its display name, and the chain is omitted for ZEC, which has
 * only one.
 */
internal fun swapQuoteMismatchSwapTypeLabel(
    args: SwapQuoteMismatchArgs,
    chainName: (String) -> StringResource
): StringResource =
    stringRes(
        R.string.swap_mismatch_swapType_format,
        stringRes(
            when (args.mode) {
                SwapMode.EXACT_INPUT -> R.string.swap_mismatch_mode_swap
                SwapMode.EXACT_OUTPUT -> R.string.swap_mismatch_mode_crosspay
                SwapMode.FLEX_INPUT -> R.string.swap_mismatch_mode_swapIntoZec
            }
        ),
        swapQuoteMismatchAssetLabel(args.originTokenTicker, args.originChainTicker, chainName),
        swapQuoteMismatchAssetLabel(args.destinationTokenTicker, args.destinationChainTicker, chainName)
    )

private fun swapQuoteMismatchAssetLabel(
    tokenTicker: String,
    chainTicker: String,
    chainName: (String) -> StringResource
): StringResource =
    if (tokenTicker.equals(ZEC_TICKER, ignoreCase = true) && chainTicker.equals(ZEC_TICKER, ignoreCase = true)) {
        stringRes(tokenTicker.uppercase())
    } else {
        stringRes(R.string.swap_mismatch_asset_format, stringRes(tokenTicker.uppercase()), chainName(chainTicker))
    }
