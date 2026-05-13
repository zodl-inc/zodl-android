package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.ui.common.model.voting.VotingDelegationPirPrecomputeResult
import co.electriccoin.zcash.ui.common.model.voting.isRoundPhaseRegression
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class VotingDelegationPirPrecomputeKey(
    val accountUuid: String,
    val roundId: String,
    val bundleIndex: Int
)

data class VotingDelegationPirPrecomputeRequest(
    val accountUuid: String,
    val walletId: String,
    val votingDbPath: String,
    val roundId: String,
    val bundleIndex: Int,
    val pirEndpoints: List<String>,
    val expectedSnapshotHeight: Long,
    val networkId: Int,
    val notesJson: String
) {
    val key: VotingDelegationPirPrecomputeKey
        get() = VotingDelegationPirPrecomputeKey(
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
        }
    }

    override suspend fun awaitDelegationPirPrecompute(
        key: VotingDelegationPirPrecomputeKey
    ): Result<VotingDelegationPirPrecomputeResult>? =
        synchronized(lock) { delegationPirJobs[key] }?.await()

    private suspend fun runPrecompute(
        request: VotingDelegationPirPrecomputeRequest
    ): Result<VotingDelegationPirPrecomputeResult> =
        runCatching {
            val pirServerUrl = pirSnapshotResolver.resolve(
                endpoints = request.pirEndpoints,
                expectedSnapshotHeight = request.expectedSnapshotHeight
            )
            val dbHandle = votingCryptoClient.openVotingDb(request.votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at ${request.votingDbPath}" }

            try {
                votingCryptoClient.setWalletId(dbHandle, request.walletId)
                votingCryptoClient.precomputeDelegationPir(
                    dbHandle = dbHandle,
                    roundId = request.roundId,
                    bundleIndex = request.bundleIndex,
                    pirServerUrl = pirServerUrl,
                    networkId = request.networkId,
                    notesJson = request.notesJson
                )
            } finally {
                votingCryptoClient.closeVotingDb(dbHandle)
            }
        }.recoverCatching { exception ->
            // Foreground submit can advance the Rust round while this background
            // precompute is still running. Treat that stale phase race as a cache miss.
            if (!exception.isRoundPhaseRegression()) {
                throw exception
            }
            VotingDelegationPirPrecomputeResult(cachedCount = 0, fetchedCount = 0)
        }
}
