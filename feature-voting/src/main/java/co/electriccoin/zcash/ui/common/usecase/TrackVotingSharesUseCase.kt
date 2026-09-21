package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.VotingShareTrackingSession
import cash.z.ecc.android.sdk.exception.TorUnavailableException
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.voting.VotingShareTrackingQuiescence
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface VotingShareTrackingResult {
    data object Completed : VotingShareTrackingResult

    data class Pending(
        val delayMillis: Long
    ) : VotingShareTrackingResult
}

/**
 * voting-5.0.0 production-completion note: `trackShares` is now a cancellable session
 * ([VotingCryptoClient.openShareTrackingSession]), replacing the standalone JNI export that had
 * no reachable cancel path and caused indefinite hangs under WorkManager (see Phase 1 of
 * `2026-09-21-round-driver-production-completion-design.md`). [cancel] lets
 * [co.electriccoin.zcash.work.VotingShareTrackingWorker] interrupt an in-flight [invoke] call for
 * the same [roundId] within seconds rather than leaving an orphaned native thread running for the
 * full WorkManager execution-time-limit window. (The worker reaches this from a coroutine that
 * polls `isStopped`, not from an overridden `onStopped()` -- `CoroutineWorker.onStopped()` is
 * `final` on the androidx.work version this app resolves; see the worker's KDoc for detail.)
 *
 * `activeSessions` is a companion-scoped map (not an instance field) for the same reason
 * `attemptCounts` below is: Koin provides this use case via `factoryOf`, so a fresh instance
 * backs every retried `VotingShareTrackingWorker` run, but `cancel()` must be able to reach a
 * session opened by a *different* instance's still-in-flight [invoke] call.
 */
class TrackVotingSharesUseCase(
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingCryptoClient: VotingCryptoClient,
    private val votingApiProvider: VotingApiProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) {
    suspend operator fun invoke(roundId: String): VotingShareTrackingResult =
        withContext(Dispatchers.IO) {
            val selectedAccount = getSelectedWalletAccount()
            val accountUuidString = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
            val recovery =
                votingRecoveryRepository.get(accountUuidString, roundId)
                    ?: return@withContext VotingShareTrackingResult.Completed
            val roundVoteServerUrls =
                recovery.voteServerUrls
                    .ifEmpty {
                        runCatching {
                            votingApiProvider
                                .fetchServiceConfig()
                                .voteServers
                                .map { endpoint -> endpoint.url.trimEnd('/') }
                                .distinct()
                        }.getOrDefault(emptyList())
                    }
            if (roundVoteServerUrls.isEmpty()) {
                return@withContext VotingShareTrackingResult.Pending(nextDelayMillis(roundId))
            }

            val synchronizer = synchronizerProvider.getSynchronizer()
            val walletDbPath = synchronizerProvider.getVotingWalletDbPath()
            val networkId = synchronizer.network.toVotingNetworkId()
            val votingDbPath =
                File(walletDbPath)
                    .parentFile
                    ?.resolve("voting.sqlite3")
                    ?.absolutePath
                    ?: error("Unable to derive voting DB path from $walletDbPath")

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            try {
                votingCryptoClient.setWalletId(dbHandle, accountUuidString, networkId)
                // Same Tor-optional fallback as SubmitVotesUseCase.kt -- Tor is a
                // preference, not a hard requirement. Only TorUnavailableException
                // (Tor disabled) falls back to 0L; TorInitializationErrorException
                // (Tor is ON but failed to bootstrap) must propagate.
                val torRuntime =
                    try {
                        synchronizer.getVotingTorRuntimeHandle()
                    } catch (e: TorUnavailableException) {
                        0L
                    }

                val session = votingCryptoClient.openShareTrackingSession(dbHandle, roundId)
                registerSession(roundId, session)
                val report =
                    try {
                        session.run(
                            torRuntime = torRuntime,
                            helperUrls = roundVoteServerUrls,
                            voteEndTimeSeconds = recovery.voteEndEpochSeconds ?: -1L
                        )
                    } finally {
                        unregisterSession(roundId, session)
                        withContext(NonCancellable) { session.close() }
                    }

                val completed =
                    report != null &&
                        report.unrecoverable.isEmpty() &&
                        report.ambiguous.isEmpty() &&
                        (
                            report.quiescence is VotingShareTrackingQuiescence.AllConfirmed ||
                                report.quiescence is VotingShareTrackingQuiescence.NothingToTrack
                        )
                if (completed) {
                    resetDelay(roundId)
                    votingRecoveryRepository.setPhase(
                        accountUuidString,
                        roundId,
                        VotingRecoveryPhase.SHARES_SUBMITTED
                    )
                    VotingShareTrackingResult.Completed
                } else {
                    VotingShareTrackingResult.Pending(nextDelayMillis(roundId))
                }
            } finally {
                withContext(NonCancellable) {
                    votingCryptoClient.closeVotingDb(dbHandle)
                }
            }
        }

    /**
     * Cancels [roundId]'s in-flight [invoke] call, if one opened a session on this or another
     * `TrackVotingSharesUseCase` instance. A no-op if none is active -- this must be safe to call
     * unconditionally from [co.electriccoin.zcash.work.VotingShareTrackingWorker]'s stop-signal
     * handling, which cannot know whether `invoke` had reached the session-open point yet when
     * the stop signal arrived.
     *
     * Known, accepted race: this is keyed purely by [roundId] (the only thing a caller like the
     * worker's stop-signal handling has), not by a specific session reference, so it cannot
     * distinguish "the session I meant to cancel" from "whatever session currently owns this
     * roundId". Under `VotingShareTrackingScheduler`'s `ExistingWorkPolicy.REPLACE`, at most one
     * worker run is normally in flight per round, but if a stale run's [cancel] call is suspended
     * on [activeSessionsMutex] at the exact moment a *new* run for the same [roundId] calls
     * [registerSession] first, the stale call could cancel the new session instead of a
     * stale/absent one. The window is narrow (a single mutex-acquisition race) and not closed
     * here -- fixing it would need session-identity-aware cancellation (e.g. cancel tokens), which
     * is more machinery than this task's scope warrants.
     */
    suspend fun cancel(roundId: String) {
        activeSessionsMutex.withLock { activeSessions[roundId] }?.cancel()
    }

    private suspend fun registerSession(
        roundId: String,
        session: VotingShareTrackingSession
    ) = activeSessionsMutex.withLock { activeSessions[roundId] = session }

    private suspend fun unregisterSession(
        roundId: String,
        session: VotingShareTrackingSession
    ) = activeSessionsMutex.withLock {
        if (activeSessions[roundId] === session) {
            activeSessions.remove(roundId)
        }
    }

    private companion object {
        const val DEFAULT_DELAY_MILLIS = 15_000L
        const val MAX_DELAY_MILLIS = 120_000L

        private val activeSessionsMutex = Mutex()
        private val activeSessions = mutableMapOf<String, VotingShareTrackingSession>()

        // Per-round consecutive-incomplete-attempt counter. Lives on the companion (i.e. shared
        // by every `TrackVotingSharesUseCase` instance), not as an instance field, because Koin
        // provides this use case via `factoryOf` (see FeatureVotingModule) rather than as a
        // singleton -- a fresh instance is injected into each new `VotingShareTrackingWorker`
        // WorkManager creates for a retried run, so per-instance state would not survive across
        // those retries. `VotingShareTrackingScheduler.schedule` enqueues with
        // `ExistingWorkPolicy.REPLACE` on a per-round unique work name, so at most one call for a
        // given `roundId` is ever in flight at a time; a plain synchronized map is therefore
        // enough -- no jitter or cross-process persistence needed for this cadence gap.
        private val attemptCounts = mutableMapOf<String, Int>()

        @Synchronized
        private fun nextDelayMillis(roundId: String): Long {
            val attempt = (attemptCounts[roundId] ?: 0) + 1
            attemptCounts[roundId] = attempt
            val backedOff = DEFAULT_DELAY_MILLIS * (1L shl (attempt - 1).coerceAtMost(32))
            return backedOff.coerceIn(DEFAULT_DELAY_MILLIS, MAX_DELAY_MILLIS)
        }

        @Synchronized
        private fun resetDelay(roundId: String) {
            attemptCounts.remove(roundId)
        }
    }
}

private fun ZcashNetwork.toVotingNetworkId() = if (isMainnet()) 1 else 0
