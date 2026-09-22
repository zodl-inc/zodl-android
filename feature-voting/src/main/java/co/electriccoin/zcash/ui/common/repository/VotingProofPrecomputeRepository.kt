package co.electriccoin.zcash.ui.common.repository

import android.util.Log
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val notesJson: String,
    val torRuntime: Long
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
    val notesJson: String,
    val torRuntime: Long
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

    /**
     * Cancels and awaits termination of any in-flight background precompute job that could
     * contend for the shared native voting-DB lock (see [VotingCryptoClient.precomputePirProofs]/
     * [VotingCryptoClient.precomputeSnapshotBundles]'s own doc comments) with a caller about to
     * open its own session for real vote submission -- callers (e.g. `SubmitVotesUseCase`) must
     * call this before opening their own DB session for [roundId], so the two never contend for
     * the lock simultaneously.
     *
     * Cancels [startSnapshotBundlePrecompute]'s job for ([accountUuid], [roundId]) specifically --
     * round-scoped, and the primary lock-holder of concern since it runs for the whole PIR
     * round-trip duration of every bundle in the round. Also cancels every still-in-flight
     * [startPirWarmup] job for [accountUuid] -- round-independent (keyed on snapshot height, not
     * [roundId]), so every one of the account's own in-flight warm-ups is cancelled rather than
     * trying to resolve which snapshot height the round being submitted maps to; a lesser but real
     * contention risk on the same native lock.
     *
     * A job that has already finished (success, failure, or already cancelled) awaits instantly --
     * this is a no-op in the common case where nothing is running.
     */
    suspend fun cancelAndAwaitPrecompute(
        accountUuid: String,
        roundId: String
    )
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

            val job = scope.launch { runPirWarmup(request) }
            pirWarmupJobs[request.key] = job
            // Important #4 (final whole-plan review): a job that completes -- successfully OR
            // with a failure -- must not permanently poison its own dedup key. Removing it here
            // on ANY completion (not just cancellation) is what lets the next screen entry retry
            // instead of silently staying stuck until process restart. Guarded by identity so a
            // newer job already registered under the same key (e.g. a retry that started while
            // this one was still winding down) is never accidentally evicted.
            job.invokeOnCompletion {
                synchronized(lock) {
                    if (pirWarmupJobs[request.key] === job) {
                        pirWarmupJobs.remove(request.key)
                    }
                }
            }
        }
    }

    override fun startSnapshotBundlePrecompute(request: VotingSnapshotBundlePrecomputeRequest) {
        synchronized(lock) {
            val existing = snapshotBundlePrecomputeJobs[request.key]
            if (existing != null && !existing.isCancelled) {
                return
            }

            val job = scope.launch { runSnapshotBundlePrecompute(request) }
            snapshotBundlePrecomputeJobs[request.key] = job
            // See startPirWarmup's identical comment above (Important #4) -- same reasoning.
            job.invokeOnCompletion {
                synchronized(lock) {
                    if (snapshotBundlePrecomputeJobs[request.key] === job) {
                        snapshotBundlePrecomputeJobs.remove(request.key)
                    }
                }
            }
        }
    }

    override suspend fun cancelAndAwaitPrecompute(
        accountUuid: String,
        roundId: String
    ) {
        val jobsToAwait = mutableListOf<Job>()
        synchronized(lock) {
            snapshotBundlePrecomputeJobs[VotingSnapshotBundlePrecomputeKey(accountUuid, roundId)]?.let { job ->
                job.cancel()
                jobsToAwait += job
            }
            pirWarmupJobs
                .filterKeys { key -> key.accountUuid == accountUuid }
                .values
                .forEach { job ->
                    job.cancel()
                    jobsToAwait += job
                }
        }
        // join() outside the lock -- awaiting termination must not hold `lock` while the
        // cancelled job's own completion handler (registered above) tries to acquire it to
        // remove itself from the dedup map, which would deadlock.
        jobsToAwait.forEach { job -> job.join() }
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
                    torRuntime = request.torRuntime,
                    pirServerUrl = pirServerUrl,
                    pirLayout = request.pirLayout,
                    notesJson = request.notesJson
                )
            } finally {
                // Important #1 (final whole-plan review): cancelAndAwaitPrecompute cancels this
                // job while it may be blocked inside the native, non-cancellable precompute call
                // -- cancellation only actually takes effect once that call returns. By then this
                // coroutine's Job is already Cancelling/Cancelled, so a plain suspend call here
                // (closeVotingDb itself uses withContext(Dispatchers.IO)) would throw
                // CancellationException immediately instead of running, leaking the dbHandle and
                // the shared native lock it holds -- exactly the outcome this whole fix exists to
                // prevent. NonCancellable guarantees this cleanup actually runs, same pattern
                // SubmitVotesUseCase.kt already uses for its own roundSession.close()/
                // closeVotingDb() cleanup.
                withContext(NonCancellable) {
                    votingCryptoClient.closeVotingDb(dbHandle)
                }
            }
        }.onSuccess { result ->
            // Minor (final whole-plan review): this result used to be built and dropped
            // entirely -- the pending live on-device test has no observable signal today that
            // precompute actually ran or what it did. Log-only, never surfaced to the UI.
            Log.d(
                TAG,
                "PIR proof warmup completed for account ${request.accountUuid}: " +
                    "cachedCount=${result.cachedCount} fetchedCount=${result.fetchedCount}"
            )
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
                    torRuntime = request.torRuntime,
                    roundId = request.roundId,
                    pirServerUrl = pirServerUrl,
                    pirLayout = request.pirLayout,
                    notesJson = request.notesJson
                )
            } finally {
                // See runPirWarmup's identical comment above (Important #1) -- same reasoning.
                withContext(NonCancellable) {
                    votingCryptoClient.closeVotingDb(dbHandle)
                }
            }
        }.onSuccess { result ->
            // Minor (final whole-plan review): same observability gap as runPirWarmup above.
            Log.d(
                TAG,
                "Snapshot bundle precompute completed for round ${request.roundId}: " +
                    "bundleCount=${result.bundleCount} eligibleWeight=${result.eligibleWeight}"
            )
        }.onFailure { throwable ->
            Log.w(TAG, "Snapshot bundle precompute failed for round ${request.roundId}", throwable)
        }
    }

    private companion object {
        const val TAG = "VotingProofPrecompute"
    }
}
