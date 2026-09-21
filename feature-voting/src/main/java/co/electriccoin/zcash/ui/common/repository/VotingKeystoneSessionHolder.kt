package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.VotingRoundSession
import cash.z.ecc.android.sdk.model.voting.VotingDelegationInputs
import cash.z.ecc.android.sdk.model.voting.VotingKeystoneSigningRequest
import cash.z.ecc.android.sdk.model.voting.VotingProposalRosterEntry
import cash.z.ecc.android.sdk.model.voting.VotingRoundDriveProgressListener
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val votingCryptoClient: VotingCryptoClient
) {
    private val mutex = Mutex()
    private var openRoundId: String? = null
    private var dbHandle: Long? = null
    private var roundSession: VotingRoundSession? = null

    /**
     * Ensures a delegation-enabled [VotingRoundSession.run] pass has completed for [roundId] on
     * a session this holder retains, caching the resulting delegation pipeline so
     * [getKeystoneSigningRequests] can be called afterward. A no-op if this holder already has
     * an open session for [roundId] -- callers should call this once per entry into the Keystone
     * signing flow (e.g. from [VotingKeystoneRepositoryImpl.createPcztEncoder]'s first call for a
     * round), not before every [getKeystoneSigningRequests] call.
     */
    @Suppress("LongParameterList")
    suspend fun ensureDelegationPipeline(
        roundId: String,
        votingDbPath: String,
        accountUuidString: String,
        networkId: Int,
        torRuntime: Long,
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
        votingCryptoClient.setWalletId(handle, accountUuidString, networkId)

        val session =
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
        // call may also fully advance/confirm the round's non-Keystone obligations if any exist,
        // which is fine: the caller's next getKeystoneSigningRequests call only asks for the
        // Keystone-specific bundle indices it still needs signed.
        session.run(delegationInputs = delegationInputs, progressListener = null)

        dbHandle = handle
        roundSession = session
        openRoundId = roundId
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

    private suspend fun closeLocked() {
        roundSession?.close()
        dbHandle?.let { votingCryptoClient.closeVotingDb(it) }
        roundSession = null
        dbHandle = null
        openRoundId = null
    }
}
