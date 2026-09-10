package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.DelegationPhase
import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationPirPrecomputeResult
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

data class VotingDelegationPirPrecomputeKey(
    val accountUuid: String,
    val roundId: String,
    val bundleIndex: Int
)

/**
 * Everything the background ZKP1 stage needs beyond the request itself. All of it is readable at
 * round-preparation time without authenticating the user, which is what lets the delegation proof
 * run while the user is still answering questions.
 *
 * Not a data class on purpose: it holds key material, so identity - not structural equality - is
 * what the repository tracks, and [clear] zeroes it as soon as the proof no longer needs it. Build
 * it from copies: it outlives the call that assembled it.
 */
class VotingDelegationProofMaterial(
    val fvkBytes: ByteArray,
    val hotkeySeed: ByteArray,
    val seedFingerprint: ByteArray,
    val accountIndex: Int,
    val roundName: String
) {
    fun clear() {
        fvkBytes.fill(0)
        hotkeySeed.fill(0)
        seedFingerprint.fill(0)
    }
}

data class VotingDelegationPirPrecomputeRequest(
    val accountUuid: String,
    val walletId: String,
    val votingDbPath: String,
    val roundId: String,
    val bundleIndex: Int,
    val pirEndpoints: List<String>,
    val pirLayout: VotingPirLayout,
    val expectedSnapshotHeight: Long,
    val networkId: Int,
    val notesJson: String,
    /** Null for Keystone, whose delegation proof cannot be produced without the device. */
    val proofMaterial: VotingDelegationProofMaterial? = null
) {
    val key: VotingDelegationPirPrecomputeKey
        get() =
            VotingDelegationPirPrecomputeKey(
                accountUuid = accountUuid,
                roundId = roundId,
                bundleIndex = bundleIndex
            )
}

interface VotingProofPrecomputeRepository {
    fun warmProvingCaches()

    fun startDelegationPirPrecompute(request: VotingDelegationPirPrecomputeRequest)

    suspend fun awaitDelegationPirPrecompute(
        key: VotingDelegationPirPrecomputeKey
    ): Result<VotingDelegationPirPrecomputeResult>?

    /**
     * Waits for the background delegation proof of [key]. Null means one was never scheduled - for
     * a Keystone round, for a bundle whose setup was not ready, or because the policy is off - and
     * the caller should prove on demand as it always did.
     *
     * A proof that was itself cancelled comes back as a failure rather than cancelling the caller;
     * only the caller's own cancellation propagates.
     */
    suspend fun awaitDelegationProof(key: VotingDelegationPirPrecomputeKey): Result<Unit>?

    /**
     * Cancels every pending background proof and zeroes its key material. A native proof already
     * running cannot be interrupted mid-flight; this only stops the ones that have not started.
     */
    fun cancelBackgroundProofs()
}

class VotingProofPrecomputeRepositoryImpl(
    private val votingCryptoClient: VotingCryptoClient,
    private val pirSnapshotResolver: PirSnapshotResolver,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : VotingProofPrecomputeRepository {
    private val warmupStarted = AtomicBoolean(false)
    private val lock = Any()
    private val delegationPirJobs =
        mutableMapOf<VotingDelegationPirPrecomputeKey, Deferred<Result<VotingDelegationPirPrecomputeResult>>>()
    private val delegationProofJobs = mutableMapOf<VotingDelegationPirPrecomputeKey, Deferred<Result<Unit>>>()
    private val resolvedPirServerUrls = mutableMapOf<VotingDelegationPirPrecomputeKey, String>()
    private val liveProofMaterials = mutableSetOf<VotingDelegationProofMaterial>()

    /**
     * One background proof at a time: the JNI holds the per-DB lock for the whole proof, so a second
     * one would only queue behind the first while delaying every foreground voting DB call.
     */
    private val proofPermits = Semaphore(1)

    override fun warmProvingCaches() {
        if (!warmupStarted.compareAndSet(false, true)) {
            return
        }

        scope.launch {
            runCatching { votingCryptoClient.warmProvingCaches() }
                .onFailure { warmupStarted.set(false) }
        }
    }

    override fun startDelegationPirPrecompute(request: VotingDelegationPirPrecomputeRequest) {
        synchronized(lock) {
            val existing = delegationPirJobs[request.key]
            if (existing != null && !existing.isCancelled) {
                return
            }

            delegationPirJobs[request.key] = scope.async { runPrecompute(request) }
            val proofMaterial = request.proofMaterial
            if (proofMaterial != null) {
                liveProofMaterials += proofMaterial
                delegationProofJobs[request.key] = scope.async { runProofStage(request, proofMaterial) }
            }
        }
    }

    override suspend fun awaitDelegationPirPrecompute(
        key: VotingDelegationPirPrecomputeKey
    ): Result<VotingDelegationPirPrecomputeResult>? =
        synchronized(lock) { delegationPirJobs[key] }?.await()

    override suspend fun awaitDelegationProof(key: VotingDelegationPirPrecomputeKey): Result<Unit>? {
        val job = synchronized(lock) { delegationProofJobs[key] } ?: return null
        return try {
            job.await()
        } catch (exception: CancellationException) {
            coroutineContext.ensureActive()
            Result.failure(exception)
        }
    }

    override fun cancelBackgroundProofs() {
        val (jobs, materials) =
            synchronized(lock) {
                val jobs = delegationProofJobs.values.toList()
                val materials = liveProofMaterials.toList()
                delegationProofJobs.clear()
                resolvedPirServerUrls.clear()
                liveProofMaterials.clear()
                jobs to materials
            }
        jobs.forEach { job -> job.cancel() }
        materials.forEach { material -> material.clear() }
    }

    /**
     * The PIR data the proof reads is exactly what [runPrecompute] warms, so the proof waits for it
     * rather than duplicating the fetch. A failed or absent precompute fails the proof stage; the
     * caller then proves on demand as before.
     */
    private suspend fun runProofStage(
        request: VotingDelegationPirPrecomputeRequest,
        material: VotingDelegationProofMaterial
    ): Result<Unit> =
        runCatching {
            try {
                val pirOutcome =
                    requireNotNull(awaitDelegationPirPrecompute(request.key)) {
                        "Voting PIR precompute was never scheduled for round ${request.roundId} " +
                            "bundle ${request.bundleIndex}"
                    }
                pirOutcome.getOrThrow()
                val pirServerUrl =
                    requireNotNull(synchronized(lock) { resolvedPirServerUrls[request.key] }) {
                        "Voting PIR server URL is missing for round ${request.roundId} " +
                            "bundle ${request.bundleIndex}"
                    }
                proofPermits.withPermit { runProof(request, material, pirServerUrl) }
            } finally {
                synchronized(lock) { liveProofMaterials -= material }
                material.clear()
            }
        }

    /**
     * Produces the delegation proof on its own DB handle.
     *
     * Waiting for the permit can take a long time, so the round is re-checked before a handle is
     * opened for a proof whose round may since have been torn down. Only a bundle that is
     * [DelegationPhase.PCZT_BUILT] has the alpha this proof binds to; anything else is either not
     * ready yet or already past the proof, and proving it would be wasted work.
     */
    private suspend fun runProof(
        request: VotingDelegationPirPrecomputeRequest,
        material: VotingDelegationProofMaterial,
        pirServerUrl: String
    ) {
        coroutineContext.ensureActive()
        val dbHandle = votingCryptoClient.openVotingDb(request.votingDbPath)
        check(dbHandle != 0L) { "Failed to open voting DB at ${request.votingDbPath}" }

        try {
            votingCryptoClient.setWalletId(dbHandle, request.walletId, request.networkId)
            val phase =
                votingCryptoClient
                    .delegationPhases(dbHandle, request.roundId)
                    .firstOrNull { bundle -> bundle.bundleIndex == request.bundleIndex }
                    ?.phase
            check(phase == DelegationPhase.PCZT_BUILT) {
                "Voting bundle ${request.bundleIndex} of round ${request.roundId} is $phase, " +
                    "not ${DelegationPhase.PCZT_BUILT}"
            }
            votingCryptoClient.buildAndProveDelegation(
                dbHandle = dbHandle,
                roundId = request.roundId,
                bundleIndex = request.bundleIndex,
                pirServerUrl = pirServerUrl,
                pirLayout = request.pirLayout,
                notesJson = request.notesJson,
                fvkBytes = material.fvkBytes,
                hotkeySeed = material.hotkeySeed,
                seedFingerprint = material.seedFingerprint,
                accountIndex = material.accountIndex,
                roundName = material.roundName
            )
        } finally {
            votingCryptoClient.closeVotingDb(dbHandle)
        }
    }

    private suspend fun runPrecompute(
        request: VotingDelegationPirPrecomputeRequest
    ): Result<VotingDelegationPirPrecomputeResult> =
        runCatching {
            val pirServerUrl =
                pirSnapshotResolver.resolve(
                    endpoints = request.pirEndpoints,
                    expectedSnapshotHeight = request.expectedSnapshotHeight
                )
            synchronized(lock) { resolvedPirServerUrls[request.key] = pirServerUrl }
            val dbHandle = votingCryptoClient.openVotingDb(request.votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at ${request.votingDbPath}" }

            try {
                votingCryptoClient.setWalletId(dbHandle, request.walletId, request.networkId)
                votingCryptoClient.precomputeDelegationPir(
                    dbHandle = dbHandle,
                    roundId = request.roundId,
                    bundleIndex = request.bundleIndex,
                    pirServerUrl = pirServerUrl,
                    pirLayout = request.pirLayout,
                    notesJson = request.notesJson
                )
            } finally {
                votingCryptoClient.closeVotingDb(dbHandle)
            }
            // No round-phase-regression recovery here (2026-08-10): precompute never touches
            // round/delegation phase (see PrepareVotingRoundUseCase/SubmitVotesUseCase's
            // construct-step handling), so that race can no longer occur, and reporting a
            // fake `Result.success(cachedCount=0, fetchedCount=0)` for a real failure is exactly
            // what let a genuine construct failure hide behind "precompute succeeded" and leave
            // a bundle's alpha NULL. A real precompute failure should surface as a plain
            // Result.failure cache miss (callers already treat any failed/absent precompute
            // result as "PIR data wasn't warmed", nothing more).
        }
}
