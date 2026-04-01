package co.electriccoin.zcash.ui.common.model

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.stringRes

fun LceError.toConfirmationState(): ZashiConfirmationState =
    ZashiConfirmationState(
        icon = R.drawable.ic_reset_zashi_warning,
        title = stringRes(co.electriccoin.zcash.ui.design.R.string.general_error_title),
        message = stringRes(co.electriccoin.zcash.ui.design.R.string.general_please_try_again),
        primaryAction =
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_try_again),
                style = ButtonStyle.DESTRUCTIVE2,
                onClick = restart
            ),
        secondaryAction =
            ButtonState(
                text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel),
                style = ButtonStyle.PRIMARY,
                onClick = dismiss
            ),
        onBack = dismiss
    )
