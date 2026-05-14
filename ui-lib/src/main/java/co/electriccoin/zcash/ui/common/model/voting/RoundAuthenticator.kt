package co.electriccoin.zcash.ui.common.model.voting

import com.google.crypto.tink.subtle.Ed25519Verify

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

private fun votingRoundAuthMessage(status: RoundAuthStatus, roundIdHex: String): String =
    when (status) {
        RoundAuthStatus.AUTHENTICATED -> {
            "Voting round ${roundIdHex.take(DISPLAY_ROUND_ID_CHARS)}... is authenticated"
        }

        RoundAuthStatus.UNKNOWN_AUTH_VERSION -> {
            "Voting round ${roundIdHex.take(DISPLAY_ROUND_ID_CHARS)}... requires a newer wallet. " +
                "Please update the wallet."
        }

        RoundAuthStatus.MISSING_ROUND -> {
            "Voting config does not authenticate round ${roundIdHex.take(DISPLAY_ROUND_ID_CHARS)}..."
        }

        RoundAuthStatus.INVALID_SIGNATURES -> {
            "Voting config signature is invalid for round ${roundIdHex.take(DISPLAY_ROUND_ID_CHARS)}..."
        }

        RoundAuthStatus.EA_PK_MISMATCH -> {
            "Voting config EA key does not match round ${roundIdHex.take(DISPLAY_ROUND_ID_CHARS)}..."
        }
    }

object RoundAuthenticator {
    const val AUTH_VERSION_V1 = 1

    fun authenticate(
        chainEaPK: ByteArray,
        roundIdHex: String,
        rounds: Map<String, VotingServiceConfig.RoundEntry>,
        trustedKeys: List<StaticVotingConfig.TrustedKey>
    ): RoundAuthStatus {
        val entry = rounds[roundIdHex] ?: return RoundAuthStatus.MISSING_ROUND
        if (entry.authVersion != AUTH_VERSION_V1) {
            return RoundAuthStatus.UNKNOWN_AUTH_VERSION
        }

        val entryEaPk =
            runCatching { entry.eaPkBytes() }.getOrNull()
                ?: return RoundAuthStatus.INVALID_SIGNATURES
        if (entryEaPk.size != EA_PK_BYTES || !verifyEntrySignatures(entry, trustedKeys)) {
            return RoundAuthStatus.INVALID_SIGNATURES
        }
        if (!chainEaPK.contentEquals(entryEaPk)) {
            return RoundAuthStatus.EA_PK_MISMATCH
        }
        return RoundAuthStatus.AUTHENTICATED
    }

    fun verifyEntrySignatures(
        entry: VotingServiceConfig.RoundEntry,
        trustedKeys: List<StaticVotingConfig.TrustedKey>
    ): Boolean {
        if (entry.authVersion != AUTH_VERSION_V1 || entry.signatures.isEmpty()) {
            return false
        }

        val eaPk = runCatching { entry.eaPkBytes() }.getOrNull() ?: return false
        if (eaPk.size != EA_PK_BYTES) {
            return false
        }

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
                    Ed25519Verify(publicKey).verify(signatureBytes, eaPk)
                }.isSuccess
        }
    }

    private const val EA_PK_BYTES = 32
    private const val ED25519_PUBLIC_KEY_BYTES = 32
    private const val ED25519_SIGNATURE_BYTES = 64
}

private const val DISPLAY_ROUND_ID_CHARS = 16
