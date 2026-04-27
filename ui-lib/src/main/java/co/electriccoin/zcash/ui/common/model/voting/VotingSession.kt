package co.electriccoin.zcash.ui.common.model.voting

import java.time.Instant

enum class SessionStatus {
    ACTIVE,
    TALLYING,
    COMPLETED,
    CANCELLED
}

data class VotingSession(
    val voteRoundId: ByteArray,
    val snapshotHeight: Long,
    val proposalsHash: ByteArray,
    val voteEndTime: Instant,
    val ceremonyStart: Instant,
    val eaPK: ByteArray,
    val vkZkp1: ByteArray,
    val vkZkp2: ByteArray,
    val vkZkp3: ByteArray,
    val ncRoot: ByteArray,
    val nullifierIMTRoot: ByteArray,
    val creator: String,
    val title: String,
    val description: String,
    val discussionUrl: String?,
    val proposals: List<Proposal>,
    val status: SessionStatus,
    val createdAtHeight: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingSession) return false
        return voteRoundId.contentEquals(other.voteRoundId)
    }

    override fun hashCode(): Int = voteRoundId.contentHashCode()
}
