package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.TorUnavailableException
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

sealed interface VotingShareTrackingResult {
    data object Completed : VotingShareTrackingResult

    data class Pending(
        val delayMillis: Long
    ) : VotingShareTrackingResult
}

/**
 * voting-4.0.0 round-driver port note (Task 6): the old hand-rolled per-share polling/resubmit
 * loop is replaced with a single [VotingCryptoClient.trackShares] call, which drives every
 * unconfirmed share for the round to quiescence internally via the crate's own
 * `ShareTrackingDriver` before returning.
 *
 * An incomplete result (missing server URLs, or a non-empty `unrecoverable`/`ambiguous` in the
 * report) returns [VotingShareTrackingResult.Pending] with an exponentially backed-off delay --
 * see [nextDelayMillis] -- rather than the fixed [DEFAULT_DELAY_MILLIS] every time; the delay
 * resets once a call for that round fully [VotingShareTrackingResult.Completed]s.
 *
 * Out of scope: full mid-run cancellation of an in-flight `trackSharesNative` call. This is only
 * a retry-cadence improvement, not a cancellation mechanism -- `trackSharesNative` is a
 * session-less, standalone JNI export (no `RoundSessionHandle` a separate JNI call could reach to
 * cancel it, unlike `runRoundNative`), a known gap documented in full in
 * `share_tracking_driver.rs`'s module doc comment; it remains a known gap for a future task.
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
                val report =
                    votingCryptoClient.trackShares(
                        dbHandle = dbHandle,
                        roundId = roundId,
                        torRuntime = torRuntime,
                        helperUrls = roundVoteServerUrls,
                        voteEndTimeSeconds = recovery.voteEndEpochSeconds ?: -1L
                    )
                if (report.unrecoverable.isNotEmpty() || report.ambiguous.isNotEmpty()) {
                    VotingShareTrackingResult.Pending(nextDelayMillis(roundId))
                } else {
                    resetDelay(roundId)
                    VotingShareTrackingResult.Completed
                }
            } finally {
                withContext(NonCancellable) {
                    votingCryptoClient.closeVotingDb(dbHandle)
                }
            }
        }

    private companion object {
        const val DEFAULT_DELAY_MILLIS = 15_000L
        const val MAX_DELAY_MILLIS = 120_000L

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
