package co.electriccoin.zcash.ui.screen.contact

import co.electriccoin.zcash.ui.common.zns.ZnsFieldStatus
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.PickerState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.StringResource

data class ABContactState(
    val info: IconButtonState?,
    val title: StringResource,
    val walletAddress: TextFieldState,
    /** The line under the address field while a typed ZNS name is looked up or once it resolved. */
    val walletAddressStatus: ZnsFieldStatus? = null,
    val contactName: TextFieldState,
    val chain: PickerState?,
    val negativeButton: ButtonState?,
    val positiveButton: ButtonState,
    val onBack: () -> Unit,
)
