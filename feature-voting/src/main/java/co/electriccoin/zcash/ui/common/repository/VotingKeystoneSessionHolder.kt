package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.VotingRoundSession
import cash.z.ecc.android.sdk.model.voting.VotingBallotIntent
import cash.z.ecc.android.sdk.model.voting.VotingDelegationInputs
import cash.z.ecc.android.sdk.model.voting.VotingKeystoneSigningRequest
import cash.z.ecc.android.sdk.model.voting.VotingProposalRosterEntry
import cash.z.ecc.android.sdk.model.voting.VotingRoundDriveProgressListener
import cash.z.ecc.android.sdk.model.voting.VotingTorLease
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.acquireVotingTorLeaseOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps one round's [VotingRoundSession] (and the [VotingCryptoClient] DB handle it was opened
 * against) alive across the multi-screen Keystone signing flow (Confirm -> Sign -> Scan -> back
 * to Sign for the next bundle, or back to Confirm once every bundle is signed).
 * [VotingRoundSession.getKeystoneSigningRequests] fails without a cached delegation pipeline,
 * which only a prior delegation-enabled [VotingRoundSession.run] call on *this same session
 * instance* produces (`VotingSdk.kt`'s doc comment: "Fails if no delegation pipeline is cached
 * yet") -- so the session cannot be opened-and-closed per screen the way `SubmitVotesUseCase`
 * does for the non-Keystone path; it must outlive individual ViewModels.
 *
 * A Koin singleton (not scoped per-screen) so it survives navigation between
 * `SignKeystoneVotingVM`/`ScanKeystoneVotingPCZTViewModel` instances, which Koin recreates per
 * screen. If the process dies mid-flow, this holder is lost along with everything else in
 * memory; the resuming call simply reopens a fresh session and calls [ensureDelegationPipeline]
 * again -- safe because `openRoundSessionNative` bootstraps the same round idempotently and a
 * previously-signed bundle's signature is already durably persisted server-side via
 * `storeKeystoneSignatures`, so the Sign screen naturally re-offers only the bundles still
 * missing one (see [VotingKeystoneRepositoryImpl]'s doc comment for the idempotent-rebuild
 * pattern this mirrors from Vizor Wallet's shipping implementation on the same crate).
 */
class VotingKeystoneSessionHolder(
    private val votingCryptoClient: VotingCryptoClient,
    private val synchronizerProvider: SynchronizerProvider
) {
    private val mutex = Mutex()
    private var openRoundId: String? = null

    // Keyed on (account, round), not round alone: without this, switching Keystone accounts on
    // the SAME round -- start signing on account A, back out, switch to account B, open the same
    // round -- read openRoundId == roundId as "already open" and reused A's session for B.
    // getKeystoneSigningRequests then handed out A's PCZTs, and setBallotIntents/runToCompletion
    // wrote B's choices into A's round state.
    private var openAccountUuidString: String? = null
    private var dbHandle: Long? = null
    private var roundSession: VotingRoundSession? = null

    // The Tor lease the currently-open roundSession was opened with, released by closeLocked()
    // after that session is closed. null when Tor was disabled. Released on the lease itself, never
    // via synchronizerProvider -- after a synchronizer rebuild the current synchronizer is not the
    // one that issued it.
    private var torLease: VotingTorLease? = null

    /**
     * Ensures a delegation-enabled [VotingRoundSession.run] pass has completed for [roundId] on
     * a session this holder retains, caching the resulting delegation pipeline so
     * [getKeystoneSigningRequests] can be called afterward. A no-op if this holder already has
     * an open session for [roundId] -- callers should call this once per entry into the Keystone
     * signing flow (e.g. from [VotingKeystoneRepositoryImpl.createPcztEncoder]'s first call for a
     * round), not before every [getKeystoneSigningRequests] call.
     *
     * The Tor lease is acquired internally, before the no-op check, because the retained session
     * is only reusable if it was opened under the same Tor state (lease present or absent) as the
     * user's current preference: a session parked with Tor off must not finish the round without
     * Tor after the user turns it on, and vice versa. On reuse the fresh lease is released right
     * away; otherwise it is handed to the new session and released in [closeLocked].
     */
    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    suspend fun ensureDelegationPipeline(
        roundId: String,
        votingDbPath: String,
        accountUuidString: String,
        networkId: Int,
        proposals: List<VotingProposalRosterEntry>,
        hotkeySecret: ByteArray,
        chainEndpoints: List<String>,
        ceremonyStartSeconds: Long,
        voteEndTimeSeconds: Long,
        delegationInputs: VotingDelegationInputs
    ) = mutex.withLock {
        val lease = synchronizerProvider.getSynchronizer().acquireVotingTorLeaseOrNull()
        val reusable =
            openRoundId == roundId &&
                openAccountUuidString == accountUuidString &&
                roundSession != null &&
                (torLease == null) == (lease == null)
        if (reusable) {
            lease?.release()
            return@withLock
        }

        val handle: Long
        try {
            closeLocked()
            handle = votingCryptoClient.openVotingDb(votingDbPath)
            check(handle != 0L) { "Failed to open voting DB at $votingDbPath" }
        } catch (t: Throwable) {
            lease?.release()
            throw t
        }

        // setWalletId/openRoundSession/session.run below are all documented to surface a
        // RuntimeException from the native layer, and this whole sequence sits behind a
        // user-paced hardware-wallet signing flow -- plenty of wall-clock time for a transient
        // failure (e.g. a network blip inside run()'s delegation pass) to hit mid-sequence. The
        // class's dbHandle/roundSession/openRoundId fields are only assigned once every step has
        // fully succeeded (below), so a failure here would otherwise leak whatever was already
        // opened -- closeLocked() on a later call finds the fields still null and has nothing to
        // close. Catch broadly, tear down exactly what this attempt actually opened, and rethrow.
        var session: VotingRoundSession? = null
        try {
            votingCryptoClient.setWalletId(handle, accountUuidString, networkId)

            session =
                votingCryptoClient.openRoundSession(
                    dbHandle = handle,
                    torLease = lease,
                    roundId = roundId,
                    proposals = proposals,
                    hotkeySecret = hotkeySecret,
                    chainEndpoints = chainEndpoints,
                    operationEpoch = 0L,
                    configuredHelperUrls = chainEndpoints,
                    voteTreeNodeUrls = chainEndpoints,
                    ceremonyStartSeconds = ceremonyStartSeconds,
                    voteEndTimeSeconds = voteEndTimeSeconds
                )
            // A delegation-enabled run() call caches the DelegationPipeline this session's
            // getKeystoneSigningRequests needs -- see VotingRoundSession.run's doc comment. This
            // call may also fully advance/confirm the round's non-Keystone obligations if any
            // exist, which is fine: the caller's next getKeystoneSigningRequests call only asks
            // for the Keystone-specific bundle indices it still needs signed.
            session.run(delegationInputs = delegationInputs, progressListener = null)
        } catch (t: Throwable) {
            try {
                closeResources(session, handle, lease)
            } catch (closeError: Throwable) {
                t.addSuppressed(closeError)
            }
            throw t
        }

        dbHandle = handle
        roundSession = session
        openRoundId = roundId
        openAccountUuidString = accountUuidString
        torLease = lease
    }

    suspend fun getKeystoneSigningRequests(
        roundId: String,
        bundleIndices: List<Int>
    ): List<VotingKeystoneSigningRequest> =
        mutex.withLock {
            val session =
                checkNotNull(roundSession?.takeIf { openRoundId == roundId }) {
                    "No open Keystone signing session for round $roundId -- call ensureDelegationPipeline first"
                }
            session.getKeystoneSigningRequests(bundleIndices)
        }

    /**
     * Records ballot decisions on the retained session -- the only call site of
     * [VotingRoundSession.setBallotIntents] for the Keystone path, mirroring exactly what
     * `SubmitVotesUseCase`'s non-Keystone path calls directly on its own freshly-opened session.
     * Without this, the round has no cast draft for any proposal and [VotingRoundSession.run]
     * quiesces `NeedsBallot` instead of casting anything -- there is no other place in the
     * Keystone flow (Sign/Scan screens only sign bundles, they never set ballot intents) where
     * this can happen.
     */
    suspend fun setBallotIntents(
        roundId: String,
        intents: List<VotingBallotIntent>
    ) = mutex.withLock {
        val session =
            checkNotNull(roundSession?.takeIf { openRoundId == roundId }) {
                "No open Keystone signing session for round $roundId"
            }
        session.setBallotIntents(intents)
    }

    /** Continues driving [roundId] to quiescence after every bundle is signed. */
    suspend fun runToCompletion(
        roundId: String,
        delegationInputs: VotingDelegationInputs,
        progressListener: VotingRoundDriveProgressListener?
    ) = mutex.withLock {
        val session =
            checkNotNull(roundSession?.takeIf { openRoundId == roundId }) {
                "No open Keystone signing session for round $roundId"
            }
        session.run(delegationInputs = delegationInputs, progressListener = progressListener)
    }

    suspend fun close(roundId: String) =
        mutex.withLock {
            if (openRoundId == roundId) {
                closeLocked()
            }
        }

    // Clears every field BEFORE tearing anything down, so a failure partway through can never
    // leave the holder pointing at an already-closed session -- ensureDelegationPipeline's no-op
    // check would otherwise keep reusing that dead session until process restart.
    private suspend fun closeLocked() {
        val session = roundSession
        val handle = dbHandle
        val lease = torLease
        roundSession = null
        dbHandle = null
        openRoundId = null
        openAccountUuidString = null
        torLease = null
        closeResources(session, handle, lease)
    }

    // NonCancellable: withContext(Dispatchers.IO) inside roundSession.close()/closeVotingDb()
    // throws immediately instead of running if the caller's Job is already cancelled (e.g. the
    // user backed out of the Sign screen mid delegation pass), which would otherwise leak the
    // native handles. Each step runs in its own finally so one failing close cannot skip the rest;
    // the lease goes last, after the session that uses its runtime is closed.
    private suspend fun closeResources(
        session: VotingRoundSession?,
        handle: Long?,
        lease: VotingTorLease?
    ) = withContext(NonCancellable) {
        try {
            session?.close()
        } finally {
            try {
                handle?.let { votingCryptoClient.closeVotingDb(it) }
            } finally {
                lease?.release()
            }
        }
    }
}
