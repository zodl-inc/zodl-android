package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.CastVoteSignature
import co.electriccoin.zcash.ui.common.model.voting.DelegatedShareInfo
import co.electriccoin.zcash.ui.common.model.voting.DelegationPhase
import co.electriccoin.zcash.ui.common.model.voting.DelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.SharePayload
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmation
import co.electriccoin.zcash.ui.common.model.voting.TxConfirmationProbeResult
import co.electriccoin.zcash.ui.common.model.voting.TxResult
import co.electriccoin.zcash.ui.common.model.voting.VoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingPirLayout
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionProgress
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionResult
import co.electriccoin.zcash.ui.common.model.voting.VotingTxHashLookup
import co.electriccoin.zcash.ui.common.model.voting.VotingVoteCommitment
import co.electriccoin.zcash.ui.common.model.voting.isDelegationSetupOverwrite
import co.electriccoin.zcash.ui.common.model.voting.isLastMoment
import co.electriccoin.zcash.ui.common.model.voting.requireKnownPolyLen
import co.electriccoin.zcash.ui.common.model.voting.toDelegationRegistration
import co.electriccoin.zcash.ui.common.model.voting.toSharePayloads
import co.electriccoin.zcash.ui.common.model.voting.toVoteCommitmentBundle
import co.electriccoin.zcash.ui.common.model.voting.withSubmitAt
import co.electriccoin.zcash.ui.common.provider.PirSnapshotResolver
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingDelegationPirPrecomputeKey
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingProposalSelection
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toCanonicalUuidString
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.CoroutineContext

/**
 * Supplies one bundle chain with its own [HttpClient]. Returning null means the chain shares the
 * provider's client, which is the behaviour every caller had before chains existed.
 */
fun interface VoteChainClientFactory {
    suspend fun create(): HttpClient?
}

class VotingAuthorizationException(
    cause: Exception
) : Exception(
        cause.message ?: "Voting authorization failed",
        cause
    )

class SubmitVotesUseCase(
    private val resolveVotingRoundSession: ResolveVotingRoundSessionUseCase,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val votingCryptoClient: VotingCryptoClient,
    private val votingProofPrecomputeRepository: VotingProofPrecomputeRepository,
    private val votingApiProvider: VotingApiProvider,
    private val pirSnapshotResolver: PirSnapshotResolver,
    private val votingHotkeySeedProvider: VotingHotkeySeedProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getWalletSeedBytes: GetWalletSeedBytesUseCase,
    private val prepareVotingRound: PrepareVotingRoundUseCase,
    private val votingShareTrackingScheduler: VotingShareTrackingScheduler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val chainClientFactory: VoteChainClientFactory = VoteChainClientFactory { null },
) {
    private class VotingSubmitContext(
        val roundId: String,
        val accountUuidString: String,
        val accountUuidCanonical: String,
        val recovery: VotingRecoverySnapshot,
        val session: VotingSession,
        val voteServerUrl: String,
        val walletDbPath: String,
        val votingDbPath: String,
        val networkId: Int,
        val senderSeed: ByteArray?,
        val accountIndex: Int,
        val accountUfvk: String?,
        val seedFingerprint: ByteArray?,
        val allNotesJson: String,
        val hotkeySeed: ByteArray,
        val isKeystone: Boolean,
        val pirServerUrl: String,
        val pirLayout: VotingPirLayout,
        val singleShare: Boolean,
        val sortedChoices: Map<Int, Int>,
        val totalChoices: Int
    )

    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(
        roundId: String,
        choices: Map<Int, Int>,
        onProgress: (VotingSubmissionProgress) -> Unit = {}
    ): VotingSubmissionResult =
        withContext(ioDispatcher) {
            if (choices.isEmpty()) {
                return@withContext VotingSubmissionResult(submittedProposalCount = 0)
            }

            val selectedAccount = getSelectedWalletAccount()
            val isKeystone = selectedAccount is KeystoneAccount
            val accountUuidString = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()
            val accountUuidCanonical = selectedAccount.sdkAccount.accountUuid.toCanonicalUuidString()

            when (val preparation = prepareVotingRound(roundId)) {
                is VotingRoundPreparationResult.Ready -> {
                    Unit
                }

                is VotingRoundPreparationResult.Ineligible -> {
                    throw VotingSubmissionRecoverableException(VotingErrors.Ineligible)
                }

                is VotingRoundPreparationResult.WalletSyncing -> {
                    throw VotingSubmissionRecoverableException(
                        VotingErrors.WalletSyncing(
                            scannedHeight = preparation.scannedHeight,
                            snapshotHeight = preparation.snapshotHeight
                        )
                    )
                }
            }

            val sessionContext = resolveVotingRoundSession(roundId)
            val session = sessionContext.session
            val sessionRoundId = session.voteRoundId.toHex()
            require(sessionRoundId.equals(roundId, ignoreCase = true)) {
                "Round $roundId does not match active session $sessionRoundId"
            }

            val serviceConfig = sessionContext.serviceConfig
            val voteServerUrls =
                serviceConfig.voteServers
                    .map { endpoint -> endpoint.url.trimEnd('/') }
                    .distinct()
            val voteServerUrl =
                voteServerUrls
                    .firstOrNull()
                    ?: throw VotingSubmissionRecoverableException(VotingErrors.MissingVotingServerUrl)
            val pirServerUrl =
                pirSnapshotResolver.resolve(
                    endpoints = serviceConfig.pirEndpoints.map { endpoint -> endpoint.url },
                    expectedSnapshotHeight = session.snapshotHeight
                )

            val recovery =
                votingRecoveryRepository.get(accountUuidString, roundId)
                    ?: throw VotingSubmissionRecoverableException(
                        VotingErrors.MissingPreparedRecovery(roundId)
                    )
            votingRecoveryRepository.storeVoteServerUrls(accountUuidString, roundId, voteServerUrls)
            votingRecoveryRepository.storeVoteEndEpochSeconds(
                accountUuidString,
                roundId,
                session.voteEndTime.epochSecond
            )
            val recoveryBundleCount = recovery.bundleCount
            val hotkeySeed = getHotkeySeed(accountUuidString, roundId, recovery)

            val synchronizer = synchronizerProvider.getSynchronizer()
            val walletDbPath = synchronizerProvider.getVotingWalletDbPath()
            val votingDbPath =
                File(walletDbPath)
                    .parentFile
                    ?.resolve("voting.sqlite3")
                    ?.absolutePath
                    ?: error("Unable to derive voting DB path from $walletDbPath")
            val networkId = synchronizer.network.toVotingNetworkId()
            val senderSeed = if (isKeystone) null else getWalletSeedBytes()
            val accountIndex = selectedAccount.hdAccountIndex.index.toInt()
            val accountUfvk = selectedAccount.sdkAccount.ufvk
            val seedFingerprint = selectedAccount.sdkAccount.seedFingerprint
            val allNotesJson =
                votingCryptoClient.getWalletNotesJson(
                    walletDbPath = walletDbPath,
                    snapshotHeight = session.snapshotHeight,
                    networkId = networkId,
                    accountUuidBytes = selectedAccount.sdkAccount.accountUuid.value
                )

            val singleShare = recovery.singleShareMode ?: session.isLastMoment()
            val sortedChoices = choices.toSortedMap()
            val totalChoices = sortedChoices.size

            val context =
                VotingSubmitContext(
                    roundId = roundId,
                    accountUuidString = accountUuidString,
                    accountUuidCanonical = accountUuidCanonical,
                    recovery = recovery,
                    session = session,
                    voteServerUrl = voteServerUrl,
                    walletDbPath = walletDbPath,
                    votingDbPath = votingDbPath,
                    networkId = networkId,
                    senderSeed = senderSeed,
                    accountIndex = accountIndex,
                    accountUfvk = accountUfvk,
                    seedFingerprint = seedFingerprint,
                    allNotesJson = allNotesJson,
                    hotkeySeed = hotkeySeed,
                    isKeystone = isKeystone,
                    pirServerUrl = pirServerUrl,
                    pirLayout = serviceConfig.pirLayout.requireKnownPolyLen(),
                    singleShare = singleShare,
                    sortedChoices = sortedChoices,
                    totalChoices = totalChoices
                )

            val dbHandle = votingCryptoClient.openVotingDb(context.votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at ${context.votingDbPath}" }

            val shareDelivery = ShareDelivery(coroutineContext, ioDispatcher)

            try {
                votingCryptoClient.setWalletId(
                    dbHandle,
                    context.accountUuidString,
                    context.networkId
                )
                val bundleCount =
                    recoveryBundleCount
                        ?: votingCryptoClient
                            .getBundleCount(dbHandle, roundId)
                            .takeIf { count -> count >= 0 }
                        ?: throw VotingSubmissionRecoverableException(
                            VotingErrors.MissingBundleCount(roundId)
                        )
                val persistedVotes =
                    votingCryptoClient.getVotes(
                        dbHandle = dbHandle,
                        roundId = roundId
                    )
                val unresolvedCommittedProposalIds =
                    persistedVotes.mapTo(mutableSetOf()) { vote -> vote.proposalId } -
                        context.recovery.submittedProposalIds
                requireCommitmentBackedRetriesIncluded(
                    context = context,
                    unresolvedCommittedProposalIds = unresolvedCommittedProposalIds
                )
                val submittedBundleIndicesByProposal =
                    persistedVotes
                        .filter { vote ->
                            vote.submitted
                        }.groupBy { vote ->
                            vote.proposalId
                        }.mapValues { (_, votes) ->
                            votes.mapTo(mutableSetOf()) { vote -> vote.bundleIndex }.toSet()
                        }
                shareDelivery.seed(
                    votingCryptoClient
                        .getShareDelegations(
                            dbHandle = dbHandle,
                            roundId = roundId
                        ).groupBy { record ->
                            ShareDelegationTarget(
                                bundleIndex = record.bundleIndex,
                                proposalId = record.proposalId
                            )
                        }.mapValues { (_, records) ->
                            records.mapTo(mutableSetOf()) { it.shareIndex }
                        }
                )

                if (context.recovery.needsDelegationSubmission()) {
                    submitDelegationBundles(
                        context = context,
                        dbHandle = dbHandle,
                        bundleCount = bundleCount,
                        onProgress = onProgress
                    )
                }

                val processedProposalCount =
                    submitVoteCommitmentsAndShares(
                        context = context,
                        dbHandle = dbHandle,
                        bundleCount = bundleCount,
                        submittedBundleIndicesByProposal = submittedBundleIndicesByProposal,
                        shareDelivery = shareDelivery,
                        unresolvedCommittedProposalIds = unresolvedCommittedProposalIds,
                        onProgress = onProgress
                    )

                shareDelivery.awaitAll()

                val completedProposalCount =
                    votingRecoveryRepository
                        .get(context.accountUuidString, context.roundId)
                        ?.submittedProposalIds
                        ?.size
                        ?: context.totalChoices

                // Phase transitions track recovery state-machine progress and must run
                // whenever the loop completes, so that share-tracking can resume. The
                // user-facing "voted on this round" marker (`submittedAt` /
                // `markRoundSubmitted`) is gated separately on every expected proposal
                // having been accounted for, mirroring iOS `failCount == 0` semantics.
                votingRecoveryRepository.setPhase(
                    accountUuid = context.accountUuidString,
                    roundId = context.roundId,
                    phase = VotingRecoveryPhase.VOTES_SUBMITTED
                )
                shareDelivery.firstFailure()?.let { failure -> throw failure }
                votingRecoveryRepository.setPhase(
                    accountUuid = context.accountUuidString,
                    roundId = context.roundId,
                    phase = VotingRecoveryPhase.SHARES_SUBMITTED
                )
                if (processedProposalCount == context.totalChoices) {
                    votingRecoveryRepository.storeSubmittedAt(
                        accountUuid = context.accountUuidString,
                        roundId = context.roundId,
                        submittedAtEpochSeconds = Instant.now().epochSecond
                    )
                    votingSessionStore.markRoundSubmitted(
                        accountUuid = context.accountUuidString,
                        roundId = context.roundId,
                        proposalCount = completedProposalCount
                    )
                }
                votingSessionStore.clearDraftVotes(
                    accountUuid = context.accountUuidString,
                    roundId = context.roundId
                )
                votingShareTrackingScheduler.schedule(context.roundId)

                VotingSubmissionResult(submittedProposalCount = completedProposalCount)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                throw exception
            } finally {
                withContext(NonCancellable) { shareDelivery.awaitAll() }
                shareDelivery.close()
                traceVotingStep(
                    roundId = roundId,
                    step = "closeVotingDb"
                ) {
                    votingCryptoClient.closeVotingDb(dbHandle)
                }
            }
        }

    private fun VotingRecoverySnapshot.needsDelegationSubmission(): Boolean =
        phase != VotingRecoveryPhase.DELEGATION_SUBMITTED &&
            phase != VotingRecoveryPhase.VOTES_SUBMITTED &&
            phase != VotingRecoveryPhase.SHARES_SUBMITTED

    /**
     * A persisted vote record means commitment construction completed and its POST may have
     * reached the chain. Until the proposal is durably complete, retries must include it so its
     * resulting VAN is reconciled before a later proposal attempts to spend the same input VAN.
     */
    private fun requireCommitmentBackedRetriesIncluded(
        context: VotingSubmitContext,
        unresolvedCommittedProposalIds: Set<Int>
    ) {
        val omittedProposalId =
            (unresolvedCommittedProposalIds - context.sortedChoices.keys).minOrNull()
        if (omittedProposalId != null) {
            throw VotingSubmissionRecoverableException(
                VotingErrors.OmittedCommittedProposal(
                    roundId = context.roundId,
                    proposalId = omittedProposalId
                )
            )
        }
    }

    /**
     * Posts every delegation bundle first and only then waits for their confirmations. Each wait is
     * a poll-until-mined round trip on the vote chain, so overlapping them turns `bundleCount`
     * sequential waits into one.
     */
    private suspend fun submitDelegationBundles(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleCount: Int,
        onProgress: (VotingSubmissionProgress) -> Unit
    ) {
        val ledger =
            SubmissionProgressLedger(
                bundleCount = bundleCount,
                totalChoices = 1,
                authorizing = true,
                onProgress = onProgress
            )
        val proofPermits = Semaphore(1)
        val postMutex = Mutex()
        coroutineScope {
            (0 until bundleCount)
                .map { bundleIndex ->
                    async(ioDispatcher) {
                        VoteChainSession(chainClientFactory.create()).use { session ->
                            val posted =
                                proofPermits.withPermit {
                                    postMutex.withLock {
                                        proveAndPostDelegationBundle(
                                            context,
                                            dbHandle,
                                            bundleIndex,
                                            session,
                                            ledger
                                        )
                                    }
                                }
                            posted?.let {
                                confirmDelegationBundle(context, dbHandle, it, session, ledger)
                            }
                        }
                    }
                }.awaitAll()
        }

        votingRecoveryRepository.setPhase(
            accountUuid = context.accountUuidString,
            roundId = context.roundId,
            phase = VotingRecoveryPhase.DELEGATION_SUBMITTED
        )
    }

    private suspend fun currentDelegationPhase(
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int
    ): DelegationPhase? =
        votingCryptoClient
            .delegationPhases(dbHandle, roundId)
            .firstOrNull { bundle -> bundle.bundleIndex == bundleIndex }
            ?.phase

    @Suppress("LongMethod")
    private suspend fun buildDelegationProofIfNeeded(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        ledger: SubmissionProgressLedger
    ) {
        val roundId = context.roundId
        if (currentDelegationPhase(dbHandle, roundId, bundleIndex).let {
                it == DelegationPhase.SUBMITTED || it == DelegationPhase.CONFIRMED
            }
        ) {
            return
        }
        val witnessesJson =
            votingCryptoClient.generateNoteWitnessesJson(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                walletDbPath = context.walletDbPath,
                networkId = context.networkId,
                notesJson = context.allNotesJson
            )
        votingCryptoClient.storeWitnesses(
            dbHandle = dbHandle,
            roundId = roundId,
            bundleIndex = bundleIndex,
            notesJson = context.allNotesJson,
            witnessesJson = witnessesJson
        )

        val precomputeResult =
            votingProofPrecomputeRepository.awaitDelegationPirPrecompute(
                VotingDelegationPirPrecomputeKey(
                    accountUuid = context.accountUuidString,
                    roundId = roundId,
                    bundleIndex = bundleIndex
                )
            )
        precomputeResult?.onFailure { throwable ->
            Log.w(TAG, "Voting PIR precompute failed for round $roundId bundle $bundleIndex", throwable)
        }
        // Whether THIS call just wrote fresh PCZT/alpha for this bundle (a construct that
        // succeeds, as opposed to the crate refusing to overwrite already-intact data). This
        // matters because a stale `proofs` row can survive a setup reset (resetVotingSessionState
        // clears `bundles` columns but not the `proofs` table) — if construct just wrote fresh
        // alpha, any existing proof is for the OLD alpha and must be disregarded regardless of
        // what the phase read below says. Precompute's outcome is intentionally never consulted
        // here: it's a best-effort cache warm, not a signal for whether setup is done (a prior
        // version treated a swallowed background-race "success" as "setup already built", which
        // is exactly what left bundle 1's alpha NULL and crashed build_and_prove_delegation).
        var setupJustBuilt = false
        if (!context.isKeystone) {
            runCatching {
                votingCryptoClient.buildGovernancePcztFromSeed(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    ufvk =
                        requireNotNull(context.accountUfvk) {
                            "Software wallet account is missing UFVK for voting bundle $bundleIndex"
                        },
                    networkId = context.networkId,
                    accountIndex = context.accountIndex,
                    notesJson = context.allNotesJson,
                    walletSeed =
                        requireNotNull(context.senderSeed) {
                            "Software wallet seed is missing for voting bundle $bundleIndex"
                        },
                    hotkeySeed = context.hotkeySeed,
                    seedFingerprint =
                        requireNotNull(context.seedFingerprint) {
                            "Software wallet account is missing seed fingerprint for voting bundle $bundleIndex"
                        },
                    roundName = context.session.title
                )
            }.onSuccess {
                setupJustBuilt = true
            }.recoverCatching { throwable ->
                // The crate refuses to silently overwrite already-persisted PCZT fields with
                // different data — this bundle's setup is present and intact, nothing to do.
                if (!throwable.isDelegationSetupOverwrite()) throw throwable
            }.getOrThrow()
        }

        val alreadyProved =
            !setupJustBuilt &&
                bundleIndex !in context.recovery.rebuiltSinceProofBundles &&
                currentDelegationPhase(dbHandle, roundId, bundleIndex).let {
                    it == DelegationPhase.PROVED || it == DelegationPhase.SUBMITTED || it == DelegationPhase.CONFIRMED
                }
        if (!alreadyProved) {
            val fvkBytes =
                votingCryptoClient.extractOrchardFvkFromUfvk(
                    ufvk =
                        requireNotNull(context.accountUfvk) {
                            "Account is missing UFVK for voting bundle $bundleIndex"
                        },
                    networkId = context.networkId
                )
            runVotingAuthorizationStep(context.isKeystone) {
                votingCryptoClient.buildAndProveDelegation(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    pirServerUrl = context.pirServerUrl,
                    pirLayout = context.pirLayout,
                    notesJson = context.allNotesJson,
                    fvkBytes = fvkBytes,
                    hotkeySeed = context.hotkeySeed,
                    seedFingerprint =
                        requireNotNull(context.seedFingerprint) {
                            "Account is missing seed fingerprint for voting bundle $bundleIndex"
                        },
                    accountIndex = context.accountIndex,
                    roundName = context.session.title,
                    proofProgress = { progress ->
                        ledger.update(
                            bundleIndex = bundleIndex,
                            questionIndex = 0,
                            stage = progress * PROOF_STAGE_CEILING
                        )
                    }
                )
            }
            // A fresh proof now matches the current alpha, so any earlier rebuild-since-proof
            // flag for this bundle (see VotingKeystoneRepository.createPcztEncoder) is stale.
            votingRecoveryRepository.clearBundleRebuiltSinceProof(
                accountUuid = context.accountUuidString,
                roundId = roundId,
                bundleIndex = bundleIndex
            )
        }
    }

    /**
     * True when this bundle's delegation is already on chain with a stored VAN position, either
     * because a cached transaction hash resolved to one or because the bundle is already CONFIRMED.
     *
     * A resumed round may already have this specific bundle CONFIRMED - per-bundle phase, not
     * round-level: a multi-bundle round can have bundle 0 confirmed while bundle 1 is still pending,
     * and the round-level phase alone can't tell those apart.
     *
     * SUBMITTED is deliberately NOT treated as resolved here: it means delegation_tx_hash is set but
     * van_leaf_position isn't yet - the normal in-flight state while awaiting confirmation, not an
     * edge case. Falling through is safe: buildDelegationProofIfNeeded no-ops for an
     * already-SUBMITTED bundle (see its own phase check). A software-wallet retry may have a
     * different RedPallas signature and transaction hash even though the persisted VAN commitment is
     * unchanged, so the submit path reconciles a spent-nullifier response against that commitment.
     * It then stores van_leaf_position instead of leaving it unset (which previously wedged the
     * round downstream in the vote tree sync and VAN witness generation).
     */
    private suspend fun isDelegationAlreadyResolved(
        session: VoteChainSession,
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int
    ): Boolean {
        val cachedVanPosition = probeCachedDelegationVanPosition(session, dbHandle, roundId, bundleIndex)
        if (cachedVanPosition != null) {
            votingCryptoClient.storeVanPosition(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                position = cachedVanPosition
            )
            return true
        }
        return currentDelegationPhase(dbHandle, roundId, bundleIndex) == DelegationPhase.CONFIRMED
    }

    /**
     * Proves this bundle's delegation and broadcasts it, stopping once the transaction hash is
     * durable. The hash is stored before any wait, so a crash between the two passes leaves the
     * bundle in the normal in-flight state - hash set, VAN position unset - that
     * [probeCachedDelegationVanPosition] already resumes from. Null means the delegation was
     * resolved without a transaction to await.
     */
    private suspend fun proveAndPostDelegationBundle(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        session: VoteChainSession,
        ledger: SubmissionProgressLedger
    ): PostedDelegation? {
        val roundId = context.roundId
        ledger.update(bundleIndex = bundleIndex, questionIndex = 0, stage = 0.0)

        if (isDelegationAlreadyResolved(session, dbHandle, roundId, bundleIndex)) {
            ledger.update(bundleIndex = bundleIndex, questionIndex = 0, stage = CONFIRMED_STAGE)
            return null
        }

        buildDelegationProofIfNeeded(context, dbHandle, bundleIndex, ledger)
        votingRecoveryRepository.setPhase(
            accountUuid = context.accountUuidString,
            roundId = roundId,
            phase = VotingRecoveryPhase.DELEGATION_PROVED
        )

        return when (
            val submissionResolution = resolveDelegationSubmission(context, dbHandle, bundleIndex, session)
        ) {
            is DelegationSubmissionResolution.ConfirmedVan -> {
                submissionResolution.txHash?.let { txHash ->
                    votingCryptoClient.storeDelegationTxHash(
                        dbHandle = dbHandle,
                        roundId = roundId,
                        bundleIndex = bundleIndex,
                        txHash = txHash
                    )
                }
                votingCryptoClient.storeVanPosition(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    position = submissionResolution.position
                )
                ledger.update(bundleIndex = bundleIndex, questionIndex = 0, stage = CONFIRMED_STAGE)
                null
            }

            is DelegationSubmissionResolution.AcceptedTransaction -> {
                votingCryptoClient.storeDelegationTxHash(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    txHash = submissionResolution.transaction.txHash
                )
                ledger.update(bundleIndex = bundleIndex, questionIndex = 0, stage = POSTED_STAGE)
                PostedDelegation(
                    bundleIndex = bundleIndex,
                    transaction = submissionResolution.transaction
                )
            }
        }
    }

    /**
     * Fast-path probe for an already-cached delegation transaction hash: mirrors iOS
     * `recoverDelegationVanPosition` with `confirmationTimeout: 0`. Returns the confirmed
     * VAN position, or null when the cached hash has no usable confirmation yet (not
     * propagated, failed on-chain, or missing leaf_index) so the caller re-runs the
     * delegation from scratch for this bundle without blocking on the 90s poll budget.
     */
    private suspend fun probeCachedDelegationVanPosition(
        session: VoteChainSession,
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int
    ): Int? {
        val cachedDelegationTxHash =
            votingCryptoClient.getDelegationTxHash(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex
            ) as? VotingTxHashLookup.Present ?: return null
        return awaitTxConfirmation(session, txHash = cachedDelegationTxHash.txHash, maxAttempts = 1)
            ?.takeIf { it.code == 0 }
            ?.event("delegate_vote")
            ?.attribute("leaf_index")
            ?.recoverLeafIndexOrNull()
    }

    @Suppress("LongMethod")
    private suspend fun resolveDelegationSubmission(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        session: VoteChainSession
    ): DelegationSubmissionResolution {
        val roundId = context.roundId
        return runVotingAuthorizationStep(context.isKeystone) {
            val submission =
                if (context.isKeystone) {
                    val keystoneSignature =
                        context.recovery.keystoneBundleSignatures[bundleIndex]
                            ?: error("Keystone signature is missing for voting bundle $bundleIndex")
                    votingCryptoClient.getDelegationSubmissionWithKeystoneSignature(
                        dbHandle = dbHandle,
                        roundId = roundId,
                        bundleIndex = bundleIndex,
                        keystoneSig = keystoneSignature.decodeSpendAuthSig(),
                        keystoneSighash = keystoneSignature.decodeSighash()
                    )
                } else {
                    votingCryptoClient.getDelegationSubmission(
                        dbHandle = dbHandle,
                        roundId = roundId,
                        bundleIndex = bundleIndex,
                        walletDbPath = context.walletDbPath,
                        accountUuid = context.accountUuidCanonical,
                        hotkeySeed = context.hotkeySeed,
                        roundName = context.session.title,
                        senderSeed = requireNotNull(context.senderSeed)
                    )
                }
            if (context.isKeystone) {
                val keystoneSignature =
                    context.recovery.keystoneBundleSignatures[bundleIndex]
                        ?: error("Keystone signature is missing for voting bundle $bundleIndex")
                val expectedSpendAuthSig = keystoneSignature.decodeSpendAuthSig()
                require(submission.spendAuthSig.contentEquals(expectedSpendAuthSig)) {
                    "Delegation signature mismatch for Keystone voting bundle $bundleIndex"
                }
                require(submission.sighash.contentEquals(keystoneSignature.decodeSighash())) {
                    "Delegation sighash mismatch for Keystone voting bundle $bundleIndex"
                }
                keystoneSignature.decodeRk()?.let { expectedRk ->
                    require(submission.rk.contentEquals(expectedRk)) {
                        "Delegation rk mismatch for Keystone voting bundle $bundleIndex"
                    }
                }
            }
            val registration = submission.toDelegationRegistration()
            val result =
                try {
                    session.submitDelegation(registration)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    val recoveredPosition =
                        findPersistedVanPosition(
                            context = context,
                            expectedVanCmx = registration.vanCmx
                        )
                    if (recoveredPosition != null) {
                        return@runVotingAuthorizationStep DelegationSubmissionResolution.ConfirmedVan(
                            recoveredPosition
                        )
                    }
                    throw exception
                }

            reconcileDelegationTransactionResult(
                result = result,
                bundleIndex = bundleIndex,
                rejectionMessage = "Delegation transaction was rejected",
                fetchTxConfirmation = session::fetchTxConfirmation,
                findVanPosition = {
                    findPersistedVanPosition(
                        context = context,
                        expectedVanCmx = registration.vanCmx
                    )
                }
            )
        }
    }

    private suspend fun confirmDelegationBundle(
        context: VotingSubmitContext,
        dbHandle: Long,
        posted: PostedDelegation,
        session: VoteChainSession,
        ledger: SubmissionProgressLedger
    ) {
        val roundId = context.roundId
        val bundleIndex = posted.bundleIndex
        val acceptedTransaction = posted.transaction

        val confirmation =
            acceptedTransaction.confirmation
                ?: runVotingAuthorizationStep(context.isKeystone) {
                    awaitTxConfirmation(session, acceptedTransaction.txHash)
                        ?: throw VotingSubmissionRecoverableException(
                            VotingErrors.TxConfirmationTimedOut(acceptedTransaction.txHash)
                        )
                }
        runVotingAuthorizationStep(context.isKeystone) {
            confirmation.requireAccepted("Delegation transaction failed")
        }

        val vanPosition = confirmation.delegateVoteVanPosition(bundleIndex)
        traceVotingStep(
            roundId = roundId,
            step = "storeDelegationVanPosition",
            bundleIndex = bundleIndex
        ) {
            votingCryptoClient.storeVanPosition(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                position = vanPosition
            )
        }
        ledger.update(bundleIndex = bundleIndex, questionIndex = 0, stage = CONFIRMED_STAGE)
    }

    private suspend fun findPersistedVanPosition(
        context: VotingSubmitContext,
        expectedVanCmx: ByteArray
    ): Int? {
        val voteChainStartHeight = context.session.createdAtHeight.coerceAtLeast(0)
        return try {
            findVanCommitmentPosition(
                roundId = context.roundId,
                startHeight = voteChainStartHeight,
                expectedVanCmx = expectedVanCmx,
                fetchLatest = votingApiProvider::fetchCommitmentTreeLatest,
                fetchLeafPage = votingApiProvider::fetchCommitmentTreeLeafPage
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to reconcile persisted VAN commitment for round ${context.roundId}", exception)
            null
        }
    }

    /**
     * Casts every requested vote and returns how many proposals ended up accounted for.
     *
     * Questions are ordered unresolved-commitments first, and the stable sort keeps ascending
     * proposal order within each group. Per-proposal completion is counted explicitly to mirror iOS
     * `failCount == 0` gating (`VotingStore+Submission.swift` ~line 411-440): failures throw out of
     * the enclosing try block today, but counting keeps `submittedAt` honest if a future skip-path
     * is added that does not throw. A proposal already on chain from a prior run counts as
     * submitted - the user's previous attempt already succeeded for it.
     */
    private suspend fun submitVoteCommitmentsAndShares(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleCount: Int,
        submittedBundleIndicesByProposal: Map<Int, Set<Int>>,
        shareDelivery: ShareDelivery,
        unresolvedCommittedProposalIds: Set<Int>,
        onProgress: (VotingSubmissionProgress) -> Unit
    ): Int {
        val roundId = context.roundId
        votingRecoveryRepository.storeSingleShareMode(
            accountUuid = context.accountUuidString,
            roundId = roundId,
            singleShareMode = context.singleShare
        )

        val questions =
            context.sortedChoices.entries
                .sortedBy { entry -> entry.key !in unresolvedCommittedProposalIds }
                .map { (proposalId, choiceId) ->
                    val proposal =
                        context.session.proposals.firstOrNull { it.id == proposalId }
                            ?: error("Unknown proposal id $proposalId for round $roundId")
                    val submittedBundles = submittedBundleIndicesByProposal[proposalId].orEmpty()
                    VoteQuestion(
                        proposalId = proposalId,
                        choiceId = choiceId,
                        numOptions = proposal.options.size,
                        requestedSelection =
                            proposal.options
                                .firstOrNull { option -> option.id == choiceId }
                                ?.let {
                                    VotingProposalSelection(
                                        choiceId = choiceId,
                                        numOptions = proposal.options.size
                                    )
                                },
                        submittedBundles = submittedBundles,
                        isAlreadyComplete =
                            proposalId in context.recovery.submittedProposalIds &&
                                submittedBundles.size >= bundleCount
                    )
                }

        val ledger =
            SubmissionProgressLedger(
                bundleCount = bundleCount,
                totalChoices = context.totalChoices,
                onProgress = onProgress
            )
        val processedProposalCount = AtomicInteger(0)
        val questionBarrier =
            QuestionBarrier(chainCount = bundleCount) { questionIndex ->
                val question = questions[questionIndex]
                markProposalSubmissionComplete(
                    context.accountUuidString,
                    roundId,
                    question.proposalId,
                    question.requestedSelection
                )
                processedProposalCount.incrementAndGet()
            }

        runVoteChains(
            context = context,
            dbHandle = dbHandle,
            bundleCount = bundleCount,
            questions = questions,
            ledger = ledger,
            shareDelivery = shareDelivery,
            questionBarrier = questionBarrier
        )

        return processedProposalCount.get()
    }

    /**
     * Runs one independent chain per bundle. Chains never share a step: only proving is serialized
     * (one Halo2 proof at a time keeps the device responsive and matches what a single prover can
     * actually do) and only posting is serialized (one broadcast at a time keeps the vote chain's
     * view of the mempool ordered). Everything else — witnesses, the confirmation wait, share
     * delivery — overlaps, which is where the wall-clock saving comes from.
     */
    private suspend fun runVoteChains(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleCount: Int,
        questions: List<VoteQuestion>,
        ledger: SubmissionProgressLedger,
        shareDelivery: ShareDelivery,
        questionBarrier: QuestionBarrier
    ) {
        val proofPermits = Semaphore(1)
        val postMutex = Mutex()
        val coalescer = VoteTreeSyncCoalescer { syncVoteTreeOrThrow(context, dbHandle) }
        val chainTickets = LongArray(bundleCount.coerceAtLeast(1))

        coroutineScope {
            (0 until bundleCount)
                .map { bundleIndex ->
                    async(ioDispatcher) {
                        VoteChainSession(chainClientFactory.create()).use { session ->
                            questions.forEachIndexed { questionIndex, question ->
                                if (!question.isAlreadyComplete && bundleIndex !in question.submittedBundles) {
                                    runVoteChainQuestion(
                                        context = context,
                                        dbHandle = dbHandle,
                                        session = session,
                                        bundleIndex = bundleIndex,
                                        questionIndex = questionIndex,
                                        question = question,
                                        ledger = ledger,
                                        shareDelivery = shareDelivery,
                                        proofPermits = proofPermits,
                                        postMutex = postMutex,
                                        coalescer = coalescer,
                                        chainTickets = chainTickets
                                    )
                                }
                                ledger.update(bundleIndex, questionIndex, CONFIRMED_STAGE)
                                questionBarrier.arrive(questionIndex)
                            }
                        }
                    }
                }.awaitAll()
        }
    }

    private suspend fun runVoteChainQuestion(
        context: VotingSubmitContext,
        dbHandle: Long,
        session: VoteChainSession,
        bundleIndex: Int,
        questionIndex: Int,
        question: VoteQuestion,
        ledger: SubmissionProgressLedger,
        shareDelivery: ShareDelivery,
        proofPermits: Semaphore,
        postMutex: Mutex,
        coalescer: VoteTreeSyncCoalescer,
        chainTickets: LongArray
    ) {
        val selection =
            requireNotNull(question.requestedSelection) {
                "Unknown vote option ${question.choiceId} for proposal ${question.proposalId}"
            }
        ledger.update(bundleIndex, questionIndex, 0.0)

        val reusedCachedVote =
            submitCachedVoteIfReusable(
                context = context,
                dbHandle = dbHandle,
                bundleIndex = bundleIndex,
                proposalId = question.proposalId,
                requestedSelection = selection,
                session = session
            )
        if (!reusedCachedVote) {
            val syncedHeight = coalescer.sync(storeTicket = chainTickets[bundleIndex])
            val commitment =
                proofPermits.withPermit {
                    proveVoteBundle(
                        context = context,
                        dbHandle = dbHandle,
                        syncedHeight = syncedHeight,
                        bundleIndex = bundleIndex,
                        proposalId = question.proposalId,
                        choiceId = question.choiceId,
                        numOptions = question.numOptions,
                        onProofProgress = { proofProgress ->
                            ledger.update(bundleIndex, questionIndex, proofProgress * PROOF_STAGE_CEILING)
                        }
                    )
                }
            val posted =
                postMutex.withLock {
                    postVoteBundle(
                        context = context,
                        dbHandle = dbHandle,
                        session = session,
                        bundleIndex = bundleIndex,
                        proposalId = question.proposalId,
                        commitment = commitment
                    )
                }
            ledger.update(bundleIndex, questionIndex, POSTED_STAGE)

            coalescer.enterConfirmation()
            try {
                confirmVoteBundle(
                    context = context,
                    dbHandle = dbHandle,
                    posted = posted,
                    session = session
                )
                chainTickets[bundleIndex] = coalescer.nextTicket()
            } finally {
                coalescer.exitConfirmation()
            }
        }

        launchShareDelivery(
            context = context,
            dbHandle = dbHandle,
            bundleIndex = bundleIndex,
            proposalId = question.proposalId,
            shareDelivery = shareDelivery
        )
    }

    private suspend fun resolveReusableCachedVoteConfirmation(
        session: VoteChainSession,
        dbHandle: Long,
        roundId: String,
        bundleIndex: Int,
        proposalId: Int
    ): TxConfirmationProbeResult.Confirmed? {
        val cachedVoteTxHash =
            votingCryptoClient.getVoteTxHash(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                proposalId = proposalId
            ) as? VotingTxHashLookup.Present ?: return null
        return probeCachedTx(session, cachedVoteTxHash.txHash)
            .also { confirmation ->
                if (confirmation !is TxConfirmationProbeResult.Confirmed) {
                    Log.i(
                        TAG,
                        "Cached vote tx ${cachedVoteTxHash.txHash} for round $roundId " +
                            "is not reusable ($confirmation); rebuilding commitment"
                    )
                }
            }.takeIf { it is TxConfirmationProbeResult.Confirmed } as? TxConfirmationProbeResult.Confirmed
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun submitCachedVoteIfReusable(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        proposalId: Int,
        requestedSelection: VotingProposalSelection,
        session: VoteChainSession
    ): Boolean {
        val roundId = context.roundId
        val cachedConfirmation =
            resolveReusableCachedVoteConfirmation(
                session = session,
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                proposalId = proposalId
            ) ?: return false

        // Reusing the cached transaction re-binds this proposal to its previously
        // broadcast choice; revalidate the request against the selection lock
        // before recording any positions.
        votingRecoveryRepository.storeProposalSelections(
            accountUuid = context.accountUuidString,
            roundId = roundId,
            proposalSelections = mapOf(proposalId to requestedSelection)
        )

        val confirmation = cachedConfirmation.confirmation
        val (confirmedVanPosition, vcTreePosition) = confirmation.castVoteLeafPositions()
        traceVotingStep(
            roundId = roundId,
            step = "storeCachedVoteVanPosition",
            bundleIndex = bundleIndex,
            proposalId = proposalId
        ) {
            votingCryptoClient.storeVanPosition(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                position = confirmedVanPosition
            )
        }

        traceVotingStep(
            roundId = roundId,
            step = "recordCachedVcPosition",
            bundleIndex = bundleIndex,
            proposalId = proposalId
        ) {
            try {
                votingCryptoClient.recordVcPosition(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    proposalId = proposalId,
                    vcTreePosition = vcTreePosition
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw VotingSubmissionRecoverableException(
                    VotingErrors.MissingCachedCommitment(
                        roundId = roundId,
                        bundleIndex = bundleIndex,
                        proposalId = proposalId
                    ),
                    e
                )
            }
        }
        return true
    }

    private suspend fun syncVoteTreeOrThrow(
        context: VotingSubmitContext,
        dbHandle: Long
    ): Long {
        val roundId = context.roundId
        val syncedHeight =
            traceVotingStep(
                roundId = roundId,
                step = "syncVoteTree"
            ) {
                votingCryptoClient.syncVoteTree(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    nodeUrl = context.voteServerUrl
                )
            }
        if (syncedHeight < 0) {
            throw VotingSubmissionRecoverableException(
                VotingErrors.VoteTreeSyncFailed(roundId)
            )
        }
        return syncedHeight
    }

    /**
     * Builds this bundle's vote commitment and broadcasts it, stopping once the transaction hash is
     * durable. The confirmation wait is deliberately left to [confirmVoteBundle] so every bundle of
     * a question can be in flight at the same time.
     */
    private suspend fun proveVoteBundle(
        context: VotingSubmitContext,
        dbHandle: Long,
        syncedHeight: Long,
        bundleIndex: Int,
        proposalId: Int,
        choiceId: Int,
        numOptions: Int,
        onProofProgress: (Double) -> Unit
    ): VotingVoteCommitment {
        val roundId = context.roundId
        val vanWitnessJson =
            traceVotingStep(
                roundId = roundId,
                step = "generateVanWitnessJson",
                bundleIndex = bundleIndex,
                proposalId = proposalId
            ) {
                votingCryptoClient.generateVanWitnessJson(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    anchorHeight = syncedHeight.toInt()
                )
            }
        val vanWitness = vanWitnessJson.toVanWitnessSummary()
        // Lock the choice at the first step that binds it to durable state:
        // buildVoteCommitment persists the commitment, and the submit below can
        // land on chain even when its response is lost. A failure before this
        // point leaves the selection free to change on retry.
        votingRecoveryRepository.storeProposalSelections(
            accountUuid = context.accountUuidString,
            roundId = roundId,
            proposalSelections =
                mapOf(
                    proposalId to
                        VotingProposalSelection(
                            choiceId = choiceId,
                            numOptions = numOptions
                        )
                )
        )
        val commitment =
            traceVotingStep(
                roundId = roundId,
                step = "buildVoteCommitment",
                bundleIndex = bundleIndex,
                proposalId = proposalId
            ) {
                votingCryptoClient.buildVoteCommitment(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    hotkeySeed = context.hotkeySeed,
                    proposalId = proposalId,
                    choice = choiceId,
                    numOptions = numOptions,
                    witnessJson = vanWitnessJson,
                    vanPosition = vanWitness.position,
                    anchorHeight = vanWitness.anchorHeight,
                    singleShare = context.singleShare,
                    proofProgress = onProofProgress
                )
            }
        return commitment
    }

    /**
     * Broadcasts an already-proved commitment and persists its transaction hash. Kept separate from
     * [proveVoteBundle] so a run can serialize proving (one Halo2 proof at a time) and posting
     * (one broadcast at a time) independently of each other.
     */
    private suspend fun postVoteBundle(
        context: VotingSubmitContext,
        dbHandle: Long,
        session: VoteChainSession,
        bundleIndex: Int,
        proposalId: Int,
        commitment: VotingVoteCommitment
    ): PostedVote {
        val roundId = context.roundId
        val signature = CastVoteSignature(voteAuthSig = commitment.voteAuthSig)
        val acceptedTransaction =
            reconcileVotingTransactionResult(
                result =
                    session.submitVoteCommitment(
                        bundle = commitment.toVoteCommitmentBundle(),
                        signature = signature
                    ),
                rejectionMessage = "Vote commitment transaction was rejected",
                fetchTxConfirmation = session::fetchTxConfirmation
            )
        acceptedTransaction.confirmation?.let { recoveredConfirmation ->
            requireRecoveredCastVoteMatchesCommitment(
                context = context,
                bundleIndex = bundleIndex,
                proposalId = proposalId,
                expectedVanCmx = commitment.voteAuthorityNoteNew,
                expectedVoteCommitment = commitment.voteCommitment,
                confirmation = recoveredConfirmation
            )
        }
        traceVotingStep(
            roundId = roundId,
            step = "storeVoteTxHash",
            bundleIndex = bundleIndex,
            proposalId = proposalId
        ) {
            votingCryptoClient.storeVoteTxHash(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                proposalId = proposalId,
                txHash = acceptedTransaction.txHash
            )
        }

        return PostedVote(
            bundleIndex = bundleIndex,
            proposalId = proposalId,
            txHash = acceptedTransaction.txHash,
            confirmation = acceptedTransaction.confirmation
        )
    }

    private suspend fun confirmVoteBundle(
        context: VotingSubmitContext,
        dbHandle: Long,
        posted: PostedVote,
        session: VoteChainSession
    ) {
        val roundId = context.roundId
        val bundleIndex = posted.bundleIndex
        val proposalId = posted.proposalId

        val confirmation =
            posted.confirmation
                ?: awaitTxConfirmation(session, posted.txHash)
                ?: throw VotingSubmissionRecoverableException(
                    VotingErrors.TxConfirmationTimedOut(posted.txHash)
                )
        confirmation.requireAccepted("Vote commitment transaction failed")

        val (confirmedVanPosition, vcTreePosition) = confirmation.castVoteLeafPositions()
        traceVotingStep(
            roundId = roundId,
            step = "storeConfirmedVoteVanPosition",
            bundleIndex = bundleIndex,
            proposalId = proposalId
        ) {
            votingCryptoClient.storeVanPosition(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                position = confirmedVanPosition
            )
        }
        traceVotingStep(
            roundId = roundId,
            step = "recordVcPosition",
            bundleIndex = bundleIndex,
            proposalId = proposalId
        ) {
            votingCryptoClient.recordVcPosition(
                dbHandle = dbHandle,
                roundId = roundId,
                bundleIndex = bundleIndex,
                proposalId = proposalId,
                vcTreePosition = vcTreePosition
            )
        }
    }

    private suspend fun requireRecoveredCastVoteMatchesCommitment(
        context: VotingSubmitContext,
        bundleIndex: Int,
        proposalId: Int,
        expectedVanCmx: ByteArray,
        expectedVoteCommitment: ByteArray,
        confirmation: TxConfirmation
    ) {
        val (confirmedVanPosition, confirmedVoteCommitmentPosition) = confirmation.castVoteLeafPositions()
        val (actualVanPosition, actualVoteCommitmentPosition) =
            scanRecoveredCastVoteLeafPositions(
                context = context,
                bundleIndex = bundleIndex,
                proposalId = proposalId,
                expectedVanCmx = expectedVanCmx,
                expectedVoteCommitment = expectedVoteCommitment
            )
        if (
            actualVanPosition != confirmedVanPosition.toLong() ||
            actualVoteCommitmentPosition != confirmedVoteCommitmentPosition
        ) {
            throw VotingSubmissionRecoverableException(
                VotingErrors.RecoveredVoteCommitmentMismatch(
                    roundId = context.roundId,
                    bundleIndex = bundleIndex,
                    proposalId = proposalId
                )
            )
        }
    }

    /**
     * Resolves the positions of the recovered cast-vote's two leaves from one
     * paginated tree scan — both leaves live in the same round tree. A scan that
     * cannot be completed proves nothing: it surfaces as the retryable
     * [VotingErrors.RecoveredVoteVerificationUnavailable], never as a verified
     * mismatch and never as a raw scan-invariant exception.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun scanRecoveredCastVoteLeafPositions(
        context: VotingSubmitContext,
        bundleIndex: Int,
        proposalId: Int,
        expectedVanCmx: ByteArray,
        expectedVoteCommitment: ByteArray
    ): Pair<Long?, Long?> =
        try {
            val positions =
                findCommitmentLeafPositions(
                    roundId = context.roundId,
                    startHeight = context.session.createdAtHeight.coerceAtLeast(0),
                    expectedLeaves = listOf(expectedVanCmx, expectedVoteCommitment),
                    fetchLatest = votingApiProvider::fetchCommitmentTreeLatest,
                    fetchLeafPage = votingApiProvider::fetchCommitmentTreeLeafPage
                )
            positions[0] to positions[1]
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw VotingSubmissionRecoverableException(
                VotingErrors.RecoveredVoteVerificationUnavailable(
                    roundId = context.roundId,
                    bundleIndex = bundleIndex,
                    proposalId = proposalId
                ),
                exception
            )
        }

    private suspend fun markProposalSubmissionComplete(
        accountUuid: String,
        roundId: String,
        proposalId: Int,
        requestedSelection: VotingProposalSelection?
    ) {
        // Completing a proposal revalidates its selection lock, so a request
        // that changes a previously broadcast choice can never silently count
        // the old vote as this submission.
        if (requestedSelection != null) {
            votingRecoveryRepository.storeProposalSelections(
                accountUuid = accountUuid,
                roundId = roundId,
                proposalSelections = mapOf(proposalId to requestedSelection)
            )
        }
        // Recovery is durable and written first; if the process dies before the
        // in-memory session store is pruned, the next launch replays this state.
        votingRecoveryRepository.markProposalSubmitted(
            accountUuid = accountUuid,
            roundId = roundId,
            proposalId = proposalId
        )
        votingSessionStore.clearDraftVote(
            accountUuid = accountUuid,
            roundId = roundId,
            proposalId = proposalId
        )
    }

    private suspend fun getHotkeySeed(
        accountUuid: String,
        roundId: String,
        recovery: VotingRecoverySnapshot
    ): ByteArray {
        recovery.decodeHotkeySeed()?.let { legacySeed ->
            if (votingHotkeySeedProvider.get(accountUuid) == null) {
                votingHotkeySeedProvider.store(accountUuid, legacySeed)
            }
            return legacySeed
        }

        return votingHotkeySeedProvider.get(accountUuid)
            ?: throw VotingSubmissionRecoverableException(VotingErrors.MissingHotkeySeed(roundId))
    }

    /**
     * Polls `fetchTxConfirmation` until a confirmation is returned or the attempt budget is
     * exhausted. Mirrors iOS `delegationTxConfirmationStatus` semantics:
     *
     * - `maxAttempts = TX_CONFIRMATION_RETRIES` (default, 45 × 2s ≈ 90s) — fresh-submit waits.
     * - `maxAttempts = 1` — single fetch, no sleep, returns null if the TX hasn't propagated.
     *   Used for the cached-delegation-hash recovery probe so a transient lookup miss does
     *   not stall the submission flow (iOS: `confirmationTimeout: 0`).
     *
     * Returns null when the TX is not seen within the budget; callers decide whether that is
     * fatal (fresh-submit) or a fall-through signal (recovery).
     */
    private suspend fun awaitTxConfirmation(
        session: VoteChainSession,
        txHash: String,
        maxAttempts: Int = TX_CONFIRMATION_RETRIES
    ): TxConfirmation? {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
        repeat(maxAttempts) { attempt ->
            session.fetchTxConfirmation(txHash)?.let { return it }
            if (attempt + 1 < maxAttempts) {
                delay(TX_CONFIRMATION_POLL_MS)
            }
        }
        return null
    }

    private suspend fun probeCachedTx(
        session: VoteChainSession,
        txHash: String
    ): TxConfirmationProbeResult {
        val confirmation =
            awaitTxConfirmation(session, txHash, maxAttempts = 1)
                ?: return TxConfirmationProbeResult.NotFound
        return if (confirmation.code == 0) {
            TxConfirmationProbeResult.Confirmed(confirmation)
        } else {
            TxConfirmationProbeResult.Rejected(confirmation.log)
        }
    }

    private suspend fun submitMissingShares(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        proposalId: Int,
        shareDelivery: ShareDelivery
    ) {
        val roundId = context.roundId
        val target = ShareDelegationTarget(bundleIndex = bundleIndex, proposalId = proposalId)
        val committedVote =
            traceVotingStep(
                roundId = roundId,
                step = "recoverCommittedVote",
                bundleIndex = bundleIndex,
                proposalId = proposalId
            ) {
                votingCryptoClient.recoverCommittedVote(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    proposalId = proposalId
                )
            }
        val payloads =
            committedVote.sharePayloadsJson.toSharePayloads().map { payload ->
                payload.withSubmitAt(
                    votingCryptoClient.scheduledShareSubmitAt(
                        nowSeconds = Instant.now().epochSecond,
                        ceremonyStartSeconds = context.session.ceremonyStart.epochSecond,
                        voteEndTimeSeconds = context.session.voteEndTime.epochSecond,
                        singleShare = context.singleShare
                    )
                )
            }
        val pendingPayloads = shareDelivery.pending(target, payloads)

        if (pendingPayloads.isEmpty()) {
            return
        }

        val delegationResults = delegateSharesWithRetry(pendingPayloads)
        delegationResults.forEach { info ->
            val payload =
                pendingPayloads.firstOrNull { candidate ->
                    candidate.encShare.shareIndex == info.shareIndex &&
                        candidate.proposalId == info.proposalId
                } ?: return@forEach
            traceVotingStep(
                roundId = roundId,
                step = "recordShareDelegation",
                bundleIndex = bundleIndex,
                proposalId = info.proposalId,
                shareIndex = info.shareIndex
            ) {
                votingCryptoClient.recordShareDelegation(
                    dbHandle = dbHandle,
                    roundId = roundId,
                    bundleIndex = bundleIndex,
                    proposalId = info.proposalId,
                    shareIndex = info.shareIndex,
                    sentToUrls = info.acceptedByServers,
                    nullifier = ByteArray(0),
                    submitAt = payload.submitAt
                )
            }
            shareDelivery.recordDelivered(target, info.shareIndex)
        }
    }

    /**
     * Delivers this bundle's shares in the background. A delivery that no server accepts is
     * recorded rather than thrown: it must not stop the votes still to be cast, and the round only
     * fails once every vote is on chain.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun launchShareDelivery(
        context: VotingSubmitContext,
        dbHandle: Long,
        bundleIndex: Int,
        proposalId: Int,
        shareDelivery: ShareDelivery
    ) {
        shareDelivery.launch {
            try {
                submitMissingShares(
                    context = context,
                    dbHandle = dbHandle,
                    bundleIndex = bundleIndex,
                    proposalId = proposalId,
                    shareDelivery = shareDelivery
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(
                    TAG,
                    "Voting share delivery failed for round ${context.roundId} " +
                        "bundle $bundleIndex proposal $proposalId",
                    exception
                )
                shareDelivery.record(exception)
            }
        }
    }

    private suspend fun <T> traceVotingStep(
        roundId: String,
        step: String,
        bundleIndex: Int? = null,
        proposalId: Int? = null,
        shareIndex: Int? = null,
        block: suspend () -> T
    ): T {
        val context =
            buildString {
                append("round=").append(roundId)
                if (bundleIndex != null) append(" bundle=").append(bundleIndex)
                if (proposalId != null) append(" proposal=").append(proposalId)
                if (shareIndex != null) append(" share=").append(shareIndex)
            }
        Log.i(TAG, "Voting trace begin $step $context")
        return try {
            block().also {
                Log.i(TAG, "Voting trace end $step $context")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Voting trace failed $step $context", exception)
            throw exception
        }
    }

    private suspend fun delegateSharesWithRetry(payloads: List<SharePayload>): List<DelegatedShareInfo> {
        var lastRetryableError: Exception? = null
        repeat(SHARE_DELEGATION_ATTEMPTS) { attempt ->
            try {
                return votingApiProvider.delegateShares(payloads)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!exception.isShareDelegationExhaustion() && !exception.isTransientVotingInfrastructureFailure()) {
                    throw exception
                }
                lastRetryableError = exception
                if (attempt + 1 < SHARE_DELEGATION_ATTEMPTS) {
                    delay(SHARE_DELEGATION_RETRY_MS)
                }
            }
        }

        throw lastRetryableError ?: IllegalStateException("No voting server accepted share")
    }

    private fun Throwable.isShareDelegationExhaustion(): Boolean {
        val lower = message.orEmpty().lowercase()
        return lower.contains("no voting server accepted share") ||
            lower.contains("no reachable vote servers") ||
            lower.contains("all configured vote servers failed")
    }

    private fun Throwable.isTransientVotingInfrastructureFailure(): Boolean =
        generateSequence(this) { throwable -> throwable.cause }
            .map { throwable -> throwable.message.orEmpty().lowercase() }
            .any { lower ->
                lower.contains("http 5") ||
                    lower.contains("timeout") ||
                    lower.contains("timed out") ||
                    lower.contains("connect") ||
                    lower.contains("connection") ||
                    lower.contains("transport became inactive") ||
                    lower.contains("grpcstatus") ||
                    lower.contains("network")
            }

    private suspend fun <T> runVotingAuthorizationStep(
        isKeystone: Boolean,
        block: suspend () -> T
    ): T =
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw exception.asVotingAuthorizationExceptionIfNeeded(isKeystone)
        }

    private fun ZcashNetwork.toVotingNetworkId() =
        if (isMainnet()) 1 else 0

    private fun TxConfirmation.requireAccepted(fallbackMessage: String) {
        if (code != 0) {
            throw IllegalStateException(log.ifEmpty { fallbackMessage })
        }
    }

    private fun String.toVanWitnessSummary(): VanWitnessSummary {
        val json = JSONObject(this)
        return VanWitnessSummary(
            position = json.getInt("position"),
            anchorHeight = json.getInt("anchor_height")
        )
    }

    private data class VanWitnessSummary(
        val position: Int,
        val anchorHeight: Int
    )

    private data class ShareDelegationTarget(
        val bundleIndex: Int,
        val proposalId: Int
    )

    /**
     * Owns the background delivery of encrypted shares: the coroutine scope they run on, which
     * share indices are already on a helper server, the jobs still in flight, and the failures they
     * reported.
     *
     * Share delivery is Tor traffic to helper servers and never blocks the vote chain, so keeping it
     * off the critical path removes one network round trip per bundle per question.
     *
     * The jobs run on a supervisor child of the submission's own job: a delivery that fails never
     * cancels the votes still to be cast, but cancelling the submission still cancels the
     * deliveries.
     */
    private class ShareDelivery(
        parentContext: CoroutineContext,
        dispatcher: CoroutineDispatcher
    ) {
        private val supervisor = SupervisorJob(parentContext.job)
        private val scope = CoroutineScope(parentContext + supervisor + dispatcher)

        private val mutex = Mutex()
        private val deliveredByTarget = mutableMapOf<ShareDelegationTarget, MutableSet<Int>>()

        private val lock = ReentrantLock()
        private val jobs = mutableListOf<Job>()
        private val failures = mutableListOf<Exception>()

        fun seed(delivered: Map<ShareDelegationTarget, Set<Int>>) {
            deliveredByTarget.clear()
            delivered.forEach { (target, indices) -> deliveredByTarget[target] = indices.toMutableSet() }
        }

        fun launch(block: suspend () -> Unit) {
            lock.withLock { jobs += scope.launch { block() } }
        }

        /**
         * Waits for every delivery started so far. A caller that must not be interrupted - the one
         * closing the voting DB - wraps this in `NonCancellable`, because a plain join throws once
         * the outer job is cancelled and the DB must not close under a running job.
         */
        suspend fun awaitAll() {
            lock.withLock { jobs.toList() }.joinAll()
        }

        /**
         * Finishes the supervisor job. It is a `CompletableJob`, so without this the enclosing
         * `withContext` would wait on it forever.
         */
        fun close() {
            supervisor.complete()
        }

        fun record(exception: Exception) {
            lock.withLock { failures += exception }
        }

        /**
         * The first delivery failure, if any. A share no server accepted was never
         * `recordShareDelegation`-ed, so [co.electriccoin.zcash.ui.common.usecase.TrackVotingSharesUseCase]
         * cannot pick it up later and the submission has to surface it - just once every vote is
         * safely on chain. A retry re-enters through the cached-vote path and resends only the
         * share indices that are still missing.
         */
        fun firstFailure(): Exception? = lock.withLock { failures.firstOrNull() }

        suspend fun pending(
            target: ShareDelegationTarget,
            payloads: List<SharePayload>
        ): List<SharePayload> =
            mutex.withLock {
                val delivered = deliveredByTarget.getOrPut(target) { mutableSetOf() }
                payloads.filterNot { payload -> payload.encShare.shareIndex in delivered }
            }

        suspend fun recordDelivered(
            target: ShareDelegationTarget,
            shareIndex: Int
        ) {
            mutex.withLock {
                deliveredByTarget.getOrPut(target) { mutableSetOf() } += shareIndex
            }
        }
    }

    /** One question of the round, resolved once up front so every chain reads the same view. */
    private data class VoteQuestion(
        val proposalId: Int,
        val choiceId: Int,
        val numOptions: Int,
        /**
         * Null when the requested choice no longer matches a known option; such a request neither
         * locks a selection nor conflicts with one.
         */
        val requestedSelection: VotingProposalSelection?,
        val submittedBundles: Set<Int>,
        val isAlreadyComplete: Boolean
    )

    /**
     * Fires [onQuestionComplete] once every chain has passed a question, so the selection lock and
     * `markProposalSubmitted` are written exactly once and only after the last bundle is done.
     */
    private class QuestionBarrier(
        private val chainCount: Int,
        private val onQuestionComplete: suspend (Int) -> Unit
    ) {
        private val mutex = Mutex()
        private val arrivals = mutableMapOf<Int, Int>()

        suspend fun arrive(questionIndex: Int) {
            val isLastChain =
                mutex.withLock {
                    val arrived = (arrivals[questionIndex] ?: 0) + 1
                    arrivals[questionIndex] = arrived
                    arrived >= chainCount
                }
            if (isLastChain) {
                onQuestionComplete(questionIndex)
            }
        }
    }

    /**
     * One bundle chain's connection to the vote chain. A null client means "use the shared one",
     * which is what every call did before chains existed and what tests without a factory still get.
     */
    private inner class VoteChainSession(
        private val client: HttpClient?
    ) : AutoCloseable {
        suspend fun submitDelegation(registration: DelegationRegistration): TxResult =
            if (client == null) {
                votingApiProvider.submitDelegation(registration)
            } else {
                votingApiProvider.submitDelegation(registration, client)
            }

        suspend fun submitVoteCommitment(
            bundle: VoteCommitmentBundle,
            signature: CastVoteSignature
        ): TxResult =
            if (client == null) {
                votingApiProvider.submitVoteCommitment(bundle, signature)
            } else {
                votingApiProvider.submitVoteCommitment(bundle, signature, client)
            }

        suspend fun fetchTxConfirmation(txHash: String): TxConfirmation? =
            if (client == null) {
                votingApiProvider.fetchTxConfirmation(txHash)
            } else {
                votingApiProvider.fetchTxConfirmation(txHash, client)
            }

        override fun close() {
            client?.close()
        }
    }

    /** A vote commitment whose transaction hash is durable but whose confirmation is still pending. */
    private data class PostedVote(
        val bundleIndex: Int,
        val proposalId: Int,
        val txHash: String,
        val confirmation: TxConfirmation?
    )

    /** A delegation whose transaction hash is durable but whose confirmation is still pending. */
    private data class PostedDelegation(
        val bundleIndex: Int,
        val transaction: AcceptedVotingTransaction
    )

    private companion object {
        const val TAG = "SubmitVotesUseCase"
        const val TX_CONFIRMATION_RETRIES = 45
        const val TX_CONFIRMATION_POLL_MS = 2_000L
        const val SHARE_DELEGATION_ATTEMPTS = 3
        const val SHARE_DELEGATION_RETRY_MS = 2_000L
    }
}

internal data class AcceptedVotingTransaction(
    val txHash: String,
    val confirmation: TxConfirmation?
)

/** Fraction of a bundle's per-question progress a completed zero-knowledge proof accounts for. */
internal const val PROOF_STAGE_CEILING = 0.85

/** Fraction reached once the transaction is broadcast and its hash is durable. */
internal const val POSTED_STAGE = 0.9

/** Fraction reached once the transaction is confirmed and its positions are stored. */
internal const val CONFIRMED_STAGE = 1.0

/**
 * Tracks per-bundle progress so the overall figure stays monotonic when bundles no longer advance
 * in lockstep - a later bundle can be proving while an earlier one is still awaiting confirmation.
 *
 * Each bundle's position is `completedQuestions + stageFraction`, the reported fraction is the mean
 * of those positions, and `current` follows the slowest bundle so the "question X of N" label never
 * runs ahead of work that is still outstanding.
 *
 * Guarded by a plain lock rather than a coroutine `Mutex`: the native proof-progress callback is not
 * a suspending function, and the critical section is arithmetic plus one listener call.
 */
internal class SubmissionProgressLedger(
    bundleCount: Int,
    private val totalChoices: Int,
    private val authorizing: Boolean = false,
    private val onProgress: (VotingSubmissionProgress) -> Unit
) {
    private val lock = ReentrantLock()
    private val positions = DoubleArray(bundleCount.coerceAtLeast(1))

    fun update(
        bundleIndex: Int,
        questionIndex: Int,
        stage: Double
    ) {
        lock.withLock {
            val position = questionIndex + stage.coerceIn(0.0, 1.0)
            if (position > positions[bundleIndex]) {
                positions[bundleIndex] = position
            }
            onProgress(currentProgress())
        }
    }

    private fun currentProgress(): VotingSubmissionProgress {
        val progress = overallProgress()
        return if (authorizing) {
            VotingSubmissionProgress.Authorizing(progress = progress)
        } else {
            VotingSubmissionProgress.Submitting(
                current = (positions.min().toInt() + 1).coerceAtMost(totalChoices),
                total = totalChoices,
                progress = progress
            )
        }
    }

    private fun overallProgress(): Float {
        var accumulated = 0.0
        positions.forEach { position ->
            val completedQuestions = position.toInt()
            accumulated +=
                calculateSubmittingBundleProgress(
                    proposalIndex = completedQuestions,
                    bundleIndex = 0,
                    bundleCount = 1,
                    totalChoices = totalChoices,
                    bundleProgress = position - completedQuestions
                )
        }
        return (accumulated / positions.size).toFloat().coerceIn(0f, 1f)
    }
}

internal sealed interface DelegationSubmissionResolution {
    data class AcceptedTransaction(
        val transaction: AcceptedVotingTransaction
    ) : DelegationSubmissionResolution

    data class ConfirmedVan(
        val position: Int,
        val txHash: String? = null
    ) : DelegationSubmissionResolution
}

/**
 * Treats a spent-nullifier rejection as an ambiguous retry only when the rejected response's hash
 * resolves to a successful transaction. This mirrors Vizor's bounded recovery behavior without
 * persisting an unconfirmed rejection hash.
 */
internal suspend fun reconcileVotingTransactionResult(
    result: TxResult,
    rejectionMessage: String,
    maxRecoveryAttempts: Int = SPENT_NULLIFIER_RECOVERY_ATTEMPTS,
    recoveryDelayMillis: Long = SPENT_NULLIFIER_RECOVERY_POLL_MS,
    fetchTxConfirmation: suspend (String) -> TxConfirmation?
): AcceptedVotingTransaction {
    require(maxRecoveryAttempts >= 1) { "maxRecoveryAttempts must be >= 1, was $maxRecoveryAttempts" }
    require(recoveryDelayMillis >= 0) { "recoveryDelayMillis must be non-negative" }

    if (result.code == 0) {
        check(result.txHash.isNotBlank()) { "Accepted voting transaction did not include tx_hash" }
        return AcceptedVotingTransaction(result.txHash, confirmation = null)
    }

    if (result.txHash.isNotBlank() && result.log.isSpentNullifierRejection()) {
        repeat(maxRecoveryAttempts) { attempt ->
            val confirmation = fetchTxConfirmation(result.txHash)
            if (confirmation?.code == 0) {
                return AcceptedVotingTransaction(result.txHash, confirmation)
            }
            if (confirmation != null) {
                throw IllegalStateException(
                    confirmation.log.ifEmpty { result.log.ifEmpty { rejectionMessage } }
                )
            }
            if (attempt + 1 < maxRecoveryAttempts) {
                delay(recoveryDelayMillis)
            }
        }
    }

    throw IllegalStateException(result.log.ifEmpty { rejectionMessage })
}

@Suppress("TooGenericExceptionCaught")
internal suspend fun reconcileDelegationTransactionResult(
    result: TxResult,
    bundleIndex: Int,
    rejectionMessage: String,
    fetchTxConfirmation: suspend (String) -> TxConfirmation?,
    findVanPosition: suspend () -> Int?
): DelegationSubmissionResolution {
    if (!result.log.isSpentNullifierRejection()) {
        return DelegationSubmissionResolution.AcceptedTransaction(
            reconcileVotingTransactionResult(result, rejectionMessage, fetchTxConfirmation = fetchTxConfirmation)
        )
    }

    var hashFailure: Exception? = null
    val acceptedTransaction =
        try {
            reconcileVotingTransactionResult(
                result = result,
                rejectionMessage = rejectionMessage,
                fetchTxConfirmation = fetchTxConfirmation
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            hashFailure = exception
            null
        }

    return if (acceptedTransaction != null) {
        resolveRecoveredDelegation(acceptedTransaction, bundleIndex, findVanPosition)
    } else {
        val vanPosition = findVanPosition() ?: throw hashFailure ?: IllegalStateException(rejectionMessage)
        DelegationSubmissionResolution.ConfirmedVan(vanPosition)
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun resolveRecoveredDelegation(
    accepted: AcceptedVotingTransaction,
    bundleIndex: Int,
    findVanPosition: suspend () -> Int?
): DelegationSubmissionResolution {
    val confirmation =
        checkNotNull(accepted.confirmation) {
            "Spent-nullifier hash recovery must include a confirmation"
        }
    return try {
        confirmation.delegateVoteVanPosition(bundleIndex)
        DelegationSubmissionResolution.AcceptedTransaction(accepted)
    } catch (exception: CancellationException) {
        throw exception
    } catch (leafException: Exception) {
        val vanPosition = findVanPosition() ?: throw leafException
        DelegationSubmissionResolution.ConfirmedVan(
            position = vanPosition,
            txHash = accepted.txHash
        )
    }
}

private fun String.isSpentNullifierRejection(): Boolean =
    contains("nullifier", ignoreCase = true) &&
        contains("spent", ignoreCase = true)

internal fun Exception.asVotingAuthorizationExceptionIfNeeded(isKeystone: Boolean): Exception =
    when {
        this is VotingSubmissionRecoverableException -> this
        this is VotingAuthorizationException -> this
        isKeystone -> VotingAuthorizationException(this)
        else -> this
    }

internal fun TxConfirmation.delegateVoteVanPosition(bundleIndex: Int): Int {
    val rawLeafIndex =
        event("delegate_vote")
            ?.attribute("leaf_index")
            ?: throw unexpectedSdkResponse("Missing delegate_vote leaf_index for bundle $bundleIndex")

    return rawLeafIndex.recoverLeafIndexOrNull()
        ?: throw unexpectedSdkResponse(
            "Malformed delegate_vote leaf_index for bundle $bundleIndex: $rawLeafIndex"
        )
}

/**
 * Some compatibility clients opportunistically Base64-decode CometBFT event text before we see
 * it. A plain decimal position parses immediately; otherwise, if the text contains a byte that
 * can't be plain ASCII decimal, re-encoding it as Base64 restores the original decimal position.
 * Returns null if neither interpretation yields a valid position - shared by every `leaf_index`
 * reader so a fix here covers all of them, not just whichever call site reproduced the bug.
 */
internal fun String.recoverLeafIndexOrNull(): Int? {
    val normalized = trim()
    val plainDecimal = normalized.toIntOrNull()
    if (plainDecimal != null) return plainDecimal

    return normalized
        .takeIf { it.any { character -> character.code > ASCII_MAX_CODE_POINT } }
        ?.let { nonAscii -> Base64.getEncoder().encodeToString(nonAscii.toByteArray(Charsets.UTF_8)) }
        ?.toIntOrNull()
}

internal fun TxConfirmation.castVoteLeafPositions(): Pair<Int, Long> {
    val rawLeafIndex =
        event("cast_vote")
            ?.attribute("leaf_index")
            ?: throw unexpectedSdkResponse("Missing cast_vote leaf_index")
    // Unlike delegate_vote's single leaf_index, this one is "van,commitment" - a comma is never
    // part of the Base64 alphabet, so if this text were ever opportunistically Base64-decoded
    // upstream (see recoverLeafIndexOrNull), splitting on ',' would fail here and we'd throw
    // below rather than silently mis-parse a corrupted value. No recovery attempt is needed as
    // long as that assumption about the upstream decode holds; see
    // castVoteLeafPositionsRejectsCorruptedLeafIndexInsteadOfMisparsing for the guard test.
    val leafParts = rawLeafIndex.split(',')
    val vanPosition = leafParts.getOrNull(0)?.trim()?.toIntOrNull()
    val voteCommitmentPosition = leafParts.getOrNull(1)?.trim()?.toLongOrNull()
    if (leafParts.size != 2 || vanPosition == null || voteCommitmentPosition == null) {
        throw unexpectedSdkResponse("Malformed cast_vote leaf_index: $rawLeafIndex")
    }
    return vanPosition to voteCommitmentPosition
}

private fun unexpectedSdkResponse(message: String) =
    VotingSubmissionRecoverableException(VotingErrors.UnexpectedSdkResponse(message))

private const val ASCII_MAX_CODE_POINT = 0x7F

internal fun calculateSubmittingBundleProgress(
    proposalIndex: Int,
    bundleIndex: Int,
    bundleCount: Int,
    totalChoices: Int,
    bundleProgress: Double
): Float {
    require(proposalIndex >= 0) { "proposalIndex must be non-negative" }
    require(bundleIndex >= 0) { "bundleIndex must be non-negative" }
    require(bundleCount > 0) { "bundleCount must be positive" }
    require(totalChoices > 0) { "totalChoices must be positive" }

    val completedBundles =
        proposalIndex * bundleCount +
            bundleIndex +
            bundleProgress.coerceIn(0.0, 1.0)
    val bundleTotal = totalChoices * bundleCount

    return (completedBundles / bundleTotal).toFloat().coerceIn(0f, 1f)
}

internal const val SPENT_NULLIFIER_RECOVERY_ATTEMPTS = 3
internal const val SPENT_NULLIFIER_RECOVERY_POLL_MS = 1_000L

/**
 * Collapses the per-question vote-tree syncs of concurrent chains into one, without ever handing
 * a chain a tree that is missing its own leaf.
 *
 * Two hazards make a plain shared Deferred unsafe. A chain that stores its VAN position after a
 * cached sync started would get a tree without that leaf, so a sync is only reused when it began
 * at or after the ticket the caller took when it last stored a position. And a chain whose leaf
 * is already on chain but whose position is not yet stored makes the crate treat the tree as
 * needing a full rebuild, so a fresh sync first waits for every chain to leave that window.
 */
internal class VoteTreeSyncCoalescer(
    private val sync: suspend () -> Long
) {
    private val mutex = Mutex()
    private val inFlightConfirmations = MutableStateFlow(0)
    private var ticketCounter = 0L
    private var current: CompletableDeferred<Long>? = null
    private var currentTicket = 0L

    suspend fun nextTicket(): Long = mutex.withLock { ++ticketCounter }

    /**
     * Opens the window in which this chain's leaf is on chain but its position is not stored yet.
     * A tree sync started inside that window would make the crate rebuild the whole tree, so no
     * fresh sync begins until every chain has left it.
     */
    fun enterConfirmation() {
        inFlightConfirmations.update { count -> count + 1 }
    }

    fun exitConfirmation() {
        inFlightConfirmations.update { count -> count - 1 }
    }

    suspend fun sync(storeTicket: Long): Long {
        var gate = CompletableDeferred<Long>()
        var isLeader = false
        mutex.withLock {
            val existing = current
            if (existing != null && currentTicket >= storeTicket) {
                gate = existing
            } else {
                isLeader = true
                ticketCounter += 1
                currentTicket = ticketCounter
                current = gate
            }
        }
        if (isLeader) {
            runLeaderSync(gate)
        }
        return gate.await()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runLeaderSync(gate: CompletableDeferred<Long>) {
        try {
            inFlightConfirmations.first { count -> count == 0 }
            gate.complete(sync())
        } catch (exception: CancellationException) {
            gate.completeExceptionally(exception)
            throw exception
        } catch (exception: Exception) {
            clearFailedLeader(gate)
            gate.completeExceptionally(exception)
        }
    }

    private suspend fun clearFailedLeader(gate: CompletableDeferred<Long>) {
        mutex.withLock {
            if (current === gate) {
                current = null
            }
        }
    }
}
