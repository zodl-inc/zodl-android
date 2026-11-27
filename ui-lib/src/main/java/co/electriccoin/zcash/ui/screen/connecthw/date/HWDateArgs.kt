package co.electriccoin.zcash.ui.screen.connecthw.date

import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import kotlinx.serialization.Serializable

@Serializable
data class HWDateArgs(
    val enrollment: HWWalletEnrollment
)
