package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VoteOption(
    @SerialName("index")
    val id: Int,
    val label: String,
    val description: String? = null
)

fun VoteOption.isAbstainOption(): Boolean = label.contains("abstain", ignoreCase = true)
