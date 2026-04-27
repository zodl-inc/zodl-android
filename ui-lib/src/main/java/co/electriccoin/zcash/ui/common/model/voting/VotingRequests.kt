package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.Serializable

@Serializable
data class DelegationRegistration(
    val roundIdHex: String,
    val proof: ByteArray,
    val hotkeyAddress: String,
    val snapshotHeight: Long,
    // Full submission fields (from DelegationSubmissionData)
    val rk: ByteArray = ByteArray(0),
    val spendAuthSig: ByteArray = ByteArray(0),
    val sighash: ByteArray = ByteArray(0),
    val nfSigned: ByteArray = ByteArray(0),
    val cmxNew: ByteArray = ByteArray(0),
    val govComm: ByteArray = ByteArray(0),
    val govNullifiers: List<ByteArray> = emptyList(),
    val alpha: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DelegationRegistration) return false
        return roundIdHex == other.roundIdHex
    }

    override fun hashCode(): Int = roundIdHex.hashCode()
}

@Serializable
data class SharePayload(
    val shareIndex: Int,
    val encryptedShare: ByteArray,
    val serverUrl: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SharePayload) return false
        return shareIndex == other.shareIndex && encryptedShare.contentEquals(other.encryptedShare)
    }

    override fun hashCode(): Int {
        var result = shareIndex
        result = 31 * result + encryptedShare.contentHashCode()
        return result
    }
}

@Serializable
data class VoteCommitmentBundle(
    val proof: ByteArray,
    val encryptedShares: List<ByteArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoteCommitmentBundle) return false
        return proof.contentEquals(other.proof)
    }

    override fun hashCode(): Int = proof.contentHashCode()
}

@Serializable
data class CastVoteSignature(
    val roundIdHex: String,
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CastVoteSignature) return false
        return roundIdHex == other.roundIdHex && signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = roundIdHex.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}

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
