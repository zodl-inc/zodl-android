package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.Serializable

/**
 * Granular reason for marking a wallet ineligible to vote in a round.
 *
 * Mirrors iOS `VotingStore+Session.swift` (case `.votingWeightLoaded`): the no-notes
 * branch fires before any bundling; the balance-too-low branch fires only after smart
 * bundling drops dust bundles. Both branches collapse to `eligibleWeight == 0` on the
 * Rust side, so the distinction must be derived in Kotlin from the un-bundled notes
 * count.
 */
@Serializable
enum class VoteIneligibilityReason {
    /** Wallet held no notes at the snapshot height — nothing to bundle. */
    NO_NOTES,

    /** Wallet held notes but smart bundling dropped them all under the ballot divisor. */
    BALANCE_TOO_LOW,
}
