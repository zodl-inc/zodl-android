package co.electriccoin.zcash.ui.screen.connecthardware.neworactive

import co.electriccoin.zcash.ui.screen.connecthardware.HardwareWalletEnrollment
import kotlinx.serialization.Serializable

@Serializable
data class HardwareNewOrActiveArgs(
    val enrollment: HardwareWalletEnrollment
)
