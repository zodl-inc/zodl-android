package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.component.error
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.common.model.LceError
import co.electriccoin.zcash.ui.common.model.LedgerOperationUnsupportedException
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import com.flexa.core.Flexa.scope
import kotlinx.coroutines.launch

class ErrorMapperUseCase(
    private val sendEmail: SendEmailUseCase,
) {
    /**
     * A [LedgerOperationUnsupportedException] carries its own copy and overrides whatever title and
     * message the call site passed — the caller cannot know a Ledger account was selected, and the
     * generic failure copy would be misleading.
     */
    fun mapToState(
        error: LceContent.Error,
        title: StringResource? = null,
        message: StringResource? = null,
        primaryStyle: ButtonStyle? = null,
    ): LceError.BottomSheet {
        val isLedgerUnsupported = error.cause is LedgerOperationUnsupportedException
        return mapToBottomSheet(
            error = error,
            title =
                when {
                    isLedgerUnsupported -> stringRes(co.electriccoin.zcash.ui.R.string.ledger_unsupported_title)
                    else -> title ?: stringRes(co.electriccoin.zcash.ui.design.R.string.coinVote_error_title)
                },
            message =
                when {
                    isLedgerUnsupported -> stringRes(co.electriccoin.zcash.ui.R.string.ledger_unsupported_message)
                    else ->
                        message
                            ?: stringRes(co.electriccoin.zcash.ui.design.R.string.swapAndPay_failure_laterDesc)
                },
            primaryStyle = primaryStyle,
        )
    }

    private fun mapToBottomSheet(
        error: LceContent.Error,
        title: StringResource,
        message: StringResource,
        primaryStyle: ButtonStyle?,
    ) = LceError.BottomSheet(
        ZashiConfirmationState.error(
            title = title,
            message = message,
            primaryStyle = primaryStyle ?: ButtonStyle.TERTIARY,
            onPrimary = error.restart,
            onBack = error.dismiss,
            onSecondary = {
                error.dismiss()
                scope.launch {
                    sendEmail(error.cause as? Exception ?: Exception(error.cause.message, error.cause))
                }
            },
        )
    )
}
