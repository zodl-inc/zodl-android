package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.error
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.displayColor
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.ExternalUrl
import co.electriccoin.zcash.ui.screen.voting.isDefaultVotingConfig
import co.electriccoin.zcash.ui.screen.voting.polldescription.VotePollDescriptionArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListMode
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsArgs
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingArgs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class VoteProposalDetailVM(
    private val args: VoteProposalDetailArgs,
    votingApiRepository: VotingApiRepository,
    private val configurationRepository: ConfigurationRepository,
    private val votingChainConfigRepository: VotingChainConfigRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val navigationRouter: NavigationRouter,
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
) : ViewModel() {
    private val unansweredSheet = MutableStateFlow<ZashiConfirmationState?>(null)
    private val unverifiedPollWarningSheet = MutableStateFlow<ZashiConfirmationState?>(null)
    private val selectedAccountUuid: Flow<String> =
        observeSelectedWalletAccount
            .require()
            .map { account -> account.sdkAccount.accountUuid.toVotingAccountScopeId() }

    val state: StateFlow<LceState<VoteProposalDetailState>> =
        combine(
            votingApiRepository.snapshot,
            votingSessionStore.state,
            selectedAccountUuid,
            unansweredSheet,
            unverifiedPollWarningSheet,
        ) { apiSnapshot, sessionState, accountUuid, unansweredSheetState, unverifiedSheet ->
            apiSnapshot.rounds
                .firstOrNull { it.id == args.roundId }
                ?.let { round ->
                    createState(
                        round = round,
                        drafts = sessionState.draftVotesFor(accountUuid, args.roundId),
                        accountUuid = accountUuid,
                        unansweredSheetState = unansweredSheetState,
                        unverifiedSheet = unverifiedSheet
                    )
                }
        }.map { content ->
            LceState(
                content = content,
                isLoading = content == null
            )
        }.stateIn(this)

    private fun createState(
        round: VotingRound,
        drafts: Map<Int, Int>,
        accountUuid: String,
        unansweredSheetState: ZashiConfirmationState?,
        unverifiedSheet: ZashiConfirmationState?,
    ): VoteProposalDetailState {
        val proposals = round.proposals
        val requestedIndex = proposals.indexOfFirst { it.id == args.proposalId }
        val proposalIndex = requestedIndex.takeIf { it >= 0 } ?: 0
        val proposal = proposals.getOrElse(proposalIndex) { proposals.first() }
        val position = proposalIndex + 1
        val selectedOptionId = drafts[proposal.id]
        val pollEnded = round.status != SessionStatus.ACTIVE

        return VoteProposalDetailState(
            positionLabel = stringRes(R.string.vote_proposal_position, position, proposals.size),
            title = stringRes(proposal.title),
            description = stringRes(proposal.description),
            forumUrl = proposal.forumUrl,
            options = buildOptions(proposal, selectedOptionId, accountUuid, args.isReadOnly || pollEnded),
            isLocked = args.isReadOnly || pollEnded,
            isEditingFromReview = args.isEditingFromReview,
            isFromList = args.isFromList,
            unansweredSheet = unansweredSheetState?.takeIf { !pollEnded },
            showPollEndedSheet = pollEnded && !args.isReadOnly,
            unverifiedPollWarningSheet = unverifiedSheet,
            onBack = ::onBack,
            onNext = { onNext(proposals, proposalIndex, drafts, accountUuid, round) },
            onViewMore = {
                navigationRouter.forward(
                    VotePollDescriptionArgs(
                        title = proposal.title,
                        description = proposal.description,
                        discussionUrl = proposal.forumUrl
                    )
                )
            },
            onForumClick = {
                proposal.forumUrl?.let { navigationRouter.forward(ExternalUrl(it)) }
            },
            onPollEndedClose = ::onBack,
            onPollEndedViewResults = { onPollEndedViewResults(round) },
        )
    }

    private fun buildOptions(
        proposal: Proposal,
        selectedOptionId: Int?,
        accountUuid: String,
        isReadOnly: Boolean
    ): List<VoteVoteOptionRowState> {
        val options = proposal.options

        return options.map { option ->
            VoteVoteOptionRowState(
                index = option.id,
                label = stringRes(option.label),
                description = option.description?.let { stringRes(it) },
                color = option.displayColor(),
                isSelected = selectedOptionId == option.id,
                isLocked = isReadOnly,
                onSelect = {
                    votingSessionStore.toggleDraftVote(
                        accountUuid = accountUuid,
                        roundId = args.roundId,
                        proposalId = proposal.id,
                        optionId = option.id
                    )
                    persistDraftsForCurrentRound(accountUuid)
                },
            )
        }
    }

    private fun onBack() = navigationRouter.backTo(VoteProposalListArgs::class)

    private fun onNext(
        proposals: List<Proposal>,
        currentIndex: Int,
        drafts: Map<Int, Int>,
        accountUuid: String,
        round: VotingRound,
    ) {
        if (args.isFromList) {
            navigationRouter.backTo(VoteProposalListArgs::class)
            return
        }

        val isLast = currentIndex == proposals.lastIndex
        if (!isLast) {
            val nextProposal = proposals[currentIndex + 1]
            navigationRouter.replace(
                VoteProposalDetailArgs(
                    proposalId = nextProposal.id,
                    roundId = args.roundId,
                    isEditingFromReview = args.isEditingFromReview,
                    isReadOnly = args.isReadOnly,
                )
            )
            return
        }

        val allDrafted = proposals.all { drafts.containsKey(it.id) }
        if (allDrafted) {
            navigationRouter.replace(
                VoteProposalListArgs(
                    roundId = args.roundId,
                    mode = VoteProposalListMode.REVIEW
                )
            )
        } else {
            val unansweredCount = proposals.count { !drafts.containsKey(it.id) }
            unansweredSheet.value =
                if (drafts.isEmpty()) {
                    buildNoChoicesSheet()
                } else {
                    buildUnansweredSheet(unansweredCount, accountUuid, round)
                }
        }
    }

    private fun onConfirmUnanswered(
        accountUuid: String,
        round: VotingRound
    ) {
        unansweredSheet.value = null
        persistDraftsForCurrentRound(accountUuid)
        navigationRouter.replace(
            VoteProposalListArgs(
                roundId = round.id,
                mode = VoteProposalListMode.REVIEW
            )
        )
    }

    /**
     * Mirrors iOS `Self.persistDrafts(...)` calls in `VotingStore+Submission.swift:15,24` and
     * `VotingStore+Navigation.swift:447,492`: every draft mutation snapshots the current
     * draft state to durable storage so a process kill on the proposal list (before the user
     * reaches `VoteConfirmSubmissionVM.persistDraftChoices`) doesn't lose taps.
     *
     * Reads the post-mutation snapshot from the in-memory session store (the toggle/abstain
     * call that preceded this is synchronous), then issues a fire-and-forget write through
     * the recovery repository. Failures are logged and swallowed: the in-memory state stays
     * consistent and the next mutation will retry.
     */
    private fun persistDraftsForCurrentRound(accountUuid: String) {
        if (args.roundId.isEmpty()) return
        val snapshot =
            votingSessionStore.state.value
                .draftVotesFor(accountUuid = accountUuid, roundId = args.roundId)
        viewModelScope.launch {
            try {
                votingRecoveryRepository.storeDraftChoices(
                    accountUuid = accountUuid,
                    roundId = args.roundId,
                    draftChoices = snapshot
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(
                    "VoteProposalDetail",
                    "Failed to persist draft votes for ${args.roundId}",
                    throwable
                )
            }
        }
    }

    private fun navigateToRoundOutcome(round: VotingRound) {
        when (round.status) {
            SessionStatus.TALLYING -> {
                navigationRouter.forward(VoteTallyingArgs(roundIdHex = round.id))
            }

            else -> {
                navigationRouter.forward(VoteResultsArgs(roundIdHex = round.id))
            }
        }
    }

    private fun onPollEndedViewResults(round: VotingRound) {
        if (!isOnDefaultConfig()) {
            unverifiedPollWarningSheet.value = buildUnverifiedPollWarningSheet(round)
            return
        }
        navigateToRoundOutcome(round)
    }

    private fun isOnDefaultConfig(): Boolean =
        isDefaultVotingConfig(
            chainConfig = votingChainConfigRepository.state.value,
            configuration = configurationRepository.configurationFlow.value
        )

    private fun buildUnansweredSheet(
        unansweredCount: Int,
        accountUuid: String,
        round: VotingRound,
    ) = ZashiConfirmationState.error(
        title = stringRes(R.string.vote_proposal_detail_unanswered_title),
        message =
            if (unansweredCount == 1) {
                stringRes(R.string.vote_proposal_detail_unanswered_message_singular)
            } else {
                stringRes(R.string.vote_proposal_detail_unanswered_message_plural, unansweredCount)
            },
        primaryText = stringRes(R.string.vote_confirm_cta),
        secondaryText = stringRes(R.string.vote_proposal_detail_unanswered_go_back),
        onPrimary = { onConfirmUnanswered(accountUuid, round) },
        onSecondary = { unansweredSheet.value = null },
        onBack = { unansweredSheet.value = null },
    )

    private fun buildNoChoicesSheet() =
        ZashiConfirmationState.error(
            title = stringRes(R.string.vote_proposal_detail_unanswered_title),
            message = stringRes(R.string.vote_proposal_detail_no_choices_message),
            primaryText = stringRes(R.string.vote_dismiss),
            secondaryText = null,
            onPrimary = { unansweredSheet.value = null },
            onBack = { unansweredSheet.value = null },
        )

    private fun buildUnverifiedPollWarningSheet(round: VotingRound) =
        ZashiConfirmationState(
            icon = R.drawable.ic_alert_circle,
            title = stringRes(R.string.vote_unverified_poll_title),
            message = stringRes(R.string.vote_unverified_poll_message),
            primaryAction =
                ButtonState(
                    text = stringRes(R.string.vote_error_go_back),
                    style = ButtonStyle.PRIMARY,
                    onClick = ::dismissUnverifiedPollWarning
                ),
            secondaryAction =
                ButtonState(
                    text = stringRes(R.string.vote_proceed_anyway),
                    style = ButtonStyle.SECONDARY,
                    onClick = { proceedFromUnverifiedPollWarning(round) }
                ),
            onBack = ::dismissUnverifiedPollWarning,
            style = ZashiConfirmationStyle.UNVERIFIED_POLL_WARNING
        )

    private fun proceedFromUnverifiedPollWarning(round: VotingRound) {
        unverifiedPollWarningSheet.value = null
        navigateToRoundOutcome(round)
    }

    private fun dismissUnverifiedPollWarning() {
        unverifiedPollWarningSheet.value = null
    }
}
