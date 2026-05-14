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

fun Proposal.abstainOptionId(): Int =
    options
        .firstOrNull(VoteOption::isAbstainOption)
        ?.id
        ?: ((options.maxOfOrNull(VoteOption::id) ?: 0) + 1)

internal fun Proposal.isSyntheticAbstainChoice(choiceId: Int): Boolean =
    options.none(VoteOption::isAbstainOption) && choiceId == abstainOptionId()

fun Proposal.optionsWithAbstain(): List<VoteOption> =
    if (options.any(VoteOption::isAbstainOption)) {
        options
    } else {
        options + VoteOption(id = abstainOptionId(), label = "Abstain")
    }
