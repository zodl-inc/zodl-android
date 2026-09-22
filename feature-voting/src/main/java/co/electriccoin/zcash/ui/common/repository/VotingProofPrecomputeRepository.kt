package co.electriccoin.zcash.ui.common.repository

import android.util.Log
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Dedup key for [VotingProofPrecomputeRepository.startPirWarmup]. [precomputePirProofs] is
 * bundle- and round-independent (see its SDK doc comment), so the cache warm-up it drives is
 * keyed on the wallet notes actually used -- [accountUuid] plus the [snapshotHeight] those notes
 * (and the PIR server resolved against it) were fetched at -- rather than a round id.
 */
data class VotingPirWarmupKey(
    val accountUuid: String,
    val snapshotHeight: Long
)

data class VotingPirWarmupRequest(
    val accountUuid: String,
    val walletId: String,
    val votingDbPath: String,
    val snapshotHeight: Long,
    val pirEndpoints: List<String>,
    val pirLayout: VotingPirLayout,
    val networkId: Int,
    val notesJson: String
) {
    val key: VotingPirWarmupKey
        get() = VotingPirWarmupKey(accountUuid = accountUuid, snapshotHeight = snapshotHeight)
}

/**
 * Dedup key for [VotingProofPrecomputeRepository.startSnapshotBundlePrecompute].
 * [precomputeSnapshotBundles] persists a round's canonical bundle plan, so unlike PIR warmup this
 * is genuinely round-scoped.
 */
data class VotingSnapshotBundlePrecomputeKey(
    val accountUuid: String,
    val roundId: String
)

data class VotingSnapshotBundlePrecomputeRequest(
    val accountUuid: String,
    val walletId: String,
    val votingDbPath: String,
    val roundId: String,
    val pirEndpoints: List<String>,
    val pirLayout: VotingPirLayout,
    val expectedSnapshotHeight: Long,
    val networkId: Int,
    val notesJson: String
) {
    val key: VotingSnapshotBundlePrecomputeKey
        get() = VotingSnapshotBundlePrecomputeKey(accountUuid = accountUuid, roundId = roundId)
}

interface VotingProofPrecomputeRepository {
    fun warmProvingCaches()

    /**
     * Fire-and-forget, deduped background warm-up of the bundle- and round-independent PIR proof
     * cache (Task 1's `precomputePirProofs`) -- safe to trigger before any round is selected, e.g.
     * from the poll list or proposal detail screen's entry hook. Never blocks or fails the
     * triggering screen: failures are logged and swallowed.
     */
    fun startPirWarmup(request: VotingPirWarmupRequest)

    /**
     * Fire-and-forget, deduped background precompute of a round's snapshot-stable bundle plan
     * plus PIR warm-up for every bundle in it (Task 2's `precomputeSnapshotBundles`) -- a verified
     * strict superset of the old per-bundle delegation-PIR precompute. Requires a resolved
     * [VotingSnapshotBundlePrecomputeRequest.roundId], so this can only fire once a specific round
     * is selected (proposal detail / review), unlike [startPirWarmup]. Never blocks or fails the
     * triggering screen: failures are logged and swallowed.
     */
    fun startSnapshotBundlePrecompute(request: VotingSnapshotBundlePrecomputeRequest)
}

class VotingProofPrecomputeRepositoryImpl(
    private val votingCryptoClient: VotingCryptoClient,
    private val pirSnapshotResolver: PirSnapshotResolver,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : VotingProofPrecomputeRepository {
    private val lock = Any()
    private val pirWarmupJobs = mutableMapOf<VotingPirWarmupKey, Job>()
    private val snapshotBundlePrecomputeJobs = mutableMapOf<VotingSnapshotBundlePrecomputeKey, Job>()

    override fun warmProvingCaches() {
        // voting-5.0.0 background-precompute port, Task 3: the crate's own
        // start_proving_cache_warmup() now dedupes and backgrounds this for free, so the
        // AtomicBoolean gate this method used to need is redundant -- repeat calls (e.g. every
        // screen re-entry) are cheap at the crate level. The old onFailure { warmupStarted.set
        // (false) } retry-reset was already dead code before this simplification too:
        // warm_proving_caches() has never returned a Result to propagate a failure through, so
        // that branch could never actually run. Every call is now forwarded directly.
        scope.launch {
            runCatching { votingCryptoClient.warmProvingCaches() }
                .onFailure { throwable -> Log.w(TAG, "warmProvingCaches failed", throwable) }
        }
    }

    override fun startPirWarmup(request: VotingPirWarmupRequest) {
        synchronized(lock) {
            val existing = pirWarmupJobs[request.key]
            if (existing != null && !existing.isCancelled) {
                return
            }

            pirWarmupJobs[request.key] = scope.launch { runPirWarmup(request) }
        }
    }

    override fun startSnapshotBundlePrecompute(request: VotingSnapshotBundlePrecomputeRequest) {
        synchronized(lock) {
            val existing = snapshotBundlePrecomputeJobs[request.key]
            if (existing != null && !existing.isCancelled) {
                return
            }

            snapshotBundlePrecomputeJobs[request.key] = scope.launch { runSnapshotBundlePrecompute(request) }
        }
    }

    private suspend fun runPirWarmup(request: VotingPirWarmupRequest) {
        runCatching {
            val pirServerUrl =
                pirSnapshotResolver.resolve(
                    endpoints = request.pirEndpoints,
                    expectedSnapshotHeight = request.snapshotHeight
                )
            val dbHandle = votingCryptoClient.openVotingDb(request.votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at ${request.votingDbPath}" }

            try {
                votingCryptoClient.setWalletId(dbHandle, request.walletId, request.networkId)
                votingCryptoClient.precomputePirProofs(
                    dbHandle = dbHandle,
                    pirServerUrl = pirServerUrl,
                    pirLayout = request.pirLayout,
                    notesJson = request.notesJson
                )
            } finally {
                votingCryptoClient.closeVotingDb(dbHandle)
            }
        }.onFailure { throwable ->
            // Fire-and-forget background optimization: a failure here means the app pays PIR
            // latency synchronously later instead of finding warm proofs -- never something the
            // triggering screen should see or retry on its own.
            Log.w(TAG, "PIR proof warmup failed for account ${request.accountUuid}", throwable)
        }
    }

    private suspend fun runSnapshotBundlePrecompute(request: VotingSnapshotBundlePrecomputeRequest) {
        runCatching {
            val pirServerUrl =
                pirSnapshotResolver.resolve(
                    endpoints = request.pirEndpoints,
                    expectedSnapshotHeight = request.expectedSnapshotHeight
                )
            val dbHandle = votingCryptoClient.openVotingDb(request.votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at ${request.votingDbPath}" }

            try {
                votingCryptoClient.setWalletId(dbHandle, request.walletId, request.networkId)
                votingCryptoClient.precomputeSnapshotBundles(
                    dbHandle = dbHandle,
                    roundId = request.roundId,
                    pirServerUrl = pirServerUrl,
                    pirLayout = request.pirLayout,
                    notesJson = request.notesJson
                )
            } finally {
                votingCryptoClient.closeVotingDb(dbHandle)
            }
        }.onFailure { throwable ->
            Log.w(TAG, "Snapshot bundle precompute failed for round ${request.roundId}", throwable)
        }
    }

    private companion object {
        const val TAG = "VotingProofPrecompute"
    }
}
