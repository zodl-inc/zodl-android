package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.component.error
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.common.model.LceError
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import com.flexa.core.Flexa.scope
import kotlinx.coroutines.launch

class ErrorMapperUseCase(
    private val sendEmail: SendEmailUseCase,
) {
    fun mapToState(
        error: LceContent.Error,
        title: StringResource? = null,
        message: StringResource? = null,
        primaryStyle: ButtonStyle? = null,
    ) = LceError.BottomSheet(
        ZashiConfirmationState.error(
            title = title ?: stringRes(co.electriccoin.zcash.ui.design.R.string.general_error_title),
            message = message ?: stringRes(co.electriccoin.zcash.ui.design.R.string.swapAndPay_failure_laterDesc),
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
