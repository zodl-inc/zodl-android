package co.electriccoin.zcash.ui.common.component

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

fun ZashiConfirmationState.Companion.error(
    title: StringResource = stringRes(co.electriccoin.zcash.ui.design.R.string.general_error_title),
    message: StringResource = stringRes(co.electriccoin.zcash.ui.design.R.string.swapAndPay_failure_laterDesc),
    primaryText: StringResource = stringRes(co.electriccoin.zcash.ui.design.R.string.general_try_again),
    secondaryText: StringResource? = stringRes(co.electriccoin.zcash.ui.design.R.string.general_contact_support),
    primaryStyle: ButtonStyle = ButtonStyle.TERTIARY,
    secondaryStyle: ButtonStyle = ButtonStyle.PRIMARY,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)? = null,
    onBack: () -> Unit,
): ZashiConfirmationState =
    ZashiConfirmationState(
        icon = R.drawable.ic_reset_zashi_warning,
        title = title,
        message = message,
        primaryAction =
            ButtonState(
                text = primaryText,
                style = primaryStyle,
                onClick = onPrimary,
            ),
        secondaryAction =
            if (secondaryText != null && onSecondary != null) {
                ButtonState(
                    text = secondaryText,
                    style = secondaryStyle,
                    onClick = onSecondary,
                )
            } else {
                null
            },
        onBack = onBack,
    )

fun ZashiConfirmationState.Companion.destructive(
    title: StringResource,
    message: StringResource,
    primaryText: StringResource,
    secondaryText: StringResource = stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel),
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    onSecondary: () -> Unit = onBack,
): ZashiConfirmationState =
    ZashiConfirmationState(
        icon = R.drawable.ic_reset_zashi_warning,
        title = title,
        message = message,
        primaryAction =
            ButtonState(
                text = primaryText,
                style = ButtonStyle.DESTRUCTIVE2,
                onClick = onPrimary,
            ),
        secondaryAction =
            ButtonState(
                text = secondaryText,
                style = ButtonStyle.PRIMARY,
                onClick = onSecondary,
            ),
        onBack = onBack,
    )
