package co.electriccoin.zcash.ui.common.model.voting

import com.google.crypto.tink.subtle.Ed25519Verify
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class RoundAuthStatus {
    AUTHENTICATED,
    MISSING_ROUND,
    UNKNOWN_AUTH_VERSION,
    INVALID_SIGNATURES,
    EA_PK_MISMATCH
}

class VotingRoundAuthenticationException(
    val status: RoundAuthStatus,
    val roundIdHex: String
) : VotingConfigException(votingRoundAuthMessage(status, roundIdHex))

private const val ROUND_ID_DISPLAY_LENGTH = 16

private fun votingRoundAuthMessage(status: RoundAuthStatus, roundIdHex: String): String =
    when (status) {
        RoundAuthStatus.AUTHENTICATED -> {
            "Voting round ${roundIdHex.take(ROUND_ID_DISPLAY_LENGTH)}... is authenticated"
        }

        RoundAuthStatus.UNKNOWN_AUTH_VERSION -> {
            "Voting round ${roundIdHex.take(ROUND_ID_DISPLAY_LENGTH)}... requires a newer wallet. " +
                "Please update the wallet."
        }

        RoundAuthStatus.MISSING_ROUND -> {
            "Voting config does not authenticate round ${roundIdHex.take(ROUND_ID_DISPLAY_LENGTH)}..."
        }

        RoundAuthStatus.INVALID_SIGNATURES -> {
            "Voting config signature is invalid for round ${roundIdHex.take(ROUND_ID_DISPLAY_LENGTH)}..."
        }

        RoundAuthStatus.EA_PK_MISMATCH -> {
            "Voting config EA key does not match round ${roundIdHex.take(ROUND_ID_DISPLAY_LENGTH)}..."
        }
    }

/**
 * The wallet accepts only [AUTH_VERSION_V2] dynamic-config round attestations.
 *
 * Policy decision (MOB-1678, `zcash_voting` 3.0 bump): v1 entries — signatures over the raw
 * 32-byte `ea_pk` only — are rejected outright, matching the crate's own verifier. Accepting
 * v1 would let the wallet act on a round whose attestation does not pin the round id or the
 * PIR layout it is about to be queried with. Deliberate and overturnable, not an oversight.
 */
object RoundAuthenticator {
    const val AUTH_VERSION_V2 = 2

    /**
     * Authenticate one chain-sourced round against the dynamic config registry.
     *
     * For `auth_version: 2`, the admin signature covers the fixed-width payload built by
     * [signingPayloadV2]: domain tag, round id, `ea_pk`, and the config's full PIR layout.
     * [pirLayout] MUST be the top-level `pir_layout` of the same dynamic config [rounds]
     * came from — the signature binds them, so a config mixing layouts and rounds from
     * different generations fails here by construction. After verifying the signature
     * against a key from the bundled static config, the wallet still checks that the chain
     * response carries exactly the same `ea_pk`; this chain-binding step catches a stale or
     * hostile vote server response and is not subsumed by v2.
     */
    fun authenticate(
        chainEaPK: ByteArray,
        roundIdHex: String,
        rounds: Map<String, VotingServiceConfig.RoundEntry>,
        trustedKeys: List<StaticVotingConfig.TrustedKey>,
        pirLayout: VotingPirLayout
    ): RoundAuthStatus {
        val entry = rounds[roundIdHex] ?: return RoundAuthStatus.MISSING_ROUND
        if (entry.authVersion != AUTH_VERSION_V2) {
            return RoundAuthStatus.UNKNOWN_AUTH_VERSION
        }

        val entryEaPk =
            runCatching { entry.eaPkBytes() }.getOrNull()
                ?: return RoundAuthStatus.INVALID_SIGNATURES
        if (entryEaPk.size != EA_PK_BYTES ||
            !verifyEntrySignatures(entry, roundIdHex, pirLayout, trustedKeys)
        ) {
            return RoundAuthStatus.INVALID_SIGNATURES
        }
        if (!chainEaPK.contentEquals(entryEaPk)) {
            return RoundAuthStatus.EA_PK_MISMATCH
        }
        return RoundAuthStatus.AUTHENTICATED
    }

    /**
     * Returns true when at least one entry signature validates over the v2 payload.
     *
     * The dynamic config names a trusted admin key by `key_id`; it does not inline public
     * keys. This resolves `key_id` into the static config, requires matching algorithms, and
     * verifies the ed25519 signature over [signingPayloadV2] for [roundIdHex] and [pirLayout].
     */
    fun verifyEntrySignatures(
        entry: VotingServiceConfig.RoundEntry,
        roundIdHex: String,
        pirLayout: VotingPirLayout,
        trustedKeys: List<StaticVotingConfig.TrustedKey>
    ): Boolean {
        if (entry.authVersion != AUTH_VERSION_V2 || entry.signatures.isEmpty()) {
            return false
        }

        val eaPk = runCatching { entry.eaPkBytes() }.getOrNull() ?: return false
        val payload = signingPayloadV2(roundIdHex, eaPk, pirLayout) ?: return false

        val trustedByKeyId = trustedKeys.associateBy(StaticVotingConfig.TrustedKey::keyId)
        return entry.signatures.any { signature ->
            val trustedKey = trustedByKeyId[signature.keyId] ?: return@any false
            if (trustedKey.alg != StaticVotingConfig.ALG_ED25519 || signature.alg != trustedKey.alg) {
                return@any false
            }

            val publicKey = runCatching { trustedKey.pubkeyBytes() }.getOrNull() ?: return@any false
            val signatureBytes = runCatching { signature.sigBytes() }.getOrNull() ?: return@any false
            publicKey.size == ED25519_PUBLIC_KEY_BYTES &&
                signatureBytes.size == ED25519_SIGNATURE_BYTES &&
                runCatching {
                    Ed25519Verify(publicKey).verify(signatureBytes, payload)
                }.isSuccess
        }
    }

    /**
     * Canonical round-auth v2 bytes, mirroring `zcash_voting::round_auth::RoundAuthPayloadV2`:
     *
     * `"zcash-shielded-vote:round-auth:v2" (33B) || round_id (32B) || ea_pk (32B) ||
     * pir_depth || tier0_layers || tier1_layers || poly_len` (each u32 little-endian).
     *
     * Returns null — the entry can never authenticate — when [roundIdHex] does not
     * strict-hex-decode to exactly 32 bytes (crate parity: an undecodable id is skipped
     * defensively), when [eaPk] is not 32 bytes, or when [pirLayout] predates
     * `pir_layout.poly_len` (a pre-3.0 config cannot have produced a v2 attestation).
     */
    fun signingPayloadV2(
        roundIdHex: String,
        eaPk: ByteArray,
        pirLayout: VotingPirLayout
    ): ByteArray? {
        val roundId = runCatching { roundIdHex.lowercaseHexToBytes() }.getOrNull() ?: return null
        if (roundId.size != ROUND_ID_BYTES || eaPk.size != EA_PK_BYTES || pirLayout.polyLen == 0) {
            return null
        }

        val payloadSize = DOMAIN_TAG_V2.size + roundId.size + eaPk.size + PIR_LAYOUT_FIELD_COUNT * Int.SIZE_BYTES
        val buffer = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(DOMAIN_TAG_V2)
        buffer.put(roundId)
        buffer.put(eaPk)
        buffer.putInt(pirLayout.pirDepth)
        buffer.putInt(pirLayout.tier0Layers)
        buffer.putInt(pirLayout.tier1Layers)
        buffer.putInt(pirLayout.polyLen)
        return buffer.array()
    }

    private val DOMAIN_TAG_V2 = "zcash-shielded-vote:round-auth:v2".toByteArray(Charsets.US_ASCII)
    private const val ROUND_ID_BYTES = 32
    private const val EA_PK_BYTES = 32
    private const val ED25519_PUBLIC_KEY_BYTES = 32
    private const val ED25519_SIGNATURE_BYTES = 64
    private const val PIR_LAYOUT_FIELD_COUNT = 4
}
