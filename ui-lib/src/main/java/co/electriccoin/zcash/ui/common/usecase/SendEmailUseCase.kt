package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.screen.support.model.SupportInfoType
import co.electriccoin.zcash.ui.util.EmailUtil

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
     */
    operator fun invoke(submitResult: SubmitResult.Failure) {
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
                                    0,
                                    false.toString(),
                                    submitResult.code,
                                    submitResult.description,
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
        sendSupportEmail(
            subject = context.getString(R.string.app_name),
            messageBody =
                EmailUtil.formatMessage(
                    body = "Grpc failure",
                    supportInfo = ""
                )
        )
    }

    /**
     * Sends a support email for transaction submission error.
     */
    @Suppress("MagicNumber")
    operator fun invoke(submitResult: SubmitResult.Error) {
        sendSupportEmail(
            subject = context.getString(R.string.app_name),
            messageBody =
                EmailUtil.formatMessage(
                    body = "Error submitting transaction",
                    supportInfo =
                        buildString {
                            appendLine(context.getString(R.string.send_confirmation_multiple_report_statuses))
                            appendLine(
                                context.getString(
                                    R.string.send_confirmation_multiple_report_status_failure,
                                    0,
                                    false.toString(),
                                    -1,
                                    submitResult.cause.stackTraceToLimitedString(250),
                                )
                            )
                        }
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
                            status.quote.depositAddress.address,
                            status.quote.originAsset.value(),
                            status.quote.destinationAsset.value(),
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
