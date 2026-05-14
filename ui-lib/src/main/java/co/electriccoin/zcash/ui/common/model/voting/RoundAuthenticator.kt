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
    ): RoundAuthStatus =
        rounds[roundIdHex]
            ?.let { entry -> authenticateEntry(chainEaPK, entry, trustedKeys) }
            ?: RoundAuthStatus.MISSING_ROUND

    private fun authenticateEntry(
        chainEaPK: ByteArray,
        entry: VotingServiceConfig.RoundEntry,
        trustedKeys: List<StaticVotingConfig.TrustedKey>
    ): RoundAuthStatus {
        val entryEaPk = runCatching { entry.eaPkBytes() }.getOrNull()
        return when {
            entry.authVersion != AUTH_VERSION_V1 -> RoundAuthStatus.UNKNOWN_AUTH_VERSION
            entryEaPk == null -> RoundAuthStatus.INVALID_SIGNATURES
            entryEaPk.size != EA_PK_BYTES -> RoundAuthStatus.INVALID_SIGNATURES
            !verifyEntrySignatures(entry, trustedKeys) -> RoundAuthStatus.INVALID_SIGNATURES
            !chainEaPK.contentEquals(entryEaPk) -> RoundAuthStatus.EA_PK_MISMATCH
            else -> RoundAuthStatus.AUTHENTICATED
        }
    }

    fun verifyEntrySignatures(
        entry: VotingServiceConfig.RoundEntry,
        trustedKeys: List<StaticVotingConfig.TrustedKey>
    ): Boolean =
        if (entry.authVersion != AUTH_VERSION_V1 || entry.signatures.isEmpty()) {
            false
        } else {
            val eaPk = runCatching { entry.eaPkBytes() }.getOrNull()
            eaPk?.takeIf { it.size == EA_PK_BYTES }?.let { verifiedEaPk ->
                val trustedByKeyId = trustedKeys.associateBy(StaticVotingConfig.TrustedKey::keyId)
                entry.signatures.any { signature ->
                    val trustedKey = trustedByKeyId[signature.keyId]
                    trustedKey?.takeIf { key ->
                        key.alg == StaticVotingConfig.ALG_ED25519 && signature.alg == key.alg
                    }?.let { key ->
                        val publicKey = runCatching { key.pubkeyBytes() }.getOrNull()
                        val signatureBytes = runCatching { signature.sigBytes() }.getOrNull()
                        publicKey != null &&
                            signatureBytes != null &&
                            publicKey.size == ED25519_PUBLIC_KEY_BYTES &&
                            signatureBytes.size == ED25519_SIGNATURE_BYTES &&
                            runCatching {
                                Ed25519Verify(publicKey).verify(signatureBytes, verifiedEaPk)
                            }.isSuccess
                    } == true
                }
            } == true
        }

    private const val EA_PK_BYTES = 32
    private const val ED25519_PUBLIC_KEY_BYTES = 32
    private const val ED25519_SIGNATURE_BYTES = 64
}

private const val DISPLAY_ROUND_ID_CHARS = 16
