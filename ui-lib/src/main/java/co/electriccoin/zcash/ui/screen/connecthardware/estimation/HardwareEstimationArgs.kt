package co.electriccoin.zcash.ui.screen.connecthardware.estimation

import co.electriccoin.zcash.ui.screen.connecthardware.HardwareWalletEnrollment
import kotlinx.serialization.Serializable

@Serializable
data class HardwareEstimationArgs(
    val enrollment: HardwareWalletEnrollment,
    val blockHeight: Long,
)
