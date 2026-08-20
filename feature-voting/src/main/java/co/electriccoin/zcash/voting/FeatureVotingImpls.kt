package co.electriccoin.zcash.voting

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import co.electriccoin.zcash.ui.NavigationRouter
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
import org.json.JSONObject

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
        runCatching {
            refreshActiveVotingSession()
        }.getOrElse {
            return false
        }
        val accountUuid = getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
        var recovery: VotingRecoverySnapshot? = null
        for (roundId in votingApiRepository.snapshot.value.sessionsByRoundId.keys) {
            val candidate = votingRecoveryRepository.get(accountUuid, roundId)
            if (candidate?.pendingKeystoneRequest != null) {
                recovery = candidate
                break
            }
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
