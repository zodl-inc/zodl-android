package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Proposal(
    val id: Int,
    val title: String,
    val description: String,
    val options: List<VoteOption>,
    @SerialName("zip_number")
    val zipNumber: String? = null,
    @SerialName("forum_url")
    val forumUrl: String? = null
)
