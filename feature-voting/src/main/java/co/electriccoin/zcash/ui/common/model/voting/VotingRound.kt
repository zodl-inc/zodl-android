package co.electriccoin.zcash.ui.common.model.voting

import java.time.Instant

data class VotingRound(
    val id: String,
    val title: String,
    val description: String,
    val discussionUrl: String?,
    val createdAtHeight: Long = 0,
    val snapshotHeight: Long,
    val snapshotDate: Instant,
    val votingStart: Instant,
    val votingEnd: Instant,
    val proposals: List<Proposal>,
    val status: SessionStatus = SessionStatus.ACTIVE
)
