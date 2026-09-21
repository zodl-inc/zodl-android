package co.electriccoin.zcash.ui.common.usecase

import android.util.Log
import cash.z.ecc.android.sdk.exception.TorUnavailableException
import cash.z.ecc.android.sdk.ext.toHex
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.voting.VotingBallotIntent
import cash.z.ecc.android.sdk.model.voting.VotingDelegationInputs
import cash.z.ecc.android.sdk.model.voting.VotingProposalRosterEntry
import cash.z.ecc.android.sdk.model.voting.VotingRoundDriveProgressListener
import cash.z.ecc.android.sdk.model.voting.VotingRoundQuiescence
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
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.toCanonicalUuidString
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * voting-5.0.0 round-driver port note: no longer thrown by this file (Keystone signing, the old
 * source of protocol-auth failures, is deferred per Task 7) — kept only because
 * `VoteConfirmSubmissionVM` still pattern-matches on this type for a specific UI status. Revisit
 * when Keystone signing is re-ported.
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
 * Scope cut for this pass (see the port plan's "Scope cut" section): Keystone accounts are
 * rejected outright (Task 7 deferred); there is no persisted recovery snapshot — resuming a round
 * means calling this again, which re-derives everything from the round's own on-disk/on-chain
 * state via [VotingRoundSession.run] rather than a local state machine (Task 8 default); errors
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
            if (selectedAccount is KeystoneAccount) {
                throw VotingSubmissionRecoverableException(VotingErrors.KeystoneNotSupported)
            }
            val accountUuidString = selectedAccount.sdkAccount.accountUuid.toVotingAccountScopeId()

            when (val preparation = prepareVotingRound(roundId)) {
                is VotingRoundPreparationResult.Ready -> Unit
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
                    roundSession.setBallotIntents(
                        choices.map { (proposalId, choiceId) -> VotingBallotIntent(proposalId, choiceId) }
                    )

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
                    val progressListener =
                        VotingRoundDriveProgressListener { progress ->
                            progress.tally?.let { tally ->
                                lastCompletedProposals =
                                    maxOf(lastCompletedProposals ?: 0, tally.completedProposals)
                                lastTotalProposals =
                                    maxOf(lastTotalProposals ?: 0, tally.totalProposals)
                            }
                            onProgress(
                                VotingSubmissionProgress.RunningRound(
                                    completedProposals = lastCompletedProposals,
                                    totalProposals = lastTotalProposals,
                                    proofProgress = progress.proofProgress
                                )
                            )
                            // CHP_BENCH — see ChpBenchLog.kt's own note: local-only, never merge.
                            // Not routed through chpBenchLog() itself (that helper's signature is
                            // timing-specific, elapsedMs required); this is the same "CHP_BENCH"
                            // tag so a benchmark logcat capture also proves the round-driver isn't
                            // silently stuck for its ~100-second-plus run.
                            Log.d(
                                "CHP_BENCH",
                                "app=zodl step=progress round=$roundId detail=$progress"
                            )
                        }
                    val report =
                        roundSession.run(delegationInputs, progressListener)
                            ?: throw VotingSubmissionRecoverableException(
                                VotingErrors.UnexpectedSdkResponse("Round session run() returned no report")
                            )
                    chpBenchLog("run", roundId, System.currentTimeMillis() - chpBenchRunStart)

                    if (report.failures.isNotEmpty()) {
                        throw VotingSubmissionRecoverableException(
                            VotingErrors.UnexpectedSdkResponse(
                                "Voting round $roundId run() reported ${report.failures.size} failure(s): " +
                                    report.failures.joinToString(separator = "; ") { failure -> failure.message }
                            )
                        )
                    }

                    // Task 4's acceptance criterion: PersistedChainTerminal must surface to the
                    // user immediately, never be silently retried. This single-shot call never
                    // retries anything on its own, so that criterion is satisfied structurally —
                    // but it must still fall through to the generic failure below, not be
                    // mistaken for success. Unknown is explicitly NOT treated as success either
                    // (Task 9-lite requirement, a defect the lost plan's own review caught once
                    // already).
                    when (report.quiescence) {
                        is VotingRoundQuiescence.NoWorkLeft,
                        is VotingRoundQuiescence.BackgroundShareWorkOnly -> Unit
                        else ->
                            throw VotingSubmissionRecoverableException(
                                VotingErrors.UnexpectedSdkResponse(
                                    "Voting round $roundId did not reach quiescence: ${report.quiescence}"
                                )
                            )
                    }

                    // Schedules VotingShareTrackingWorker unconditionally on success (matches the
                    // pre-parking-commit round-driver implementation of this call, which this
                    // rewrite lost -- see git history for the exact prior call site). Safe to
                    // call even when quiescence was NoWorkLeft: TrackVotingSharesUseCase's own
                    // first pass short-circuits immediately when no unconfirmed shares remain.
                    votingShareTrackingScheduler.schedule(roundId)

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
}

private fun ZcashNetwork.toVotingNetworkId() = if (isMainnet()) 1 else 0

private const val MILLIS_PER_SECOND = 1000L
