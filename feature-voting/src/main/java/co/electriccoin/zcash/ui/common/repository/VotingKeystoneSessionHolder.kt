package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.VotingRoundSession
import cash.z.ecc.android.sdk.exception.TorUnavailableException
import cash.z.ecc.android.sdk.model.voting.VotingBallotIntent
import cash.z.ecc.android.sdk.model.voting.VotingDelegationInputs
import cash.z.ecc.android.sdk.model.voting.VotingKeystoneSigningRequest
import cash.z.ecc.android.sdk.model.voting.VotingProposalRosterEntry
import cash.z.ecc.android.sdk.model.voting.VotingRoundDriveProgressListener
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
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
    private var dbHandle: Long? = null
    private var roundSession: VotingRoundSession? = null

    // True exactly when the currently-open roundSession was opened with a pinned Tor runtime
    // handle this holder is responsible for releasing (see closeLocked()) -- false when Tor was
    // disabled (torRuntime = 0L) and nothing was pinned.
    private var torRuntimePinned = false

    /**
     * Ensures a delegation-enabled [VotingRoundSession.run] pass has completed for [roundId] on
     * a session this holder retains, caching the resulting delegation pipeline so
     * [getKeystoneSigningRequests] can be called afterward. A no-op if this holder already has
     * an open session for [roundId] -- callers should call this once per entry into the Keystone
     * signing flow (e.g. from [VotingKeystoneRepositoryImpl.createPcztEncoder]'s first call for a
     * round), not before every [getKeystoneSigningRequests] call.
     *
     * The Tor runtime handle is resolved internally, only on the path that actually opens a new
     * session (below the no-op check) -- resolving it in the caller and passing it in would pin
     * a handle on every call, including calls this function no-ops on, and that pin would then
     * never be released (this holder only releases what it itself pinned, in [closeLocked]).
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
        if (openRoundId == roundId && roundSession != null) {
            return@withLock
        }
        closeLocked()

        val handle = votingCryptoClient.openVotingDb(votingDbPath)
        check(handle != 0L) { "Failed to open voting DB at $votingDbPath" }

        // Same Tor policy as SubmitVotesUseCase's non-Keystone path: 0L (Tor disabled) is the
        // only fallback; a real init failure must propagate. Pinned here, released in
        // closeLocked() -- see torRuntimePinned's own comment.
        @Suppress("SwallowedException")
        val torRuntime =
            try {
                synchronizerProvider.getSynchronizer().getVotingTorRuntimeHandle()
            } catch (e: TorUnavailableException) {
                0L
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
                    torRuntime = torRuntime,
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
            // NonCancellable: see closeLocked()'s own comment -- the same leak risk applies to
            // tearing down a partially-opened attempt after a cancellation.
            withContext(NonCancellable) {
                session?.close()
                votingCryptoClient.closeVotingDb(handle)
                if (torRuntime != 0L) synchronizerProvider.getSynchronizer().releaseVotingTorRuntimeHandle()
            }
            throw t
        }

        dbHandle = handle
        roundSession = session
        openRoundId = roundId
        torRuntimePinned = torRuntime != 0L
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

    // NonCancellable: withContext(Dispatchers.IO) inside roundSession.close()/closeVotingDb()/
    // releaseVotingTorRuntimeHandle() throws immediately instead of running if the caller's Job
    // is already cancelled (e.g. the user backed out of the Sign screen mid delegation pass),
    // which would otherwise leak all three native handles instead of closing them.
    private suspend fun closeLocked() =
        withContext(NonCancellable) {
            roundSession?.close()
            dbHandle?.let { votingCryptoClient.closeVotingDb(it) }
            if (torRuntimePinned) synchronizerProvider.getSynchronizer().releaseVotingTorRuntimeHandle()
            roundSession = null
            dbHandle = null
            openRoundId = null
            torRuntimePinned = false
        }
}
