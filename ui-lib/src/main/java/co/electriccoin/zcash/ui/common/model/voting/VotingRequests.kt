package co.electriccoin.zcash.ui.common.model.voting

private fun List<ByteArray>.contentListEquals(other: List<ByteArray>) =
    size == other.size && indices.all { this[it].contentEquals(other[it]) }

private fun List<ByteArray>.contentListHashCode() =
    fold(1) { acc, value -> 31 * acc + value.contentHashCode() }

private fun <T> List<T>.contentListEquals(
    other: List<T>,
    matcher: (T, T) -> Boolean
) = size == other.size && indices.all { matcher(this[it], other[it]) }

private fun <T> List<T>.contentListHashCode(hasher: (T) -> Int) =
    fold(1) { acc, value -> 31 * acc + hasher(value) }

data class DelegationRegistration(
    val rk: ByteArray,
    val spendAuthSig: ByteArray,
    val signedNoteNullifier: ByteArray,
    val cmxNew: ByteArray,
    val vanCmx: ByteArray,
    val govNullifiers: List<ByteArray>,
    val proof: ByteArray,
    val voteRoundId: ByteArray,
    val sighash: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DelegationRegistration) return false

        return rk.contentEquals(other.rk) &&
            spendAuthSig.contentEquals(other.spendAuthSig) &&
            signedNoteNullifier.contentEquals(other.signedNoteNullifier) &&
            cmxNew.contentEquals(other.cmxNew) &&
            vanCmx.contentEquals(other.vanCmx) &&
            govNullifiers.contentListEquals(other.govNullifiers) &&
            proof.contentEquals(other.proof) &&
            voteRoundId.contentEquals(other.voteRoundId) &&
            sighash.contentEquals(other.sighash)
    }

    override fun hashCode(): Int {
        var result = rk.contentHashCode()
        result = 31 * result + spendAuthSig.contentHashCode()
        result = 31 * result + signedNoteNullifier.contentHashCode()
        result = 31 * result + cmxNew.contentHashCode()
        result = 31 * result + vanCmx.contentHashCode()
        result = 31 * result + govNullifiers.contentListHashCode()
        result = 31 * result + proof.contentHashCode()
        result = 31 * result + voteRoundId.contentHashCode()
        result = 31 * result + sighash.contentHashCode()
        return result
    }
}

data class EncryptedShare(
    val c1: ByteArray,
    val c2: ByteArray,
    val shareIndex: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedShare) return false

        return c1.contentEquals(other.c1) &&
            c2.contentEquals(other.c2) &&
            shareIndex == other.shareIndex
    }

    override fun hashCode(): Int {
        var result = c1.contentHashCode()
        result = 31 * result + c2.contentHashCode()
        result = 31 * result + shareIndex
        return result
    }
}

data class VoteCommitmentBundle(
    val vanNullifier: ByteArray,
    val voteAuthorityNoteNew: ByteArray,
    val voteCommitment: ByteArray,
    val proposalId: Int,
    val proof: ByteArray,
    val encShares: List<EncryptedShare>,
    val anchorHeight: Int,
    val voteRoundId: String,
    val sharesHash: ByteArray,
    val shareBlindFactors: List<ByteArray> = emptyList(),
    val shareComms: List<ByteArray> = emptyList(),
    val rVpkBytes: ByteArray = ByteArray(0),
    val alphaV: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoteCommitmentBundle) return false

        return vanNullifier.contentEquals(other.vanNullifier) &&
            voteAuthorityNoteNew.contentEquals(other.voteAuthorityNoteNew) &&
            voteCommitment.contentEquals(other.voteCommitment) &&
            proposalId == other.proposalId &&
            proof.contentEquals(other.proof) &&
            encShares.contentListEquals(other.encShares, EncryptedShare::equals) &&
            anchorHeight == other.anchorHeight &&
            voteRoundId == other.voteRoundId &&
            sharesHash.contentEquals(other.sharesHash) &&
            shareBlindFactors.contentListEquals(other.shareBlindFactors) &&
            shareComms.contentListEquals(other.shareComms) &&
            rVpkBytes.contentEquals(other.rVpkBytes) &&
            alphaV.contentEquals(other.alphaV)
    }

    override fun hashCode(): Int {
        var result = vanNullifier.contentHashCode()
        result = 31 * result + voteAuthorityNoteNew.contentHashCode()
        result = 31 * result + voteCommitment.contentHashCode()
        result = 31 * result + proposalId
        result = 31 * result + proof.contentHashCode()
        result = 31 * result + encShares.contentListHashCode(EncryptedShare::hashCode)
        result = 31 * result + anchorHeight
        result = 31 * result + voteRoundId.hashCode()
        result = 31 * result + sharesHash.contentHashCode()
        result = 31 * result + shareBlindFactors.contentListHashCode()
        result = 31 * result + shareComms.contentListHashCode()
        result = 31 * result + rVpkBytes.contentHashCode()
        result = 31 * result + alphaV.contentHashCode()
        return result
    }
}

data class SharePayload(
    val sharesHash: ByteArray,
    val proposalId: Int,
    val voteDecision: Int,
    val encShare: EncryptedShare,
    val treePosition: Long,
    val allEncShares: List<EncryptedShare> = emptyList(),
    val shareComms: List<ByteArray> = emptyList(),
    val primaryBlind: ByteArray = ByteArray(0),
    val submitAt: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SharePayload) return false

        return sharesHash.contentEquals(other.sharesHash) &&
            proposalId == other.proposalId &&
            voteDecision == other.voteDecision &&
            encShare == other.encShare &&
            treePosition == other.treePosition &&
            allEncShares.contentListEquals(other.allEncShares, EncryptedShare::equals) &&
            shareComms.contentListEquals(other.shareComms) &&
            primaryBlind.contentEquals(other.primaryBlind) &&
            submitAt == other.submitAt
    }

    override fun hashCode(): Int {
        var result = sharesHash.contentHashCode()
        result = 31 * result + proposalId
        result = 31 * result + voteDecision
        result = 31 * result + encShare.hashCode()
        result = 31 * result + treePosition.hashCode()
        result = 31 * result + allEncShares.contentListHashCode(EncryptedShare::hashCode)
        result = 31 * result + shareComms.contentListHashCode()
        result = 31 * result + primaryBlind.contentHashCode()
        result = 31 * result + submitAt.hashCode()
        return result
    }
}

data class CastVoteSignature(
    val voteAuthSig: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CastVoteSignature) return false

        return voteAuthSig.contentEquals(other.voteAuthSig)
    }

    override fun hashCode(): Int = voteAuthSig.contentHashCode()
}

data class DelegatedShareInfo(
    val shareIndex: Int,
    val proposalId: Int,
    val acceptedByServers: List<String>
)

enum class ShareConfirmationResult {
    PENDING,
    CONFIRMED
}
