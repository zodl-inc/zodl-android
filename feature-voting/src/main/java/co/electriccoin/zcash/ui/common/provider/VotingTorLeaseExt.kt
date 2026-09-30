package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.TorUnavailableException
import cash.z.ecc.android.sdk.model.voting.VotingTorLease

/**
 * Leases the synchronizer's Tor runtime for a voting call or session, or returns `null` when the
 * user has Tor disabled -- plain HTTP is the explicit fallback only in that case.
 * `TorInitializationErrorException` (Tor is on but failed to bootstrap) propagates: silently
 * downgrading a Tor user's vote traffic to plain HTTP would deanonymize them.
 *
 * The caller owns the returned lease and must release it in a `finally` once every session it was
 * passed to has been closed. Release goes to the Tor client that issued it, so it stays correct
 * across a synchronizer rebuild -- never release via whichever synchronizer is current at that point.
 */
@Suppress("SwallowedException")
suspend fun Synchronizer.acquireVotingTorLeaseOrNull(): VotingTorLease? =
    try {
        acquireVotingTorLease()
    } catch (e: TorUnavailableException) {
        null
    }
