@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.time.Instant
import java.util.Base64

@Serializable
@JsonIgnoreUnknownKeys
data class ChainRoundsResponse(
    val rounds: List<ChainRoundDto>? = null
)

@Serializable
@JsonIgnoreUnknownKeys
data class ChainActiveRoundResponse(
    val round: ChainRoundDto? = null
)

@Serializable
data class ChainTxResponse(
    val tx: ChainTxDto? = null
)

@Serializable
data class ChainTxDto(
    val hash: String = "",
    val confirmed: Boolean = false,
)

@Serializable
@JsonIgnoreUnknownKeys
data class ChainRoundDto(
    @SerialName("vote_round_id") val voteRoundId: String,
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("snapshot_height") val snapshotHeight: Long,
    @SerialName("snapshot_blockhash") val snapshotBlockhash: String = "",
    @SerialName("vote_end_time") val voteEndTime: Long,
    @SerialName("ceremony_phase_start") val ceremonyPhaseStart: Long = 0,
    @SerialName("status") val status: Int = 1,
    @SerialName("proposals") val proposals: List<ChainProposalDto> = emptyList(),
    @SerialName("proposals_hash") val proposalsHash: String = "",
    @SerialName("ea_pk") val eaPk: String = "",
    @SerialName("vk_zkp1") val vkZkp1: String = "",
    @SerialName("vk_zkp2") val vkZkp2: String = "",
    @SerialName("vk_zkp3") val vkZkp3: String = "",
    @SerialName("nc_root") val ncRoot: String = "",
    @SerialName("nullifier_imt_root") val nullifierImtRoot: String = "",
    @SerialName("creator") val creator: String = "",
    @SerialName("discussion_url") val discussionUrl: String? = null,
    @SerialName("created_at_height") val createdAtHeight: Long = 0,
) {
    fun toVotingRound(): VotingRound {
        val validatedProposals = validatedProposals()
        return VotingRound(
            id = voteRoundId.normalizeRoundId(),
            title = title,
            description = description,
            discussionUrl = discussionUrl,
            createdAtHeight = createdAtHeight,
            snapshotHeight = snapshotHeight,
            snapshotDate = Instant.ofEpochSecond(ceremonyPhaseStart.takeIf { it > 0 } ?: voteEndTime),
            votingStart = Instant.ofEpochSecond(ceremonyPhaseStart),
            votingEnd = Instant.ofEpochSecond(voteEndTime),
            proposals = validatedProposals,
            status =
                when (status) {
                    1 -> SessionStatus.ACTIVE
                    2 -> SessionStatus.TALLYING
                    3 -> SessionStatus.COMPLETED
                    else -> SessionStatus.CANCELLED
                }
        )
    }

    fun toVotingSession(): VotingSession {
        val validatedProposals = validatedProposals()
        return VotingSession(
            voteRoundId = voteRoundId.decodeBinaryField(),
            snapshotHeight = snapshotHeight,
            snapshotBlockhash = snapshotBlockhash.decodeBinaryField(),
            proposalsHash = proposalsHash.decodeBinaryField(),
            voteEndTime = Instant.ofEpochSecond(voteEndTime),
            ceremonyStart = Instant.ofEpochSecond(ceremonyPhaseStart),
            eaPK = eaPk.decodeBinaryField(),
            vkZkp1 = vkZkp1.decodeBinaryField(),
            vkZkp2 = vkZkp2.decodeBinaryField(),
            vkZkp3 = vkZkp3.decodeBinaryField(),
            ncRoot = ncRoot.decodeBinaryField(),
            nullifierIMTRoot = nullifierImtRoot.decodeBinaryField(),
            creator = creator,
            title = title,
            description = description,
            discussionUrl = discussionUrl,
            proposals = validatedProposals,
            status =
                when (status) {
                    1 -> SessionStatus.ACTIVE
                    2 -> SessionStatus.TALLYING
                    3 -> SessionStatus.COMPLETED
                    else -> SessionStatus.CANCELLED
                },
            createdAtHeight = createdAtHeight
        )
    }

    private fun validatedProposals(): List<Proposal> =
        proposals
            .map(ChainProposalDto::toProposal)
            .also(::validateChainProposals)
}

@Serializable
@JsonIgnoreUnknownKeys
data class ChainProposalDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",
    @SerialName("options") val options: List<ChainVoteOptionDto> = emptyList(),
    @SerialName("zip_number") val zipNumber: String? = null,
    @SerialName("forum_url") val forumUrl: String? = null,
) {
    fun toProposal(): Proposal =
        Proposal(
            id = id,
            title = title,
            description = description,
            options = options.map(ChainVoteOptionDto::toVoteOption),
            zipNumber = zipNumber,
            forumUrl = forumUrl
        )
}

@Serializable
@JsonIgnoreUnknownKeys
data class ChainVoteOptionDto(
    @SerialName("label") val label: String,
    @SerialName("index") val index: Int? = null,
) {
    fun toVoteOption(): VoteOption =
        VoteOption(
            id = index ?: DEFAULT_MISSING_OPTION_INDEX,
            label = label
        )
}

private fun validateChainProposals(proposals: List<Proposal>) {
    if (proposals.size !in MIN_PROPOSALS..MAX_PROPOSALS) {
        throw VotingConfigException(
            "proposals must contain between $MIN_PROPOSALS and $MAX_PROPOSALS entries"
        )
    }

    val proposalIds = mutableSetOf<Int>()
    proposals.forEach { proposal ->
        if (proposal.id !in MIN_PROPOSAL_ID..MAX_PROPOSAL_ID) {
            throw VotingConfigException(
                "proposal id must be in the range $MIN_PROPOSAL_ID to $MAX_PROPOSAL_ID"
            )
        }
        if (!proposalIds.add(proposal.id)) {
            throw VotingConfigException("proposal ids must be unique")
        }
        if (proposal.options.size !in MIN_OPTIONS..MAX_OPTIONS) {
            throw VotingConfigException(
                "proposal options must contain between $MIN_OPTIONS and $MAX_OPTIONS entries"
            )
        }

        val optionIds = proposal.options.map(VoteOption::id)
        if (optionIds.toSet().size != optionIds.size) {
            throw VotingConfigException("option index values within a proposal must be unique")
        }
        val expectedOptionIds = 0 until proposal.options.size
        if (optionIds.sorted() != expectedOptionIds.toList()) {
            throw VotingConfigException("option index values within a proposal must be 0-indexed contiguous")
        }
    }
}

private fun String.normalizeRoundId(): String {
    val trimmed = trim()
    if (trimmed.isHexEncoded()) {
        return trimmed.lowercase()
    }

    val decoded = runCatching { Base64.getDecoder().decode(trimmed) }.getOrNull()
    return decoded?.toHexString() ?: trimmed
}

private fun String.decodeBinaryField(): ByteArray {
    val trimmed = trim()
    if (trimmed.isEmpty()) return ByteArray(0)
    if (trimmed.isHexEncoded()) return trimmed.hexToBytes()

    return runCatching { Base64.getDecoder().decode(trimmed) }.getOrDefault(ByteArray(0))
}

private fun String.isHexEncoded(): Boolean =
    isNotEmpty() && length % 2 == 0 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { chunk -> chunk.toInt(16).toByte() }.toByteArray()

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte) }

private const val MIN_PROPOSALS = 1
private const val MAX_PROPOSALS = 15
private const val MIN_PROPOSAL_ID = 1
private const val MAX_PROPOSAL_ID = 15
private const val MIN_OPTIONS = 2
private const val MAX_OPTIONS = 8
private const val DEFAULT_MISSING_OPTION_INDEX = 0
