package co.electriccoin.zcash.ui.screen.connecthardware.date

import co.electriccoin.zcash.ui.screen.connecthardware.HardwareWalletEnrollment
import kotlinx.serialization.Serializable

@Serializable
data class HardwareDateArgs(
    val enrollment: HardwareWalletEnrollment
)
