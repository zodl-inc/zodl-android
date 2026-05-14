package co.electriccoin.zcash.ui.common.model

/**
 * Common wallet restoring state enum. This describes whether the current block synchronization run is in the
 * restoring state or a subsequent synchronization state.
 *
 * WARN: Do NOT reorder or change the values; doing so would update their ordinal numbers, which could break the
 * wallet UI.
 */
enum class WalletRestoringState {
    NONE,
    INITIATING, // New wallet syncing
    RESTORING, // Existing wallet syncing
    RESYNCING, // Existing wallet resyncing
    SYNCING; // Follow-up syncing

    fun toNumber() = ordinal

    companion object {
        fun fromNumber(ordinal: Int) = entries[ordinal]
    }
}
