package co.electriccoin.zcash.ui.common.model.voting

private fun List<ByteArray>.contentListEquals(other: List<ByteArray>) =
    size == other.size && indices.all { this[it].contentEquals(other[it]) }

private fun List<ByteArray>.contentListHashCode() =
    fold(1) { acc, value -> HASH_MULTIPLIER * acc + value.contentHashCode() }

private const val HASH_MULTIPLIER = 31

data class VotingBundleSetupResult(
    val bundleCount: Int,
    val eligibleWeight: Long,
    val bundleWeights: List<Long> = emptyList()
)

data class VotingHotkey(
    val rawAddress: ByteArray,
    val address: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingHotkey) return false

        return rawAddress.contentEquals(other.rawAddress) &&
            address == other.address
    }

    override fun hashCode(): Int {
        var result = rawAddress.contentHashCode()
        result = 31 * result + address.hashCode()
        return result
    }
}

data class VotingGovernancePczt(
    val pcztBytes: ByteArray,
    val rk: ByteArray,
    val sighash: ByteArray,
    val actionIndex: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingGovernancePczt) return false

        return rk.contentEquals(other.rk) &&
            sighash.contentEquals(other.sighash)
    }

    override fun hashCode(): Int {
        var result = rk.contentHashCode()
        result = 31 * result + sighash.contentHashCode()
        return result
    }
}

data class VotingDelegationProof(
    val proof: ByteArray,
    val publicInputs: List<ByteArray>,
    val nfSigned: ByteArray,
    val cmxNew: ByteArray,
    val govNullifiers: List<ByteArray>,
    val vanComm: ByteArray,
    val rk: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingDelegationProof) return false

        return proof.contentEquals(other.proof) &&
            publicInputs.contentListEquals(other.publicInputs) &&
            govNullifiers.contentListEquals(other.govNullifiers)
    }

    override fun hashCode(): Int {
        var result = proof.contentHashCode()
        result = 31 * result + publicInputs.contentListHashCode()
        result = 31 * result + govNullifiers.contentListHashCode()
        return result
    }
}

data class VotingDelegationPirPrecomputeResult(
    val cachedCount: Long,
    val fetchedCount: Long
)

data class VotingDelegationSubmission(
    val proof: ByteArray,
    val rk: ByteArray,
    val spendAuthSig: ByteArray,
    val sighash: ByteArray,
    val tx1Effects: ByteArray,
    val nfSigned: ByteArray,
    val cmxNew: ByteArray,
    val govComm: ByteArray,
    val govNullifiers: List<ByteArray>,
    val alpha: ByteArray,
    val voteRoundId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingDelegationSubmission) return false

        return sighash.contentEquals(other.sighash)
    }

    override fun hashCode(): Int = sighash.contentHashCode()
}

data class VotingVoteCommitment(
    val vanNullifier: ByteArray,
    val voteAuthorityNoteNew: ByteArray,
    val voteCommitment: ByteArray,
    val rVpk: ByteArray,
    val voteAuthSig: ByteArray,
    val anchorHeight: Int,
    val encSharesJson: String,
    val rawBundleJson: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VotingVoteCommitment) return false

        return voteCommitment.contentEquals(other.voteCommitment)
    }

    override fun hashCode(): Int = voteCommitment.contentHashCode()
}

/**
 * Recovered `vote::commit` result for an already-committed vote, as returned by
 * `recoverCommittedVoteNative` once [VotingCryptoClient.recordVcPosition] has recorded its
 * confirmed vote-commitment-tree position. [sharePayloadsJson] carries the crate-computed share
 * payloads for this vote so callers reconstruct delegation shares from recovery state instead of
 * decoding a stored commitment bundle themselves.
 */
data class VotingCommittedVoteRecord(
    val bundleIndex: Int,
    val proposalId: Int,
    val vcTreePosition: Long,
    val sharePayloadsJson: String
)

sealed interface VotingTxHashLookup {
    data object NotFound : VotingTxHashLookup

    data class Present(
        val txHash: String
    ) : VotingTxHashLookup
}
