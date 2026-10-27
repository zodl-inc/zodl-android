package co.electriccoin.zcash.ui.screen.viewingkey.importvk

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.viewingkey.R

/**
 * The sheet over the paste screen when the text is not an importable viewing key. Its message names
 * [keyPrefix], the prefix of a key for the wallet's network.
 */
internal object InvalidVKSheet {
    val preview: ZashiConfirmationState = create(keyPrefix = "uview1", onScanClick = {}, onEditClick = {})

    fun create(
        keyPrefix: String,
        onScanClick: () -> Unit,
        onEditClick: () -> Unit,
    ) = ZashiConfirmationState(
        icon = R.drawable.view_only_ic_alert,
        title = stringRes(R.string.view_only_invalid_key_title),
        message = stringRes(R.string.view_only_invalid_key_message, keyPrefix),
        primaryAction =
            ButtonState(
                text = stringRes(R.string.view_only_invalid_key_edit),
                style = ButtonStyle.PRIMARY,
                onClick = onEditClick,
            ),
        secondaryAction =
            ButtonState(
                text = stringRes(R.string.view_only_invalid_key_scan),
                style = ButtonStyle.SECONDARY,
                onClick = onScanClick,
            ),
        onBack = onEditClick,
        style = ZashiConfirmationStyle.UNVERIFIED_POLL_WARNING,
    )
}
