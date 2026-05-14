package co.electriccoin.zcash.ui.common.model.voting

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

fun VotingDelegationSubmission.toDelegationRegistration() =
    DelegationRegistration(
        rk = rk,
        spendAuthSig = spendAuthSig,
        signedNoteNullifier = nfSigned,
        cmxNew = cmxNew,
        vanCmx = govComm,
        govNullifiers = govNullifiers,
        proof = proof,
        voteRoundId = voteRoundId.hexStringToBytes(),
        sighash = sighash
    )

fun VotingVoteCommitment.toVoteCommitmentBundle(): VoteCommitmentBundle =
    rawBundleJson.toVoteCommitmentBundle()

fun String.toVoteCommitmentBundle(): VoteCommitmentBundle {
    val json = JSONObject(this)
    return VoteCommitmentBundle(
        vanNullifier = json.getString("van_nullifier").hexStringToBytes(),
        voteAuthorityNoteNew = json.getString("vote_authority_note_new").hexStringToBytes(),
        voteCommitment = json.getString("vote_commitment").hexStringToBytes(),
        proposalId = json.getInt("proposal_id"),
        proof = json.getString("proof").hexStringToBytes(),
        encShares = json.getJSONArray("enc_shares").toEncryptedShares(),
        anchorHeight = json.getInt("anchor_height"),
        voteRoundId = json.getString("vote_round_id"),
        sharesHash = json.getString("shares_hash").hexStringToBytes(),
        shareBlindFactors = json.optJSONArray("share_blinds").toByteArrays(),
        shareComms = json.optJSONArray("share_comms").toByteArrays(),
        rVpkBytes =
            json
                .optString("r_vpk_bytes")
                .takeIf { it.isNotEmpty() }
                ?.hexStringToBytes()
                ?: ByteArray(0),
        alphaV =
            json
                .optString("alpha_v")
                .takeIf { it.isNotEmpty() }
                ?.hexStringToBytes()
                ?: ByteArray(0)
    )
}

fun String.toSharePayloads(): List<SharePayload> {
    val json = JSONArray(this)
    return buildList {
        for (index in 0 until json.length()) {
            val payload = json.getJSONObject(index)
            add(
                SharePayload(
                    sharesHash = payload.getString("shares_hash").hexStringToBytes(),
                    proposalId = payload.getInt("proposal_id"),
                    voteDecision = payload.getInt("vote_decision"),
                    encShare = payload.getJSONObject("enc_share").toEncryptedShare(),
                    treePosition = payload.getLong("tree_position"),
                    allEncShares = payload.optJSONArray("all_enc_shares").toEncryptedShares(),
                    shareComms = payload.optJSONArray("share_comms").toByteArrays(),
                    primaryBlind =
                        payload
                            .optString("primary_blind")
                            .takeIf { it.isNotEmpty() }
                            ?.hexStringToBytes()
                            ?: ByteArray(0)
                )
            )
        }
    }
}

fun SharePayload.withSubmitAt(submitAt: Long) =
    copy(submitAt = submitAt)

fun List<EncryptedShare>.toEncryptedSharesJson(): String =
    JSONArray(
        map { share ->
            JSONObject()
                .put("c1", share.c1.toHexString())
                .put("c2", share.c2.toHexString())
                .put("share_index", share.shareIndex)
        }
    ).toString()

fun ByteArray.toBase64String(): String =
    Base64.getEncoder().encodeToString(this)

fun String.hexStringToBytes(): ByteArray =
    chunked(2).map { chunk -> chunk.toInt(16).toByte() }.toByteArray()

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun JSONObject.toEncryptedShare() =
    EncryptedShare(
        c1 = getString("c1").hexStringToBytes(),
        c2 = getString("c2").hexStringToBytes(),
        shareIndex = getInt("share_index")
    )

private fun JSONArray?.toEncryptedShares(): List<EncryptedShare> {
    if (this == null) return emptyList()

    return buildList {
        for (index in 0 until length()) {
            add(getJSONObject(index).toEncryptedShare())
        }
    }
}

private fun JSONArray?.toByteArrays(): List<ByteArray> {
    if (this == null) return emptyList()

    return buildList {
        for (index in 0 until length()) {
            add(getString(index).hexStringToBytes())
        }
    }
}
