package co.electriccoin.zcash.ui.screen.voting

enum class VoteTrustIndicator {
    ZODL,
    UNVERIFIED
}

fun voteTrustIndicatorFor(
    roundId: String,
    endorsedRoundIds: Set<String>,
    isOnDefaultConfig: Boolean
): VoteTrustIndicator? {
    if (!isOnDefaultConfig) {
        return VoteTrustIndicator.UNVERIFIED
    }
    return if (roundId.lowercase() in endorsedRoundIds) {
        VoteTrustIndicator.ZODL
    } else {
        null
    }
}

fun Set<String>.normalizedVotingRoundIds(): Set<String> = mapTo(mutableSetOf()) { it.lowercase() }
