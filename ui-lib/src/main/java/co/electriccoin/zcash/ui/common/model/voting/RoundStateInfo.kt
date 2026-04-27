package co.electriccoin.zcash.ui.common.model.voting

enum class RoundPhase {
    INITIALIZED,
    HOTKEY,
    DELEGATION,
    PROVED,
    VOTE_READY
}

data class RoundStateInfo(
    val roundId: String,
    val phase: RoundPhase,
    val snapshotHeight: Long,
    val hotkeyAddress: String?,
    val delegatedWeight: Long?,
    val proofGenerated: Boolean
)
