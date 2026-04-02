package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceContent
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class CreateLceErrorConfirmationStateUseCase(
    private val sendEmail: SendEmailUseCase,
) {
    operator fun invoke(
        error: LceContent.Error,
        scope: CoroutineScope,
        title: StringResource = stringRes(co.electriccoin.zcash.ui.design.R.string.general_error_title),
        message: StringResource = stringRes(co.electriccoin.zcash.ui.design.R.string.general_please_try_again),
    ): ZashiConfirmationState =
        ZashiConfirmationState(
            icon = R.drawable.ic_reset_zashi_warning,
            title = title,
            message = message,
            primaryAction =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_try_again),
                    style = ButtonStyle.DESTRUCTIVE2,
                    onClick = error.restart
                ),
            secondaryAction =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_contact_support),
                    style = ButtonStyle.PRIMARY,
                    onClick = {
                        error.dismiss()
                        scope.launch {
                            sendEmail(error.cause as? Exception ?: Exception(error.cause))
                        }
                    }
                ),
            onBack = error.dismiss
        )
}
