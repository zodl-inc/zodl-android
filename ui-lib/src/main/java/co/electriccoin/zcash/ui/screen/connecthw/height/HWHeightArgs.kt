package co.electriccoin.zcash.ui.screen.connecthw.height

import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import kotlinx.serialization.Serializable

@Serializable
data class HWHeightArgs(
    val enrollment: HWWalletEnrollment
)
