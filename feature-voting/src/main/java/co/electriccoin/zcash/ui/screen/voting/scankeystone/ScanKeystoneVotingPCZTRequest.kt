package co.electriccoin.zcash.ui.screen.voting.scankeystone

import kotlinx.serialization.Serializable

@Serializable
data class ScanKeystoneVotingPCZTRequest(
    val roundIdHex: String,
    val bundleIndex: Int,
    val actionIndex: Int
)
