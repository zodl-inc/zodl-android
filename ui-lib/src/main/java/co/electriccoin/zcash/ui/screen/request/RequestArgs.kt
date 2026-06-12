package co.electriccoin.zcash.ui.screen.request

import kotlinx.serialization.Serializable

@Serializable
data class RequestArgs(
    val addressType: Int
)
