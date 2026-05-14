@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

data class TallyResults(
    val roundId: String,
    val proposals: List<ProposalTally>
)

data class ProposalTally(
    val proposalId: Int,
    val options: List<OptionTally>
)

data class OptionTally(
    val optionId: Int,
    val weight: Long
)

@Serializable
@JsonIgnoreUnknownKeys
data class ChainTallyResultsResponse(
    @SerialName("results")
    val results: List<ChainTallyResultEntry> = emptyList()
)

@Serializable
@JsonIgnoreUnknownKeys
data class ChainTallyResultEntry(
    @SerialName("proposal_id")
    val proposalId: Int,
    @SerialName("vote_decision")
    val voteDecision: Int = 0,
    @SerialName("total_value")
    val totalValue: Long = 0,
)

fun ChainTallyResultsResponse.toTallyResults(
    roundId: String,
    ballotDivisorZatoshi: Long
): TallyResults {
    val proposalTallies =
        results
            .groupBy(ChainTallyResultEntry::proposalId)
            .map { (proposalId, entries) ->
                ProposalTally(
                    proposalId = proposalId,
                    options =
                        entries.map { entry ->
                            OptionTally(
                                optionId = entry.voteDecision,
                                weight = entry.totalValue * ballotDivisorZatoshi
                            )
                        }
                )
            }

    return TallyResults(
        roundId = roundId,
        proposals = proposalTallies
    )
}
