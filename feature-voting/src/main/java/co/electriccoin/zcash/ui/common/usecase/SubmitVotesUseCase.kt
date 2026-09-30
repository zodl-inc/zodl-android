package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.VotingRoundSession
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.voting.VotingDelegationInputs
import cash.z.ecc.android.sdk.model.voting.VotingProposalRosterEntry
import cash.z.ecc.android.sdk.model.voting.VotingRoundDriveProgressListener
import cash.z.ecc.android.sdk.model.voting.VotingRoundQuiescence
import cash.z.ecc.android.sdk.model.voting.VotingRoundRunReport
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.voting.VotingErrors
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionProgress
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionRecoverableException
import co.electriccoin.zcash.ui.common.model.voting.VotingSubmissionResult
import co.electriccoin.zcash.ui.common.model.voting.requireKnownPolyLen
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.provider.acquireVotingTorLeaseOrNull
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSessionHolder
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingProposalSelection
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.toCanonicalUuidString
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * voting-5.0.0 round-driver port note: no longer thrown by this file (Keystone signing, the old
 * source of protocol-auth failures, is now routed through
 * [VotingKeystoneSessionHolder.runToCompletion]) — kept only because
 * `VoteConfirmSubmissionVM` still pattern-matches on this type for a specific UI status.
 */
class VotingAuthorizationException(
    cause: Exception
) : Exception(
        cause.message ?: "Voting authorization failed",
        cause
    )

/**
 * Submits a round's votes through the `zcash_voting` round driver. The pre-4.0 per-bundle,
 * per-question loop (`runVoteChains`/`proveVoteBundle`/`postVoteBundle`/`confirmVoteBundle`) is
 * gone: the crate's `RoundExecutor`/`RoundDriver` owns that sequencing behind
 * [VotingCryptoClient.openRoundSession] and one [cash.z.ecc.android.sdk.VotingRoundSession.run]
 * call. Keystone accounts continue on the session [VotingKeystoneSessionHolder] retained across the
 * Sign/Scan flow ([VotingKeystoneSessionHolder.runToCompletion]) instead of opening their own.
 *
 * Resuming a round means calling this again: the round driver re-derives what is left from the
 * round's own on-disk/on-chain state. The app-side [VotingRecoveryRepository] snapshot records
 * the durable outcome around that -- proposal selections before the run, then the submitted phase,
 * submitted proposals and submission time once it succeeds -- for UI and share tracking, not as a
 * step-by-step state machine. A run's outcome is mapped onto [VotingErrors] per quiescence and
 * failure kind by `VotingRoundQuiescenceMapper`; outcomes with no clean pre-4.0 equivalent surface
 * as [VotingErrors.UnexpectedSdkResponse] carrying the crate's own detail.
 */
class SubmitVotesUseCase(
    private val resolveVotingRoundSession: ResolveVotingRoundSessionUseCase,
    private val votingCryptoClient: VotingCryptoClient,
    private val votingHotkeySeedProvider: VotingHotkeySeedProvider,
    private val synchronizerProvider: SynchronizerProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getWalletSeedBytes: GetWalletSeedBytesUseCase,
    private val prepareVotingRound: PrepareVotingRoundUseCase,
    private val votingShareTrackingScheduler: VotingShareTrackingScheduler,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingKeystoneSessionHolder: VotingKeystoneSessionHolder,
    private val votingProofPrecomputeRepository: VotingProofPrecomputeRepository,
) {
    @Suppress("LongMethod")
    suspend operator fun invoke(
        roundId: String,
        choices: Map<Int, Int>,
        onProgress: (VotingSubmissionProgress) -> Unit = {}
    ): VotingSubmissionResult =
        withContext(Dispatchers.IO) {
            if (choices.isEmpty()) {
                return@withContext VotingSubmissionResult(submittedProposalCount = 0)
            }

            val selectedAccount = getSelectedWalletAccount()
            val accountUuidString = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            // Review-screen entry can start a background precompute job
            // (PrecomputeVotingSnapshotBundlesUseCase / WarmVotingPirProofsUseCase) seconds
            // before the user taps Submit. Both paths below -- Keystone (via
            // VotingKeystoneSessionHolder.ensureDelegationPipeline) and non-Keystone (opening
            // votingCryptoClient's own DB session directly) -- would otherwise contend with that
            // job for the shared native (dbPath, walletId) lock, parking the progress UI the same
            // way a lock-contention bug was already fixed for a different cause elsewhere in this
            // port. Cancelling and awaiting termination here, before either path opens its own
            // session, guarantees the lock is free by the time either one needs it.
            //
            // NOT the only entry point into the pipeline, though (correction, Milan's review of
            // PR #6): the Keystone Sign screen reaches ensureDelegationPipeline earlier, via
            // VotingKeystoneRepository.createPcztEncoder, well before this use case ever runs --
            // that call site carries its own identical cancelAndAwaitPrecompute guard for exactly
            // this reason.
            votingProofPrecomputeRepository.cancelAndAwaitPrecompute(accountUuidString, roundId)

            if (selectedAccount is KeystoneAccount) {
                // Every bundle already carries a persisted Keystone signature by the time
                // submission reaches this point -- the Sign screen only navigates back to
                // VoteConfirmSubmission once ScanKeystoneVotingPCZTViewModel.onScanned's
                // isFinished branch fires for the last bundle. What remains is exactly what
                // VotingKeystoneSessionHolder's already-open, already-delegation-satisfied
                // session needs: one more run() call to advance vote casting to completion.
                return@withContext submitKeystoneVotes(
                    roundId = roundId,
                    choices = choices,
                    accountUuidString = accountUuidString,
                    canonicalAccountUuid = selectedAccount.sdkAccount.accountUuid.toCanonicalUuidString(),
                    onProgress = onProgress
                )
            }

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
            if (voteServerUrls.isEmpty()) {
                throw VotingSubmissionRecoverableException(VotingErrors.MissingVotingServerUrl)
            }

            val synchronizer = synchronizerProvider.getSynchronizer()
            val walletDbPath = synchronizerProvider.getVotingWalletDbPath()
            val votingDbPath =
                File(walletDbPath)
                    .parentFile
                    ?.resolve("voting.sqlite3")
                    ?.absolutePath
                    ?: error("Unable to derive voting DB path from $walletDbPath")
            val networkId = synchronizer.network.toVotingNetworkId()
            val hotkeySecret =
                votingHotkeySeedProvider.get(accountUuidString)
                    ?: throw VotingSubmissionRecoverableException(VotingErrors.MissingHotkeySeed(roundId))
            val treeStateBytes = synchronizer.getTreeState(BlockHeight.new(session.snapshotHeight))

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            try {
                votingCryptoClient.setWalletId(dbHandle, accountUuidString, networkId)

                // Tor is a user preference here, not a hard requirement -- mirrors the pre-4.0
                // architecture (VotingApiProvider builds a plain or Tor-routed client depending on
                // the user's preference). A null lease means Tor is disabled (plain HTTP); a Tor
                // init failure propagates rather than silently deanonymizing this submission (see
                // acquireVotingTorLeaseOrNull). Acquired inside the DB try and released in its own
                // finally, so no throw or cancellation between here and openRoundSession can leak
                // either the lease or the DB handle. The lease releases against the Tor client that
                // issued it, so a synchronizer rebuild during the run cannot break the release.
                val torLease = synchronizer.acquireVotingTorLeaseOrNull()
                try {
                    val proposals =
                        session.proposals.map { proposal ->
                            VotingProposalRosterEntry(proposalId = proposal.id, numOptions = proposal.options.size)
                        }
                    val roundSession =
                        votingCryptoClient.openRoundSession(
                            dbHandle = dbHandle,
                            torLease = torLease,
                            roundId = roundId,
                            proposals = proposals,
                            hotkeySecret = hotkeySecret,
                            chainEndpoints = voteServerUrls,
                            operationEpoch = 0L,
                            configuredHelperUrls = voteServerUrls,
                            voteTreeNodeUrls = voteServerUrls,
                            ceremonyStartSeconds = session.ceremonyStart.epochSecond,
                            voteEndTimeSeconds = session.voteEndTime.epochSecond
                        )
                    try {
                        votingRecoveryRepository.storeProposalSelections(
                            accountUuid = accountUuidString,
                            roundId = roundId,
                            proposalSelections =
                                choices.mapValues { (proposalId, choiceId) ->
                                    val numOptions =
                                        session.proposals
                                            .first { proposal -> proposal.id == proposalId }
                                            .options.size
                                    VotingProposalSelection(
                                        choiceId = choiceId,
                                        numOptions = numOptions
                                    )
                                }
                        )

                        roundSession.setBallotIntents(buildBallotIntents(session.proposals, choices))

                        val delegationInputs =
                            VotingDelegationInputs(
                                walletDbPath = walletDbPath,
                                accountUuid = selectedAccount.sdkAccount.accountUuid.toCanonicalUuidString(),
                                anchorTreeStateBytes = treeStateBytes,
                                hotkeySecret = hotkeySecret,
                                pirEndpoints = serviceConfig.pirEndpoints.map { endpoint -> endpoint.url },
                                pirDepth = serviceConfig.pirLayout.requireKnownPolyLen().pirDepth,
                                pirTier0Layers = serviceConfig.pirLayout.tier0Layers,
                                pirTier1Layers = serviceConfig.pirLayout.tier1Layers,
                                pirPolyLen = serviceConfig.pirLayout.polyLen,
                                keystone = false,
                                softwareSeed = getWalletSeedBytes(),
                                keystoneSig = null,
                                keystoneSighash = null,
                                snapshotHeight = session.snapshotHeight,
                                eaPk = session.eaPK,
                                ncRoot = session.ncRoot,
                                nullifierImtRoot = session.nullifierIMTRoot
                            )

                        // Ratchet, not overwrite: the round-driver interleaves several bundles
                        // concurrently (confirmed on-device -- a Delegate-phase event with no tally
                        // update for bundle B can arrive between two CastVote events for bundle A),
                        // so the last event received is not necessarily the most complete state.
                        // completedProposals/totalProposals come from PlanRefreshed's own tally --
                        // a stable "N of M" measured against the run's first plan (see
                        // VotingRoundWorkTally's doc comment) -- and only ever move forward here,
                        // the same fix Vizor Wallet's own zcash_voting v5.0.0 integration uses for
                        // the identical interleaving. See VotingSubmissionProgress.RunningRound's
                        // doc comment for why a per-event bundle/proposal id was dropped instead.
                        var lastCompletedProposals: Int? = null
                        var lastTotalProposals: Int? = null
                        // Guards lastCompletedProposals/lastTotalProposals: like
                        // VotingRoundProgressTracker's own internal state (see its class doc comment),
                        // these are mutated by progressListener below, which is called from whichever
                        // native thread the round-driver's concurrent bundle tasks happen to be
                        // running on -- unsynchronized read-maxOf-write from multiple threads risks a
                        // lost update. Milan's review of PR #6, should-fix.
                        val tallyLock = Any()
                        // Per-proposal progress tracker (each proposal's own fraction is the minimum
                        // across that proposal's own bundles, summed across every proposal currently
                        // in flight) -- moves visibly even in a many-proposal round, unlike tracking
                        // only the slowest bundle of a single proposal. Returns null (show an
                        // indeterminate indicator) until real progress exists. See
                        // VotingRoundProgressTracker's own doc
                        // comment for the Vizor Wallet precedent this mirrors.
                        val progressTracker = VotingRoundProgressTracker()
                        val progressListener =
                            VotingRoundDriveProgressListener { progress ->
                                progress.tally?.let { tally ->
                                    synchronized(tallyLock) {
                                        lastCompletedProposals =
                                            maxOf(lastCompletedProposals ?: 0, tally.completedProposals)
                                        lastTotalProposals =
                                            maxOf(lastTotalProposals ?: 0, tally.totalProposals)
                                    }
                                }
                                progressTracker.record(
                                    progress.step,
                                    progress.proofProgress,
                                    progress.voteCommitProposalId
                                )
                                progressTracker.recordPlan(progress.voteCarryingBundleIndexes)
                                // Snapshot both vars together under the same lock they're written
                                // under, rather than reading them individually below -- a plain var
                                // read with no synchronization has no Java Memory Model guarantee of
                                // seeing another thread's write at all, not just a risk of seeing a
                                // stale one.
                                val (completedForRead, totalForRead) =
                                    synchronized(tallyLock) { lastCompletedProposals to lastTotalProposals }
                                onProgress(
                                    VotingSubmissionProgress.RunningRound(
                                        completedProposals =
                                            progressTracker.estimatedCompletedProposals(
                                                completedForRead,
                                                totalForRead
                                            ),
                                        totalProposals = totalForRead,
                                        proofProgress =
                                            progressTracker.fraction(completedForRead, totalForRead)
                                    )
                                )
                            }
                        val report =
                            runRoundWithBundleFailureRetry(
                                roundId,
                                onRetrying = {
                                    val (completedForRead, totalForRead) =
                                        synchronized(tallyLock) { lastCompletedProposals to lastTotalProposals }
                                    onProgress(
                                        VotingSubmissionProgress.RunningRound(
                                            completedProposals =
                                                progressTracker.estimatedCompletedProposals(
                                                    completedForRead,
                                                    totalForRead
                                                ),
                                            totalProposals = totalForRead,
                                            proofProgress =
                                                progressTracker.fraction(completedForRead, totalForRead),
                                            isRetrying = true
                                        )
                                    )
                                }
                            ) {
                                roundSession.run(delegationInputs, progressListener)
                            }

                        // By design: PersistedChainTerminal must surface to the
                        // user immediately, never be silently retried -- unaffected by
                        // runRoundWithBundleFailureRetry above, which only ever re-invokes this call
                        // for the disjoint Failures-with-isolated-bundle-transport-errors case (see
                        // its own doc comment); every other quiescence, PersistedChainTerminal
                        // included, still falls straight through to the mapped error below on the
                        // very first report. Unknown is explicitly NOT treated as success either --
                        // an earlier version of this code did, and a review of this port caught it as
                        // a defect. See
                        // VotingRoundQuiescenceMapper.kt for the full quiescence/failure -> VotingErrors
                        // mapping.
                        report.toVotingErrorOrNull(roundId)?.let { votingError ->
                            throw VotingSubmissionRecoverableException(votingError)
                        }
                        logPartialOutcomeIfAny(roundId, report)

                        // Schedules VotingShareTrackingWorker unconditionally on success (matches the
                        // pre-parking-commit round-driver implementation of this call, which this
                        // rewrite lost -- see git history for the exact prior call site). Safe to
                        // call even when quiescence was NoWorkLeft: TrackVotingSharesUseCase's own
                        // first pass short-circuits immediately when no unconfirmed shares remain.
                        votingShareTrackingScheduler.schedule(roundId)

                        // Marks the durable recovery snapshot as having successfully submitted votes
                        // for this round -- restores the pre-rewrite phase transition that was lost
                        // in this rewrite.
                        votingRecoveryRepository.setPhase(
                            accountUuidString,
                            roundId,
                            VotingRecoveryPhase.VOTES_SUBMITTED
                        )

                        // Marks this round submitted in the durable recovery snapshot -- without
                        // this, VoteCoinholderPollingVM's persisted (cross-process-restart) fallback
                        // never sees a submitted round (VotingSessionStore's in-memory record is the
                        // only thing that currently works, and only within the same process), so a
                        // freshly relaunched app always shows an already-voted round as still
                        // ACTIVE/enterable rather than VOTED. markProposalSubmitted per proposal is
                        // also what getRoundIdsRequiringShareTracking's submittedProposalIds check
                        // depends on. Lost in the same rewrite as the scheduler call above.
                        choices.keys.forEach { proposalId ->
                            votingRecoveryRepository.markProposalSubmitted(accountUuidString, roundId, proposalId)
                        }
                        votingRecoveryRepository.storeSubmittedAt(
                            accountUuidString,
                            roundId,
                            System.currentTimeMillis() / MILLIS_PER_SECOND
                        )

                        VotingSubmissionResult(submittedProposalCount = report.completedProposals)
                    } finally {
                        closeRoundSessionLogged(roundSession, roundId)
                    }
                } finally {
                    // After roundSession.close(): the session uses the runtime until then.
                    // release() is idempotent and completes even when this coroutine is cancelled.
                    torLease?.release()
                }
            } finally {
                withContext(NonCancellable) {
                    votingCryptoClient.closeVotingDb(dbHandle)
                }
            }
        }

    /**
     * Continues a Keystone round to completion on the already-open, already-delegation-satisfied
     * session [votingKeystoneSessionHolder] retained across the Sign/Scan flow -- every bundle
     * already carries a persisted Keystone signature by the time this is reached (the Sign screen
     * only navigates back to VoteConfirmSubmission once every bundle is signed), so this is one
     * more [VotingKeystoneSessionHolder.runToCompletion] call rather than a fresh
     * [VotingCryptoClient.openRoundSession] the way the non-Keystone path above opens one.
     *
     * [canonicalAccountUuid] (not [accountUuidString]) is what [VotingDelegationInputs.accountUuid]
     * needs -- the native side parses it with `uuid::Uuid::parse_str` to look the account up in the
     * wallet database, matching the derivation already established by the non-Keystone path above
     * and by [co.electriccoin.zcash.ui.common.repository.VotingKeystoneRepositoryImpl.createPcztEncoder].
     * [accountUuidString] (the hex account-scope id) is still what the hotkey seed provider and the
     * recovery-repository calls key on, same as everywhere else in this file.
     *
     * [VotingKeystoneSessionHolder.ensureDelegationPipeline] is called here even though the
     * session is normally already open from the Sign/Scan flow -- it's a no-op in that common
     * case, but re-establishes the pipeline if a prior attempt's failure left it closed, which is
     * what makes it safe below to only close the retained session on genuine success rather than
     * unconditionally in a `finally`. [VotingRecoveryRepository.storeProposalSelections] +
     * [VotingKeystoneSessionHolder.setBallotIntents] are called next, mirroring the non-Keystone
     * path's identical sequence exactly -- without them the round has no cast draft for any
     * proposal and [VotingRoundSession.run] quiesces `NeedsBallot` instead of casting anything;
     * nothing else in the Keystone flow (the Sign/Scan screens only sign bundles) ever sets them.
     *
     * Progress reporting here is intentionally simpler than the non-Keystone path's per-bundle
     * ledger (tally-only, no per-bundle-index floor) -- a deliberate, known inconsistency left in
     * place rather than ported over.
     */
    @Suppress("LongMethod", "LongParameterList", "ThrowsCount")
    private suspend fun submitKeystoneVotes(
        roundId: String,
        choices: Map<Int, Int>,
        accountUuidString: String,
        canonicalAccountUuid: String,
        onProgress: (VotingSubmissionProgress) -> Unit
    ): VotingSubmissionResult {
        val sessionContext = resolveVotingRoundSession(roundId)
        val session = sessionContext.session
        val walletDbPath = synchronizerProvider.getVotingWalletDbPath()
        val votingDbPath =
            File(walletDbPath)
                .parentFile
                ?.resolve("voting.sqlite3")
                ?.absolutePath
                ?: error("Unable to derive voting DB path from $walletDbPath")
        val synchronizer = synchronizerProvider.getSynchronizer()
        val networkId = synchronizer.network.toVotingNetworkId()
        val treeStateBytes = synchronizer.getTreeState(BlockHeight.new(session.snapshotHeight))
        val hotkeySecret =
            votingHotkeySeedProvider.get(accountUuidString)
                ?: throw VotingSubmissionRecoverableException(VotingErrors.MissingHotkeySeed(roundId))
        val voteServerUrls =
            sessionContext.serviceConfig.voteServers
                .map { endpoint -> endpoint.url.trimEnd('/') }
                .distinct()
        // Mirrors the non-Keystone path's identical guard above (Milan's review of PR #6, nit):
        // without it, an empty voteServerUrls list reaches ensureDelegationPipeline as
        // chainEndpoints below and fails deep inside the native delegation pipeline instead of
        // surfacing this same clear, recoverable error up front.
        if (voteServerUrls.isEmpty()) {
            throw VotingSubmissionRecoverableException(VotingErrors.MissingVotingServerUrl)
        }

        val delegationInputs =
            VotingDelegationInputs(
                walletDbPath = walletDbPath,
                accountUuid = canonicalAccountUuid,
                anchorTreeStateBytes = treeStateBytes,
                hotkeySecret = hotkeySecret,
                pirEndpoints = sessionContext.serviceConfig.pirEndpoints.map { it.url },
                pirDepth =
                    sessionContext.serviceConfig.pirLayout
                        .requireKnownPolyLen()
                        .pirDepth,
                pirTier0Layers = sessionContext.serviceConfig.pirLayout.tier0Layers,
                pirTier1Layers = sessionContext.serviceConfig.pirLayout.tier1Layers,
                pirPolyLen = sessionContext.serviceConfig.pirLayout.polyLen,
                keystone = true,
                softwareSeed = null,
                keystoneSig = null,
                keystoneSighash = null,
                snapshotHeight = session.snapshotHeight,
                eaPk = session.eaPK,
                ncRoot = session.ncRoot,
                nullifierImtRoot = session.nullifierIMTRoot
            )

        votingKeystoneSessionHolder.ensureDelegationPipeline(
            roundId = roundId,
            votingDbPath = votingDbPath,
            accountUuidString = accountUuidString,
            networkId = networkId,
            proposals =
                session.proposals.map { proposal ->
                    VotingProposalRosterEntry(proposalId = proposal.id, numOptions = proposal.options.size)
                },
            hotkeySecret = hotkeySecret,
            chainEndpoints = voteServerUrls,
            ceremonyStartSeconds = session.ceremonyStart.epochSecond,
            voteEndTimeSeconds = session.voteEndTime.epochSecond,
            delegationInputs = delegationInputs
        )

        votingRecoveryRepository.storeProposalSelections(
            accountUuid = accountUuidString,
            roundId = roundId,
            proposalSelections =
                choices.mapValues { (proposalId, choiceId) ->
                    val numOptions =
                        session.proposals
                            .first { proposal -> proposal.id == proposalId }
                            .options.size
                    VotingProposalSelection(
                        choiceId = choiceId,
                        numOptions = numOptions
                    )
                }
        )
        votingKeystoneSessionHolder.setBallotIntents(
            roundId = roundId,
            intents = buildBallotIntents(session.proposals, choices)
        )

        var lastCompletedProposals: Int? = null
        var lastTotalProposals: Int? = null
        // Guards lastCompletedProposals/lastTotalProposals -- see the non-Keystone path's
        // identical tallyLock (SubmitVotesUseCase.kt above) for why: progressListener is called
        // from whichever native thread the round-driver's concurrent bundle tasks happen to be
        // running on. Milan's review of PR #6, should-fix.
        val tallyLock = Any()
        val progressTracker = VotingRoundProgressTracker()
        val progressListener =
            VotingRoundDriveProgressListener { progress ->
                progress.tally?.let { tally ->
                    synchronized(tallyLock) {
                        lastCompletedProposals = maxOf(lastCompletedProposals ?: 0, tally.completedProposals)
                        lastTotalProposals = maxOf(lastTotalProposals ?: 0, tally.totalProposals)
                    }
                }
                progressTracker.record(progress.step, progress.proofProgress, progress.voteCommitProposalId)
                progressTracker.recordPlan(progress.voteCarryingBundleIndexes)
                val (completedForRead, totalForRead) =
                    synchronized(tallyLock) { lastCompletedProposals to lastTotalProposals }
                onProgress(
                    VotingSubmissionProgress.RunningRound(
                        completedProposals =
                            progressTracker.estimatedCompletedProposals(completedForRead, totalForRead),
                        totalProposals = totalForRead,
                        proofProgress = progressTracker.fraction(completedForRead, totalForRead)
                    )
                )
            }

        val report =
            runRoundWithBundleFailureRetry(
                roundId,
                unexpectedResponseMessage = "Keystone round session run() returned no report",
                onRetrying = {
                    val (completedForRead, totalForRead) =
                        synchronized(tallyLock) { lastCompletedProposals to lastTotalProposals }
                    onProgress(
                        VotingSubmissionProgress.RunningRound(
                            completedProposals =
                                progressTracker.estimatedCompletedProposals(
                                    completedForRead,
                                    totalForRead
                                ),
                            totalProposals = totalForRead,
                            proofProgress = progressTracker.fraction(completedForRead, totalForRead),
                            isRetrying = true
                        )
                    )
                }
            ) {
                votingKeystoneSessionHolder.runToCompletion(roundId, delegationInputs, progressListener)
            }

        report.toVotingErrorOrNull(roundId)?.let { votingError ->
            throw VotingSubmissionRecoverableException(votingError)
        }
        logPartialOutcomeIfAny(roundId, report)

        // The votes are cast at this point -- record that before anything else can fail, so a
        // problem tearing down the retained session below can never make the user see an error
        // (and the round stay "not submitted") for votes that actually went out.
        votingShareTrackingScheduler.schedule(roundId)
        votingRecoveryRepository.setPhase(accountUuidString, roundId, VotingRecoveryPhase.VOTES_SUBMITTED)
        choices.keys.forEach { proposalId ->
            votingRecoveryRepository.markProposalSubmitted(accountUuidString, roundId, proposalId)
        }
        votingRecoveryRepository.storeSubmittedAt(
            accountUuidString,
            roundId,
            System.currentTimeMillis() / MILLIS_PER_SECOND
        )

        // Only close on genuine success -- unlike the non-Keystone path's roundSession (freshly
        // opened and unconditionally closed every call), this session is retained across the
        // Sign/Scan flow and must survive a transient failure here (e.g. a network blip mid
        // chain-submission) so a retry can resume the same session via ensureDelegationPipeline's
        // no-op path above instead of hitting a "no open session" checkNotNull with no way back
        // in (the Sign screen refuses to re-open once every bundle is already signed). A close
        // failure is logged, not thrown: the submission itself succeeded, and the holder has
        // already cleared its fields, so the next Keystone flow starts from a fresh session.
        closeKeystoneSessionAfterSuccess(roundId)

        return VotingSubmissionResult(submittedProposalCount = report.completedProposals)
    }

    // withContext(Dispatchers.IO) inside roundSession.close() throws immediately instead of running
    // when the parent Job is already cancelled (e.g. the user backed out mid round-drive), so
    // NonCancellable lets the native round session actually close instead of leaking its handle.
    // A close failure is only logged, same as the Keystone path: after a successful run the votes
    // are already on chain and recorded, and after a failed run the original error is the one to
    // surface.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun closeRoundSessionLogged(
        roundSession: VotingRoundSession,
        roundId: String
    ) {
        try {
            withContext(NonCancellable) { roundSession.close() }
        } catch (e: Exception) {
            Log.w(TAG, "step=round-session-close round=$roundId failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun closeKeystoneSessionAfterSuccess(roundId: String) {
        try {
            withContext(NonCancellable) { votingKeystoneSessionHolder.close(roundId) }
        } catch (e: Exception) {
            Log.w(TAG, "step=keystone-session-close round=$roundId failed after a successful submission", e)
        }
    }

    /**
     * Diagnostic-only replacement for the blanket `report.failures.isNotEmpty()` throw the
     * quiescence-based mapper removed. A run can quiesce success-shaped
     * ([cash.z.ecc.android.sdk.model.voting.VotingRoundQuiescence.NoWorkLeft] /
     * `BackgroundShareWorkOnly`, both mapped to `null` by [toVotingErrorOrNull]) while still
     * carrying non-empty `failures`/`skippedBundles` — the caller then reports full success and
     * marks every proposal submitted. That partial outcome is currently invisible; this makes it
     * greppable from logcat.
     *
     * Deliberately NOT an assertion/throw: `skippedBundles` is non-empty **by design** after
     * [SkipRemainingKeystoneBundlesUseCase] (the user explicitly chose to submit only the bundles
     * they had already signed), so a hard failure here would false-positive on a legitimate
     * flow. Escalating this to an error needs a way to distinguish skipped-by-user from
     * skipped-by-the-driver first.
     *
     * Not gated on `BuildConfig.DEBUG` (Milan's review of PR #6, should-fix): this is exactly the
     * kind of partial-outcome signal a QA/internal build needs to actually be able to capture from
     * logcat, and neither field logged here carries anything sensitive (round id, quiescence,
     * failure/skip metadata) -- matching every other diagnostic `Log.w` in this module (see e.g.
     * [VotingProofPrecomputeRepository]'s own unconditional `Log.w(TAG, ...)` calls).
     */
    private fun logPartialOutcomeIfAny(
        roundId: String,
        report: VotingRoundRunReport
    ) {
        if (report.failures.isEmpty() && report.skippedBundles.isEmpty()) {
            return
        }
        Log.w(
            TAG,
            "step=partial-outcome round=$roundId quiescence=${report.quiescence} completed the run with " +
                "failures=${report.failures} skippedBundles=${report.skippedBundles} " +
                "despite a success-shaped quiescence"
        )
    }

    /**
     * Re-invokes [runRound] up to [MAX_BUNDLE_FAILURE_RETRIES] additional times when the report
     * it returns ended in [VotingRoundQuiescence.Failures] with every recorded failure classified
     * as a transient, bundle-isolated kind (see [hasOnlyRetryableBundleFailures]) -- confirmed
     * live on a 13-bundle wallet where one bundle's delegation failed on a bare PIR transport
     * error (`RoundStepFailureKind::Transport`; the crate's default `FailureIsolation::SkipBundle`
     * skips just that bundle with zero in-crate retry of its own). A manual retry there succeeded
     * in ~73s versus the original ~12-minute run: the other already-cast bundles resumed
     * idempotently (never redone -- `NextStep::CastVote`'s own doc comment: a submitted bundle's
     * authority has moved on-chain and is never re-planned), only the failed one was retried. This
     * automates exactly that manual retry instead of surfacing the error and making the voter
     * press submit again.
     *
     * Deliberately narrow: [VotingRoundQuiescence.PersistedChainTerminal]/
     * [VotingRoundQuiescence.ChainTerminal] and every other quiescence are untouched -- only
     * [VotingRoundQuiescence.Failures] whose failures are ALL transient/bundle-isolated kinds
     * triggers a retry. A single non-retryable failure kind (e.g. `DelegationTargetMismatch`,
     * which the crate's own doc comment says retrying never fixes) or any other quiescence
     * returns the report as-is on the very first call, preserving the acceptance criterion that a
     * chain-terminal outcome always surfaces immediately (see this function's call sites).
     */
    private suspend fun runRoundWithBundleFailureRetry(
        roundId: String,
        unexpectedResponseMessage: String = "Round session run() returned no report",
        onRetrying: () -> Unit = {},
        runRound: suspend () -> VotingRoundRunReport?
    ): VotingRoundRunReport {
        suspend fun freshReport() =
            runRound() ?: throw VotingSubmissionRecoverableException(
                VotingErrors.UnexpectedSdkResponse(unexpectedResponseMessage)
            )

        var report = freshReport()
        var attempt = 0
        while (report.hasOnlyRetryableBundleFailures() && attempt < MAX_BUNDLE_FAILURE_RETRIES) {
            attempt++
            // Not gated on BuildConfig.DEBUG -- see logPartialOutcomeIfAny's own doc comment.
            Log.w(
                TAG,
                "step=bundle-failure-retry round=$roundId " +
                    "attempt=$attempt/$MAX_BUNDLE_FAILURE_RETRIES failures=${report.failures}"
            )
            onRetrying()
            delay(BUNDLE_FAILURE_RETRY_DELAY_MS)
            report = freshReport()
        }
        return report
    }

    /**
     * True only for a [VotingRoundQuiescence.Failures] report whose every recorded failure is a
     * transient, bundle-isolated kind ([RETRYABLE_BUNDLE_FAILURE_KINDS]) -- a raw crate debug
     * string per [cash.z.ecc.android.sdk.model.voting.VotingRoundStepFailure.kind]'s own doc
     * comment, matched case-insensitively the same way [toVotingErrorOrDefault] already does.
     * `false` for an empty failure list (nothing to classify as retryable) or any failure kind
     * outside the safe set, so a single logical/permanent failure alongside otherwise-transient
     * ones still blocks the retry.
     */
    private fun VotingRoundRunReport.hasOnlyRetryableBundleFailures(): Boolean =
        quiescence is VotingRoundQuiescence.Failures &&
            failures.isNotEmpty() &&
            failures.all { it.kind.lowercase() in RETRYABLE_BUNDLE_FAILURE_KINDS }

    private companion object {
        const val TAG = "SubmitVotesUseCase"
    }
}

private fun ZcashNetwork.toVotingNetworkId() = if (isMainnet()) 1 else 0

private const val MILLIS_PER_SECOND = 1000L

/** See [SubmitVotesUseCase.runRoundWithBundleFailureRetry]'s own doc comment. */
private const val MAX_BUNDLE_FAILURE_RETRIES = 2
private const val BUNDLE_FAILURE_RETRY_DELAY_MS = 2_000L

/**
 * `RoundStepFailureKind` variants safe to retry automatically -- deliberately narrow. `Transport`
 * and `Busy` are resource/network-level and expected to clear on their own; every other kind
 * (`InvalidInput`, `InsufficientEligibility`, `NoSpendableNotes`, `Storage`,
 * `InvariantViolation`, `Protocol`, `ProofFailed`, `Signing`, `HelperDeliveryIncomplete`,
 * `VoteEnded`, `DelegationTargetMismatch`) is either a logical/permanent condition retrying can
 * never fix, or not yet confirmed safe to retry blindly -- left alone rather than guessed at.
 */
private val RETRYABLE_BUNDLE_FAILURE_KINDS = setOf("transport", "busy")
