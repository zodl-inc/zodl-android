package co.electriccoin.zcash.voting

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingSession
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingKeystoneRouteStage
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.RefreshActiveVotingSessionUseCase
import co.electriccoin.zcash.ui.common.voting.VotingHomeHooks
import co.electriccoin.zcash.ui.common.voting.VotingHomeMessageSource
import co.electriccoin.zcash.ui.common.voting.VotingNavContributor
import co.electriccoin.zcash.ui.common.voting.VotingSettingsEntry
import co.electriccoin.zcash.ui.dialogComposable
import co.electriccoin.zcash.ui.screen.voting.chainconfig.VoteChainConfigArgs
import co.electriccoin.zcash.ui.screen.voting.chainconfig.VoteChainConfigScreen
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingScreen
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionScreen
import co.electriccoin.zcash.ui.screen.voting.howtovote.VoteHowToVoteArgs
import co.electriccoin.zcash.ui.screen.voting.howtovote.VoteHowToVoteScreen
import co.electriccoin.zcash.ui.screen.voting.polldescription.VotePollDescriptionArgs
import co.electriccoin.zcash.ui.screen.voting.polldescription.VotePollDescriptionScreen
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.VoteProposalDetailArgs
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.VoteProposalDetailScreen
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListMode
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListScreen
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsArgs
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsScreen
import co.electriccoin.zcash.ui.screen.voting.scankeystone.ScanKeystoneVotingPCZTRequest
import co.electriccoin.zcash.ui.screen.voting.scankeystone.WrapScanKeystoneVotingPCZTRequest
import co.electriccoin.zcash.ui.screen.voting.signkeystone.SignKeystoneVotingArgs
import co.electriccoin.zcash.ui.screen.voting.signkeystone.SignKeystoneVotingScreen
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingArgs
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingScreen
import co.electriccoin.zcash.work.VotingShareTrackingScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import java.time.Instant

/**
 * Master kill switch for coinholder (shielded) voting.
 *
 * While `false`, the voting entry point in `MoreVM` is suppressed (via [VotingSettingsEntry]) and
 * the pending-session recovery in `HomeVM` bails out (via [VotingHomeHooks]), leaving the voting
 * UI unreachable even though its screens and routes remain registered. Re-enabled on the chp
 * worktree: the SDK now builds with `cfg(zcash_voting)` (see backend-lib/build.gradle.kts and
 * Cargo.toml on that worktree). Voting config falls back to a bundled pinned production URL
 * (`StaticVotingConfig.BUNDLED_PINNED_SOURCE`) when remote config's `voting_config_url` is unset,
 * so no remote config is required to try this.
 */
const val VOTING_ENABLED = true

/**
 * Temporarily disables the Coinholder Polling home-widget prompt (MOB-1805, PR #2472) while its
 * eligibility check and the unconditional voting-session network refresh it rides along with are
 * redesigned - see MOB-1814 and the Slack thread linked there. This does NOT touch
 * [VOTING_ENABLED] or the underlying [RefreshActiveVotingSessionUseCase] call in
 * [VotingHomeHooksImpl.recoverPendingRouteIfNeeded] - voting itself, and that pre-existing
 * refresh, are unaffected. Re-enable by flipping this back to `true` once the redesign lands.
 */
const val COINHOLDER_HOME_PROMPT_ENABLED = false

class VotingHomeHooksImpl(
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingApiRepository: VotingApiRepository,
    private val votingSessionStore: VotingSessionStore,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val refreshActiveVotingSession: RefreshActiveVotingSessionUseCase,
    private val votingShareTrackingScheduler: VotingShareTrackingScheduler,
    private val navigationRouter: NavigationRouter,
) : VotingHomeHooks {
    @Suppress("ReturnCount", "SpreadOperator")
    override suspend fun recoverPendingRouteIfNeeded(): Boolean {
        if (!VOTING_ENABLED) return false
        val accountUuid = getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()

        // Purely local check - most home-screen opens have no pending Keystone request at all,
        // so skip the network refresh entirely rather than fetching service config + all rounds
        // on every open just to find nothing to recover (MOB-1814).
        val pendingRoundIds = votingRecoveryRepository.getRoundIdsWithPendingKeystoneRequest(accountUuid)
        if (pendingRoundIds.isEmpty()) return false

        runCatching {
            refreshActiveVotingSession()
        }.getOrElse {
            return false
        }
        val activeRoundIds = votingApiRepository.snapshot.value.sessionsByRoundId.keys
        val recovery =
            pendingRoundIds
                .filter { roundId -> roundId in activeRoundIds }
                .firstNotNullOfOrNull { roundId ->
                    votingRecoveryRepository.get(accountUuid, roundId)?.takeIf { it.pendingKeystoneRequest != null }
                }
        recovery ?: return false
        val roundId = recovery.roundId
        val pendingRequest = recovery.pendingKeystoneRequest ?: return false
        val draftChoices =
            recovery.draftChoices
                .ifEmpty { recovery.proposalSelections.mapValues { (_, selection) -> selection.choiceId } }
        if (draftChoices.isEmpty()) {
            return false
        }

        votingApiRepository.snapshot.value.sessionsByRoundId[roundId]
            ?.toVotingRound()
            ?.let(votingApiRepository::upsertRound)
        votingSessionStore.restoreDraftVotes(accountUuid, roundId, draftChoices)

        val restoredRoutes =
            buildList {
                add(VoteProposalListArgs(roundId = roundId, mode = VoteProposalListMode.REVIEW))
                add(
                    VoteConfirmSubmissionArgs(
                        roundIdHex = roundId,
                        choicesJson = draftChoices.toChoicesJson()
                    )
                )
                add(SignKeystoneVotingArgs(roundIdHex = roundId))
                if (pendingRequest.routeStage == VotingKeystoneRouteStage.SCAN) {
                    add(
                        ScanKeystoneVotingPCZTRequest(
                            roundIdHex = roundId,
                            bundleIndex = pendingRequest.bundleIndex,
                            actionIndex = pendingRequest.actionIndex
                        )
                    )
                }
            }

        navigationRouter.replaceAll(*restoredRoutes.toTypedArray())
        return true
    }

    /**
     * Re-enqueue share-tracking workers for any rounds the wallet finished submitting in a prior
     * launch. iOS triggers the equivalent on `governanceTabAppeared`; on Android the WorkManager
     * worker outlives the process, but if the OS killed the app between `storeVoteTxHash` and
     * the scheduler call in `SubmitVotesUseCase`, no worker was ever enqueued. Scheduling here
     * uses `ExistingWorkPolicy.REPLACE`, so re-enqueueing an active worker is a no-op, and
     * `TrackVotingSharesUseCase` short-circuits when no unconfirmed shares remain.
     *
     * Scoped to the currently selected account, mirroring the per-account pattern used by
     * [recoverPendingRouteIfNeeded] above.
     */
    override suspend fun resumePendingShareTracking() {
        if (!VOTING_ENABLED) return
        val accountUuid =
            runCatching {
                getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
            }.getOrNull() ?: return
        val pendingRoundIds =
            runCatching {
                votingRecoveryRepository.getRoundIdsRequiringShareTracking(accountUuid)
            }.getOrDefault(emptyList())
        pendingRoundIds.forEach { roundId ->
            votingShareTrackingScheduler.schedule(roundId)
        }
    }
}

class VotingSettingsEntryImpl(
    private val navigationRouter: NavigationRouter,
) : VotingSettingsEntry {
    override val isEnabled: Boolean = VOTING_ENABLED

    override fun navigateToHowToVote() = navigationRouter.forward(VoteHowToVoteArgs)

    override fun navigateToCoinholderPolling() = navigationRouter.forward(VoteCoinholderPollingArgs)
}

/**
 * See [VotingHomeMessageSource] for the eligibility contract this implements. Combines the
 * currently selected account, the latest fetched rounds/sessions ([VotingApiRepository]) and this
 * device's submission record to decide whether the Coinholder Polling home-widget prompt
 * (MOB-1805) should currently be eligible to show.
 *
 * Submission state is read from [VotingSessionStore] first (in-memory, populated the moment a
 * submit completes this process) falling back to the durable [VotingRecoveryRepository] (survives
 * process death/restart) — the same two-source fallback [VoteCoinholderPollingVM] already uses,
 * needed because [VotingSessionStore] starts empty on every fresh launch.
 */
class VotingHomeMessageSourceImpl(
    private val votingApiRepository: VotingApiRepository,
    private val votingSessionStore: VotingSessionStore,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) : VotingHomeMessageSource {
    private data class ActiveScope(
        val accountUuid: String,
        val roundId: String
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeIsCoinholderPollingMessageVisible(): Flow<Boolean> {
        if (!VOTING_ENABLED || !COINHOLDER_HOME_PROMPT_ENABLED) return flowOf(false)

        val activeScope: Flow<ActiveScope?> =
            combine(
                votingApiRepository.snapshot,
                getSelectedWalletAccount.observe(),
            ) { apiSnapshot, account ->
                val accountUuid = account?.sdkAccount?.accountUuid?.toVotingAccountScopeId()
                val activeEntry =
                    apiSnapshot.sessionsByRoundId.entries.firstOrNull { entry ->
                        entry.value.status == SessionStatus.ACTIVE
                    }

                when {
                    accountUuid == null || activeEntry == null -> null
                    !Instant.now().isBefore(activeEntry.value.voteEndTime) -> null
                    else -> ActiveScope(accountUuid, activeEntry.key)
                }
            }.distinctUntilChanged()

        return activeScope
            .flatMapLatest { scope ->
                if (scope == null) {
                    flowOf(false)
                } else {
                    val roundId = scope.roundId
                    combine(
                        votingSessionStore.state,
                        votingRecoveryRepository.observe(scope.accountUuid, roundId),
                    ) { sessionStoreState, recovery ->
                        val inMemoryCount = sessionStoreState.submittedProposalCount(scope.accountUuid, roundId)
                        val hasVoted = inMemoryCount != null || recovery?.submittedAtEpochSeconds != null

                        !hasVoted
                    }
                }
            }.distinctUntilChanged()
    }
}

class VotingNavContributorImpl : VotingNavContributor {
    override fun contribute(navGraphBuilder: NavGraphBuilder) {
        with(navGraphBuilder) {
            composable<VoteHowToVoteArgs> { VoteHowToVoteScreen() }
            composable<VoteCoinholderPollingArgs> { VoteCoinholderPollingScreen() }
            composable<VoteChainConfigArgs> { VoteChainConfigScreen() }
            composable<VoteProposalListArgs> { VoteProposalListScreen(it.toRoute()) }
            composable<VoteProposalDetailArgs> { VoteProposalDetailScreen(it.toRoute()) }
            dialogComposable<VotePollDescriptionArgs> { VotePollDescriptionScreen(it.toRoute()) }
            composable<VoteConfirmSubmissionArgs> { VoteConfirmSubmissionScreen(it.toRoute()) }
            composable<VoteTallyingArgs> { VoteTallyingScreen(it.toRoute()) }
            composable<VoteResultsArgs> { VoteResultsScreen(it.toRoute()) }
            composable<ScanKeystoneVotingPCZTRequest> { WrapScanKeystoneVotingPCZTRequest(it.toRoute()) }
            composable<SignKeystoneVotingArgs> { SignKeystoneVotingScreen(it.toRoute()) }
        }
    }
}

private fun VotingSession.toVotingRound() =
    VotingRound(
        id = voteRoundId.toLowerHex(),
        title = title,
        description = description,
        discussionUrl = discussionUrl,
        createdAtHeight = createdAtHeight,
        snapshotHeight = snapshotHeight,
        snapshotDate = ceremonyStart.takeIf { it.epochSecond > 0 } ?: voteEndTime,
        votingStart = ceremonyStart,
        votingEnd = voteEndTime,
        proposals = proposals,
        status = status
    )

private const val BYTE_MASK = 0xff

private fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

private fun Map<Int, Int>.toChoicesJson(): String =
    JSONObject(toSortedMap().mapKeys { (proposalId, _) -> proposalId.toString() }).toString()
