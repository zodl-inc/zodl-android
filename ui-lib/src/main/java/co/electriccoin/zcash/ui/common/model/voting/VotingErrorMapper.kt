package co.electriccoin.zcash.ui.common.model.voting

/**
 * Maps raw server/SDK error messages to user-friendly descriptions.
 */
internal object VotingErrorMapper {
    fun toUserFriendlyMessage(rawMessage: String): String {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("nullifier") && lower.contains("spent") -> {
                "Your vote authorization has already been used. Each wallet can only vote once per round."
            }

            lower.contains("round") && (lower.contains("not active") || lower.contains("inactive") || lower.contains("closed")) -> {
                "This voting round is no longer active. The poll may have closed while you were voting."
            }

            lower.contains("pir") || lower.contains("private information retrieval") -> {
                "Could not connect to the privacy-preserving vote server. Please check your connection and try again."
            }

            lower.contains("insufficient") && lower.contains("fund") -> {
                "Insufficient funds to authorize your vote. A small fee is required."
            }

            lower.contains("wallet") && lower.contains("sync") -> {
                "Your wallet is not fully synced. Please wait for syncing to complete before voting."
            }

            lower.contains("network") || lower.contains("timeout") || lower.contains("connect") -> {
                "Network error. Please check your connection and try again."
            }

            lower.contains("proof") || lower.contains("zkp") -> {
                "Vote proof generation failed. Please try again."
            }

            lower.contains("config") || lower.contains("version") -> {
                "Your app may need an update to participate in this voting round."
            }

            else -> {
                rawMessage.ifBlank { "An unexpected error occurred. Please try again." }
            }
        }
    }
}
