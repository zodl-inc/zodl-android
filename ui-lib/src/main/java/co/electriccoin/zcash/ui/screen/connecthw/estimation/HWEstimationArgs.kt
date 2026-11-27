package co.electriccoin.zcash.ui.screen.connecthw.estimation

import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import kotlinx.serialization.Serializable

@Serializable
data class HWEstimationArgs(
    val enrollment: HWWalletEnrollment,
    val blockHeight: Long,
)
