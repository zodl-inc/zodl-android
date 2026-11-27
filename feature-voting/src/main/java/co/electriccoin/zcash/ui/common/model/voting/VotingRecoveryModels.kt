package co.electriccoin.zcash.ui.common.model.voting

data class VotingCommitmentBundleRecord(
    val bundleJson: String,
    val bundle: VoteCommitmentBundle,
    val vcTreePosition: Long
)

data class VotingVoteRecord(
    val proposalId: Int,
    val bundleIndex: Int,
    val choice: Int,
    val submitted: Boolean
)

data class VotingShareDelegationRecord(
    val roundId: String,
    val bundleIndex: Int,
    val proposalId: Int,
    val shareIndex: Int,
    val sentToUrls: List<String>,
    val nullifier: ByteArray,
    val confirmed: Boolean,
    val submitAt: Long,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingShareDelegationRecord) return false

        return roundId == other.roundId &&
            bundleIndex == other.bundleIndex &&
            proposalId == other.proposalId &&
            shareIndex == other.shareIndex &&
            sentToUrls == other.sentToUrls &&
            nullifier.contentEquals(other.nullifier) &&
            confirmed == other.confirmed &&
            submitAt == other.submitAt &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = roundId.hashCode()
        result = 31 * result + bundleIndex
        result = 31 * result + proposalId
        result = 31 * result + shareIndex
        result = 31 * result + sentToUrls.hashCode()
        result = 31 * result + nullifier.contentHashCode()
        result = 31 * result + confirmed.hashCode()
        result = 31 * result + submitAt.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
