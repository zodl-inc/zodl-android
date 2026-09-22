package co.electriccoin.zcash.ui.screen.connecthardware.neworactive

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

data class HardwareNewOrActiveState(
    @param:DrawableRes val logo: Int,
    val subtitle: StringResource,
    val message: StringResource,
    val newDevice: ButtonState,
    val activeDevice: ButtonState,
    val newDeviceTestTag: String,
    val activeDeviceTestTag: String,
    val onBack: () -> Unit,
)
