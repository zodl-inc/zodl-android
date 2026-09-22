package co.electriccoin.zcash.ui.screen.connecthardware.height

import co.electriccoin.zcash.ui.screen.connecthardware.HardwareWalletEnrollment
import kotlinx.serialization.Serializable

@Serializable
data class HardwareHeightArgs(
    val enrollment: HardwareWalletEnrollment
)
