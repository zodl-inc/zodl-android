package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.exception.TorUnavailableException
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
import co.electriccoin.zcash.ui.common.model.voting.chpBenchLog
import co.electriccoin.zcash.ui.common.model.voting.requireKnownPolyLen
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.VotingCryptoClient
import co.electriccoin.zcash.ui.common.provider.VotingHotkeySeedProvider
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneSessionHolder
import co.electriccoin.zcash.ui.common.repository.VotingProofPrecomputeRepository
import co.electriccoin.zcash.ui.common.repository.VotingProposalSelection
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryPhase
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.toCanonicalUuidString
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.voting.BuildConfig
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * voting-5.0.0 round-driver port note: no longer thrown by this file (Keystone signing, the old
 * source of protocol-auth failures, was deferred per Task 7 but is now routed through
 * [VotingKeystoneSessionHolder.runToCompletion] as of Task 18) — kept only because
 * `VoteConfirmSubmissionVM` still pattern-matches on this type for a specific UI status.
 */
class VotingAuthorizationException(
    cause: Exception
) : Exception(
        cause.message ?: "Voting authorization failed",
        cause
    )

/**
 * voting-5.0.0 round-driver port note: this is a from-scratch rewrite for the benchmark pass
 * (Task 4/5 of the port plan), not an incremental patch of the pre-4.0 implementation. The old
 * ~1700-line per-bundle-per-question loop (`runVoteChains`/`proveVoteBundle`/`postVoteBundle`/
 * `confirmVoteBundle`) is gone entirely — the crate's own `RoundExecutor`/`RoundDriver` now owns
 * that sequencing internally behind [VotingCryptoClient.openRoundSession] + one
 * [cash.z.ecc.android.sdk.VotingRoundSession.run] call.
 *
 * Scope cut for this pass (see the port plan's "Scope cut" section): Keystone accounts are now
 * routed through [VotingKeystoneSessionHolder.runToCompletion] instead of this method's own
 * open/run sequence (Task 18) rather than rejected outright; there is no persisted recovery
 * snapshot — resuming a round means calling this again, which re-derives everything from the
 * round's own on-disk/on-chain state via [VotingRoundSession.run] rather than a local state
 * machine (Task 8 default); errors
 * are passed through as one generic [VotingErrors.UnexpectedSdkResponse] rather than mapped
 * per-failure-type (Task 9-lite). Do not treat this as a full replacement for the pre-4.0
 * implementation's UI-facing error granularity.
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
            val chpBenchStart = System.currentTimeMillis()

            val selectedAccount = getSelectedWalletAccount()
            val accountUuidString = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            // Important #1 (final whole-plan review): review-screen entry can start a
            // background precompute job (PrecomputeVotingSnapshotBundlesUseCase /
            // WarmVotingPirProofsUseCase) seconds before the user taps Submit. Both paths below
            // -- Keystone (via VotingKeystoneSessionHolder.ensureDelegationPipeline) and
            // non-Keystone (opening votingCryptoClient's own DB session directly) -- would
            // otherwise contend with that job for the shared native (dbPath, walletId) lock,
            // parking the progress UI the exact same way Task 6 already fixed for a different
            // cause. Cancelling and awaiting termination here, before either path opens its own
            // session, guarantees the lock is free by the time either one needs it. This is the
            // single entry point both submission paths funnel through, so one call site here
            // covers both.
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
            // Tor is a user preference here, not a hard requirement -- mirrors the
            // pre-4.0 architecture (VotingApiProvider builds a plain or Tor-routed
            // client depending on the user's preference). `0L` is the SDK-side "no
            // Tor runtime" sentinel `resolve_tor_runtime` handles cleanly; when Tor
            // is actually enabled, `openRoundSessionNative`'s Rust side picks real
            // Tor routing (`SessionRoute::Tor`, see round_session.rs) for this
            // session's whole lifetime -- plain HTTP is only the explicit fallback
            // when Tor is disabled/unavailable, never a silent downgrade while it's on.
            // Only TorUnavailableException (Tor disabled) falls back to 0L;
            // TorInitializationErrorException (Tor is ON but failed to bootstrap) must
            // propagate rather than silently deanonymizing this submission.
            val torRuntime =
                try {
                    synchronizer.getVotingTorRuntimeHandle()
                } catch (e: TorUnavailableException) {
                    0L
                }

            val dbHandle = votingCryptoClient.openVotingDb(votingDbPath)
            check(dbHandle != 0L) { "Failed to open voting DB at $votingDbPath" }

            try {
                votingCryptoClient.setWalletId(dbHandle, accountUuidString, networkId)

                val proposals =
                    session.proposals.map { proposal ->
                        VotingProposalRosterEntry(proposalId = proposal.id, numOptions = proposal.options.size)
                    }
                val roundSession =
                    votingCryptoClient.openRoundSession(
                        dbHandle = dbHandle,
                        torRuntime = torRuntime,
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

                    val chpBenchRunStart = System.currentTimeMillis()
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
                    // Per-proposal progress tracker (each proposal's own fraction is the minimum
                    // across that proposal's own bundles, summed across every proposal currently
                    // in flight) -- moves visibly even in a many-proposal round, unlike tracking
                    // only the slowest bundle of a single proposal. Returns null (show an
                    // indeterminate indicator) until real progress exists. See Phase 4 of the
                    // production-completion design doc and VotingRoundProgressTracker's own doc
                    // comment for the Vizor Wallet precedent this mirrors.
                    val progressTracker = VotingRoundProgressTracker()
                    val progressListener =
                        VotingRoundDriveProgressListener { progress ->
                            progress.tally?.let { tally ->
                                lastCompletedProposals =
                                    maxOf(lastCompletedProposals ?: 0, tally.completedProposals)
                                lastTotalProposals =
                                    maxOf(lastTotalProposals ?: 0, tally.totalProposals)
                            }
                            progressTracker.record(
                                progress.step,
                                progress.proofProgress,
                                progress.voteCommitProposalId
                            )
                            progressTracker.recordPlan(progress.voteCarryingBundleIndexes)
                            onProgress(
                                VotingSubmissionProgress.RunningRound(
                                    completedProposals =
                                        progressTracker.estimatedCompletedProposals(
                                            lastCompletedProposals,
                                            lastTotalProposals
                                        ),
                                    totalProposals = lastTotalProposals,
                                    proofProgress =
                                        progressTracker.fraction(lastCompletedProposals, lastTotalProposals)
                                )
                            )
                            // CHP_BENCH — see ChpBenchLog.kt's own note: local-only, never merge.
                            // Not routed through chpBenchLog() itself (that helper's signature is
                            // timing-specific, elapsedMs required); this is the same "CHP_BENCH"
                            // tag so a benchmark logcat capture also proves the round-driver isn't
                            // silently stuck for its ~100-second-plus run.
                            if (BuildConfig.DEBUG) {
                                Log.d(
                                    "CHP_BENCH",
                                    "app=zodl step=progress round=$roundId detail=$progress"
                                )
                            }
                        }
                    val report =
                        runRoundWithBundleFailureRetry(
                            roundId,
                            onRetrying = {
                                onProgress(
                                    VotingSubmissionProgress.RunningRound(
                                        completedProposals =
                                            progressTracker.estimatedCompletedProposals(
                                                lastCompletedProposals,
                                                lastTotalProposals
                                            ),
                                        totalProposals = lastTotalProposals,
                                        proofProgress =
                                            progressTracker.fraction(lastCompletedProposals, lastTotalProposals),
                                        isRetrying = true
                                    )
                                )
                            }
                        ) {
                            roundSession.run(delegationInputs, progressListener)
                        }
                    chpBenchLog("run", roundId, System.currentTimeMillis() - chpBenchRunStart)

                    // Task 4's acceptance criterion: PersistedChainTerminal must surface to the
                    // user immediately, never be silently retried -- unaffected by
                    // runRoundWithBundleFailureRetry above, which only ever re-invokes this call
                    // for the disjoint Failures-with-isolated-bundle-transport-errors case (see
                    // its own doc comment); every other quiescence, PersistedChainTerminal
                    // included, still falls straight through to the mapped error below on the
                    // very first report. Unknown is explicitly NOT treated as success either
                    // (Task 9-lite requirement, a defect the lost plan's own review caught once
                    // already). See
                    // VotingRoundQuiescenceMapper.kt for the full quiescence/failure -> VotingErrors
                    // mapping (Task 12).
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
                    // in the round-driver port (Task 9 of the production-completion plan).
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
                    // withContext(Dispatchers.IO) here (used internally by roundSession.close())
                    // throws immediately instead of running when the parent Job is already
                    // cancelled (e.g. the user backed out mid round-drive) -- NonCancellable lets
                    // the native round session actually get closed instead of leaking its handle.
                    withContext(NonCancellable) {
                        roundSession.close()
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    votingCryptoClient.closeVotingDb(dbHandle)
                }
                chpBenchLog("total", roundId, System.currentTimeMillis() - chpBenchStart)
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
     * ledger (tally-only, no per-bundle-index floor) -- see this task's report for why that
     * inconsistency was left in place rather than ported over.
     */
    @Suppress("LongMethod", "LongParameterList")
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
        // Same Tor policy as the non-Keystone path above: `0L` is the SDK's "no Tor runtime"
        // sentinel, only used when Tor is genuinely disabled; TorInitializationErrorException
        // (Tor is ON but failed to bootstrap) must propagate.
        val torRuntime =
            try {
                synchronizer.getVotingTorRuntimeHandle()
            } catch (e: TorUnavailableException) {
                0L
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
            torRuntime = torRuntime,
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
        val progressTracker = VotingRoundProgressTracker()
        val progressListener =
            VotingRoundDriveProgressListener { progress ->
                progress.tally?.let { tally ->
                    lastCompletedProposals = maxOf(lastCompletedProposals ?: 0, tally.completedProposals)
                    lastTotalProposals = maxOf(lastTotalProposals ?: 0, tally.totalProposals)
                }
                progressTracker.record(progress.step, progress.proofProgress, progress.voteCommitProposalId)
                progressTracker.recordPlan(progress.voteCarryingBundleIndexes)
                onProgress(
                    VotingSubmissionProgress.RunningRound(
                        completedProposals =
                            progressTracker.estimatedCompletedProposals(lastCompletedProposals, lastTotalProposals),
                        totalProposals = lastTotalProposals,
                        proofProgress = progressTracker.fraction(lastCompletedProposals, lastTotalProposals)
                    )
                )
            }

        val report =
            runRoundWithBundleFailureRetry(
                roundId,
                unexpectedResponseMessage = "Keystone round session run() returned no report",
                onRetrying = {
                    onProgress(
                        VotingSubmissionProgress.RunningRound(
                            completedProposals =
                                progressTracker.estimatedCompletedProposals(
                                    lastCompletedProposals,
                                    lastTotalProposals
                                ),
                            totalProposals = lastTotalProposals,
                            proofProgress = progressTracker.fraction(lastCompletedProposals, lastTotalProposals),
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

        // Only close on genuine success -- unlike the non-Keystone path's roundSession (freshly
        // opened and unconditionally closed every call), this session is retained across the
        // Sign/Scan flow and must survive a transient failure here (e.g. a network blip mid
        // chain-submission) so a retry can resume the same session via ensureDelegationPipeline's
        // no-op path above instead of hitting a "no open session" checkNotNull with no way back
        // in (the Sign screen refuses to re-open once every bundle is already signed).
        withContext(NonCancellable) { votingKeystoneSessionHolder.close(roundId) }

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

        return VotingSubmissionResult(submittedProposalCount = report.completedProposals)
    }

    /**
     * Diagnostic-only replacement for the blanket `report.failures.isNotEmpty()` throw that Task
     * 12's quiescence-based mapper removed. A run can quiesce success-shaped
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
     */
    private fun logPartialOutcomeIfAny(
        roundId: String,
        report: VotingRoundRunReport
    ) {
        if (report.failures.isEmpty() && report.skippedBundles.isEmpty()) {
            return
        }
        if (BuildConfig.DEBUG) {
            Log.w(
                "CHP_BENCH",
                "app=zodl step=partial-outcome round=$roundId " +
                    "quiescence=${report.quiescence} completed the run with " +
                    "failures=${report.failures} skippedBundles=${report.skippedBundles} " +
                    "despite a success-shaped quiescence"
            )
        }
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
            if (BuildConfig.DEBUG) {
                Log.w(
                    "CHP_BENCH",
                    "app=zodl step=bundle-failure-retry round=$roundId " +
                        "attempt=$attempt/$MAX_BUNDLE_FAILURE_RETRIES failures=${report.failures}"
                )
            }
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
