package co.electriccoin.zcash.ui.common.model.voting

data class Proposal(
    val id: Int,
    val title: String,
    val description: String,
    val options: List<VoteOption>,
    val zipNumber: String? = null,
    val forumUrl: String? = null
)
