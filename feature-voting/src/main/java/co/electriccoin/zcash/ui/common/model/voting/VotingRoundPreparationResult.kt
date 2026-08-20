package co.electriccoin.zcash.ui.common.model.voting

sealed interface VotingRoundPreparationResult {
    data class Ready(
        val roundId: String,
        val bundleCount: Int,
        val eligibleWeight: Long,
        val hotkeyAddress: String
    ) : VotingRoundPreparationResult

    data class WalletSyncing(
        val scannedHeight: Long?,
        val snapshotHeight: Long
    ) : VotingRoundPreparationResult

    data class Ineligible(
        val reason: VoteIneligibilityReason,
        val eligibleWeight: Long,
        val bundleCount: Int
    ) : VotingRoundPreparationResult
}
