package co.electriccoin.zcash.ui.common.component

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.R as UIR

/**
 * @param isSecondaryFirst Draws the secondary button above the primary one: the sheet lists
 *        [ZashiConfirmationState.primaryAction] first, so the two buttons swap slots. Ignored when
 *        there is no secondary button.
 * @param icon The sheet's icon.
 */
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
    isSecondaryFirst: Boolean = false,
    @DrawableRes icon: Int = R.drawable.ic_reset_zashi_warning,
): ZashiConfirmationState {
    val primary =
        ButtonState(
            text = primaryText,
            style = primaryStyle,
            onClick = onPrimary,
        )
    val secondary =
        if (secondaryText != null && onSecondary != null) {
            ButtonState(
                text = secondaryText,
                style = secondaryStyle,
                onClick = onSecondary,
            )
        } else {
            null
        }
    val (first, second) =
        if (isSecondaryFirst && secondary != null) {
            secondary to primary
        } else {
            primary to secondary
        }
    return ZashiConfirmationState(
        icon = icon,
        title = title,
        message = message,
        primaryAction = first,
        secondaryAction = second,
        onBack = onBack,
    )
}

/**
 * @param primaryStyle The style of the confirming button; the solid destructive one unless the
 *        screen's design asks for another.
 * @param icon The sheet's icon.
 */
fun ZashiConfirmationState.Companion.destructive(
    title: StringResource,
    message: StringResource,
    primaryText: StringResource,
    secondaryText: StringResource = stringRes(UIR.string.general_cancel),
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    onSecondary: () -> Unit = onBack,
    primaryStyle: ButtonStyle = ButtonStyle.DESTRUCTIVE2,
    @DrawableRes icon: Int = R.drawable.ic_reset_zashi_warning,
): ZashiConfirmationState =
    ZashiConfirmationState(
        icon = icon,
        title = title,
        message = message,
        primaryAction =
            ButtonState(
                text = primaryText,
                style = primaryStyle,
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
