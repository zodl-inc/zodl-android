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

/**
 * Canonical, per-bundle delegation lifecycle (matches `zcash_voting::phases::DelegationPhase`),
 * derived on read from persisted artifacts rather than the coarse round-level [RoundPhase].
 *
 * Multi-bundle rounds legitimately have different bundles at different phases at the same time
 * (e.g. bundle 0 already `SUBMITTED` while bundle 1 is still `PREPARED`) — this is why per-bundle
 * decisions (should I construct/prove/submit this bundle?) must never be made from [RoundPhase].
 */
enum class DelegationPhase {
    PREPARED,
    PCZT_BUILT,
    PROVED,
    SUBMITTED,
    CONFIRMED;

    companion object {
        fun fromWireValue(value: String): DelegationPhase =
            when (value) {
                "prepared" -> PREPARED
                "pczt_built" -> PCZT_BUILT
                "proved" -> PROVED
                "submitted" -> SUBMITTED
                "confirmed" -> CONFIRMED
                else -> error("Unknown DelegationPhase wire value: $value")
            }
    }
}

data class BundleDelegationPhase(
    val bundleIndex: Int,
    val phase: DelegationPhase
)
