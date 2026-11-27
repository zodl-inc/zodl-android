package co.electriccoin.zcash.ui.screen.connecthw.neworactive

import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import kotlinx.serialization.Serializable

@Serializable
data class HWNewOrActiveArgs(
    val enrollment: HWWalletEnrollment
)
