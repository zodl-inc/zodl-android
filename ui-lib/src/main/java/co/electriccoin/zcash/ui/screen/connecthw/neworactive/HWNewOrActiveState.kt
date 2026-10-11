package co.electriccoin.zcash.ui.screen.connecthw.neworactive

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollmentTag

data class HWNewOrActiveState(
    @param:DrawableRes val logo: Int,
    val subtitle: StringResource,
    val message: StringResource,
    val newDevice: ButtonState,
    val activeDevice: ButtonState,
    val newDeviceTestTag: String,
    val activeDeviceTestTag: String,
    val onBack: () -> Unit,
) {
    companion object {
        val previewKeystone =
            HWNewOrActiveState(
                logo = co.electriccoin.zcash.ui.design.R.drawable.image_keystone,
                subtitle = stringRes("New or Active Device ?"),
                message =
                    stringRes(
                        "Connecting to a hardware wallet that was previously connected to Zodl " +
                            "will require synchronization to discover your transaction history."
                    ),
                newDevice = ButtonState(stringRes("Connect new device")) {},
                activeDevice = ButtonState(stringRes("Connect active device")) {},
                newDeviceTestTag = HWWalletEnrollmentTag.KEYSTONE_NEW_DEVICE,
                activeDeviceTestTag = HWWalletEnrollmentTag.KEYSTONE_ACTIVE_DEVICE,
                onBack = {},
            )

        val previewLedger =
            previewKeystone.copy(
                logo = R.drawable.ic_ledger_wordmark,
                newDeviceTestTag = HWWalletEnrollmentTag.LEDGER_NEW_DEVICE,
                activeDeviceTestTag = HWWalletEnrollmentTag.LEDGER_ACTIVE_DEVICE,
            )
    }
}
