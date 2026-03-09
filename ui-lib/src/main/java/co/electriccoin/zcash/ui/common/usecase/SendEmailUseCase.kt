package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.content.Intent
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
    operator fun invoke(
        address: StringResource,
        subject: StringResource,
        message: StringResource
    ) {
        val intent =
            EmailUtil
                .newMailActivityIntent(
                    recipientAddress = address.getString(context),
                    messageSubject = subject.getString(context),
                    messageBody = message.getString(context)
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
        runCatching { context.startActivity(intent) }
    }

    suspend operator fun invoke(exception: Exception) {
        val fullMessage =
            EmailUtil.formatMessage(
                body = exception.stackTraceToString(),
                supportInfo = getSupport().toSupportString(SupportInfoType.entries.toSet())
            )
        val mailIntent =
            EmailUtil
                .newMailActivityIntent(
                    recipientAddress = context.getString(R.string.support_email_address),
                    messageSubject = context.getString(R.string.app_name),
                    messageBody = fullMessage
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
        runCatching { context.startActivity(mailIntent) }
    }

    suspend operator fun invoke(synchronizerError: SynchronizerError) {
        val fullMessage =
            EmailUtil.formatMessage(
                body = synchronizerError.getStackTrace(null),
                supportInfo = getSupport().toSupportString(SupportInfoType.entries.toSet())
            )
        val mailIntent =
            EmailUtil
                .newMailActivityIntent(
                    recipientAddress = context.getString(R.string.support_email_address),
                    messageSubject = context.getString(R.string.app_name),
                    messageBody = fullMessage
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
        runCatching { context.startActivity(mailIntent) }
    }

    suspend operator fun invoke(submitResult: SubmitResult.Partial) {
        val fullMessage =
            EmailUtil.formatMessage(
                prefix = context.getString(R.string.send_confirmation_multiple_report_text),
                supportInfo = getSupport().toSupportString(SupportInfoType.entries.toSet()),
                suffix =
                    buildString {
                        appendLine(context.getString(R.string.send_confirmation_multiple_report_statuses))
                        appendLine(submitResult.statuses.joinToString())
                    }
            )

        val mailIntent =
            EmailUtil
                .newMailActivityIntent(
                    context.getString(R.string.support_email_address),
                    context.getString(R.string.app_name),
                    fullMessage
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

        runCatching {
            context.startActivity(mailIntent)
        }
    }

    operator fun invoke(submitResult: SubmitResult.Failure) {
        val fullMessage =
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

        val mailIntent =
            EmailUtil
                .newMailActivityIntent(
                    context.getString(R.string.support_email_address),
                    context.getString(R.string.app_name),
                    fullMessage
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

        runCatching {
            context.startActivity(mailIntent)
        }
    }

    operator fun invoke(submitResult: SubmitResult.GrpcFailure) {
        val fullMessage =
            EmailUtil.formatMessage(
                body = "Grpc failure",
                supportInfo = ""
            )

        val mailIntent =
            EmailUtil
                .newMailActivityIntent(
                    context.getString(R.string.support_email_address),
                    context.getString(R.string.app_name),
                    fullMessage
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

        runCatching {
            context.startActivity(mailIntent)
        }
    }

    @Suppress("MagicNumber")
    operator fun invoke(submitResult: SubmitResult.Error) {
        val fullMessage =
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

        val mailIntent =
            EmailUtil
                .newMailActivityIntent(
                    context.getString(R.string.support_email_address),
                    context.getString(R.string.app_name),
                    fullMessage
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

        runCatching {
            context.startActivity(mailIntent)
        }
    }

    @Suppress("MagicNumber")
    suspend operator fun invoke(swapData: SwapData) {
        val status = swapData.status ?: return
        val fullMessage =
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

        val mailIntent =
            EmailUtil
                .newMailActivityIntent(
                    context.getString(R.string.support_email_address),
                    context.getString(R.string.transaction_detail_support_email_subject),
                    fullMessage
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

        runCatching {
            context.startActivity(mailIntent)
        }
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
