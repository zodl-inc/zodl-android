package co.electriccoin.zcash.ui.common.component

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.R as UIR

fun ZashiConfirmationState.Companion.error(
    title: StringResource = stringRes(UIR.string.coinVote_error_title),
    message: StringResource = stringRes(UIR.string.swapAndPay_failure_laterDesc),
    primaryText: StringResource = stringRes(UIR.string.disconnectHWWallet_tryAgain),
    secondaryText: StringResource? = stringRes(UIR.string.disconnectHWWallet_contactSupport),
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
    secondaryText: StringResource = stringRes(UIR.string.general_cancel),
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
