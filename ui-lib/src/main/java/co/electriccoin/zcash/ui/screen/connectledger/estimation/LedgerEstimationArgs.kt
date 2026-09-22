package co.electriccoin.zcash.ui.screen.connectledger.estimation

import kotlinx.serialization.Serializable

@Serializable
data class LedgerEstimationArgs(
    val blockHeight: Long,
)
