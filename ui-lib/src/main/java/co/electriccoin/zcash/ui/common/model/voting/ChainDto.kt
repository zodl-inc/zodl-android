package co.electriccoin.zcash.ui.common.model.voting

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * DTOs matching the JSON returned by the shielded-vote chain.
 * Field names verified against live chain responses.
 */

@Serializable
data class ChainRoundsResponse(
    val rounds: List<ChainRoundDto>? = null
)

@Serializable
data class ChainRoundResponse(
    val round: ChainRoundDto
)

@Serializable
data class ChainActiveRoundResponse(
    val round: ChainRoundDto? = null
)

@Serializable
data class ChainRoundDto(
    @SerialName("vote_round_id") val voteRoundId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("snapshot_height") val snapshotHeight: Long,
    @SerialName("vote_end_time") val voteEndTime: Long,
    @SerialName("created_at_height") val createdAtHeight: Long = 0,
    @SerialName("status") val status: Int = 1,
    @SerialName("proposals") val proposals: List<ChainProposalDto> = emptyList(),
    @SerialName("ea_pk") val eaPk: String = "",
    @SerialName("nc_root") val ncRoot: String = "",
    @SerialName("nullifier_imt_root") val nullifierImtRoot: String = "",
    @SerialName("snapshot_blockhash") val snapshotBlockhash: String = "",
) {
    fun toVotingRound(): VotingRound =
        VotingRound(
            id = voteRoundId,
            title = title,
            description = description,
            discussionUrl = null,
            snapshotHeight = snapshotHeight,
            snapshotDate = Instant.ofEpochSecond(voteEndTime),
            votingStart = Instant.EPOCH,
            votingEnd = Instant.ofEpochSecond(voteEndTime),
            proposals = proposals.map { it.toProposal() },
            status =
                when (status) {
                    1 -> SessionStatus.ACTIVE
                    2 -> SessionStatus.TALLYING
                    3 -> SessionStatus.COMPLETED
                    else -> SessionStatus.CANCELLED
                }
        )

    fun eaPkBytes(): ByteArray = runCatching { Base64.decode(eaPk, Base64.DEFAULT) }.getOrDefault(ByteArray(0))

    fun ncRootBytes(): ByteArray = runCatching { Base64.decode(ncRoot, Base64.DEFAULT) }.getOrDefault(ByteArray(0))

    fun nullifierImtRootBytes(): ByteArray = runCatching { Base64.decode(nullifierImtRoot, Base64.DEFAULT) }.getOrDefault(ByteArray(0))
}

@Serializable
data class ChainProposalDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("options") val options: List<ChainVoteOptionDto> = emptyList(),
) {
    fun toProposal(): Proposal =
        Proposal(
            id = id,
            title = title,
            description = description,
            options = options.mapIndexed { idx, opt -> opt.toVoteOption(idx) },
            zipNumber = null,
            forumUrl = null,
        )
}

@Serializable
data class ChainVoteOptionDto(
    @SerialName("label") val label: String,
    @SerialName("index") val index: Int? = null,
) {
    fun toVoteOption(fallbackIdx: Int): VoteOption =
        VoteOption(
            id = index ?: fallbackIdx,
            label = label,
        )
}

@Serializable
data class ChainTxResponse(
    val tx: ChainTxDto? = null
)

@Serializable
data class ChainTxDto(
    val hash: String = "",
    val confirmed: Boolean = false,
)
