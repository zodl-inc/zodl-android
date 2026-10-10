package co.electriccoin.zcash.ui.screen.disconnect

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

@Immutable
data class DisconnectState(
    val header: StringResource,
    val title: StringResource,
    val subtitle: StringResource,
    val warningTitle: StringResource,
    val warningItems: List<StringResource>,
    @field:DrawableRes val icon: Int,
    val connectedTitle: StringResource,
    val connectedStatus: StringResource,
    val infoText: StringResource,
    val disconnectButton: ButtonState,
    val confirmationDialog: ZashiConfirmationState?,
    val onBack: () -> Unit,
) {
    companion object {
        val preview =
            DisconnectState(
                header = stringRes(R.string.disconnectHWWallet_title),
                title = stringRes(R.string.deleteKeystoneTitle),
                subtitle = stringRes(R.string.deleteKeystoneDesc),
                warningTitle = stringRes(R.string.disconnectHWWallet_mayInclude),
                warningItems =
                    listOf(
                        stringRes(R.string.disconnectHWWallet_bullet1),
                        stringRes(R.string.disconnectHWWallet_bullet2),
                        stringRes(R.string.disconnectHWWallet_bullet3),
                    ),
                icon = co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone,
                connectedTitle = stringRes(R.string.keystoneHW),
                connectedStatus = stringRes(R.string.currentlyConnected),
                infoText = stringRes(R.string.connectedHWInfo),
                disconnectButton =
                    ButtonState(
                        stringRes(R.string.disconnectHWWallet_title),
                        style = ButtonStyle.DESTRUCTIVE1
                    ) {},
                confirmationDialog = null,
                onBack = {}
            )
    }
}
