package co.electriccoin.zcash.ui.screen.voting.proposallist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceError
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VotingRoundPreparationResult
import co.electriccoin.zcash.ui.common.model.voting.voteBadgeInfo
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.effectiveChoices
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.VotingSessionStore
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.PrepareVotingRoundUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.ineligible.VoteIneligibilityReason
import co.electriccoin.zcash.ui.screen.voting.ineligible.VoteIneligibleArgs
import co.electriccoin.zcash.ui.screen.voting.polldescription.VotePollDescriptionArgs
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.VoteProposalDetailArgs
import co.electriccoin.zcash.ui.screen.voting.votingerror.VotingErrorMapper
import co.electriccoin.zcash.ui.screen.voting.walletsyncing.VoteWalletSyncingArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoteProposalListVM(
    private val args: VoteProposalListArgs,
    private val votingApiRepository: VotingApiRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val votingSessionStore: VotingSessionStore,
    private val prepareVotingRound: PrepareVotingRoundUseCase,
    private val navigationRouter: NavigationRouter,
    observeSelectedWalletAccount: ObserveSelectedWalletAccountUseCase,
) : ViewModel() {
    private val preparationErrorSheet = MutableStateFlow<ZashiConfirmationState?>(null)
    private var preparationJob: Job? = null

    init {
        prepareForVoting()
    }

    private val selectedAccountUuid: Flow<String> =
        observeSelectedWalletAccount.require()
            .map { account -> account.sdkAccount.accountUuid.toVotingAccountScopeId() }

    private val recoveryFlow: Flow<VotingRecoverySnapshot?> =
        selectedAccountUuid.flatMapLatest { accountUuid ->
            if (args.roundId.isEmpty()) {
                flowOf(null)
            } else {
                votingRecoveryRepository.observe(accountUuid, args.roundId)
            }
        }

    val state: StateFlow<LceState<VoteProposalListState>> =
        combine(
            votingApiRepository.snapshot,
            votingSessionStore.state,
            selectedAccountUuid,
            recoveryFlow,
        ) { apiSnapshot, sessionState, accountUuid, recovery ->
            resolveRound(apiSnapshot.rounds)
                ?.let { round ->
                    createState(
                        round = round,
                        drafts = sessionState.draftVotesFor(accountUuid, round.id),
                        recovery = recovery
                    )
                }
        }.let { contentFlow ->
            combine(contentFlow, preparationErrorSheet) { content, errorSheet ->
                content to errorSheet
            }
        }.map { (content, errorSheet) ->
            LceState(
                content = content,
                isLoading = content == null,
                error = errorSheet?.let(LceError::BottomSheet)
            )
        }.stateIn(
            viewModel = this,
            initialValue = LceState(content = null, isLoading = true)
        )

    private fun prepareForVoting() {
        if (args.roundId.isEmpty() || args.mode != VoteProposalListMode.VOTING) {
            return
        }
        if (preparationJob?.isActive == true) {
            return
        }

        preparationErrorSheet.value = null
        preparationJob = viewModelScope.launch {
            runCatching {
                prepareVotingRound(args.roundId)
            }.onSuccess { preparation ->
                when (preparation) {
                    is VotingRoundPreparationResult.WalletSyncing ->
                        navigationRouter.forward(VoteWalletSyncingArgs(roundId = args.roundId))

                    is VotingRoundPreparationResult.Ineligible ->
                        navigationRouter.forward(
                            VoteIneligibleArgs(
                                reason = preparation.toIneligibilityReason(),
                                snapshotHeight = snapshotHeightFor(args.roundId),
                                eligibleWeightZatoshi = preparation.eligibleWeight
                            )
                        )

                    else -> Unit
                }
            }.onFailure { throwable ->
                Log.e("VoteProposalList", "Failed to prepare voting round ${args.roundId}", throwable)
                preparationErrorSheet.value = buildPreparationErrorSheet(throwable)
            }
        }
    }

    private fun buildPreparationErrorSheet(throwable: Throwable): ZashiConfirmationState {
        val rawMessage = throwable.message.orEmpty()
        return ZashiConfirmationState(
            icon = R.drawable.ic_reset_zashi_warning,
            title = stringRes(R.string.vote_error_voting_unavailable_title),
            message = rawMessage
                .takeIf { it.isNotBlank() }
                ?.let(VotingErrorMapper::toUserFriendlyMessage)
                ?: stringRes(R.string.vote_error_voting_unavailable_message),
            primaryAction = ButtonState(
                text = stringRes(R.string.vote_try_again),
                style = ButtonStyle.PRIMARY,
                onClick = {
                    preparationErrorSheet.value = null
                    prepareForVoting()
                }
            ),
            secondaryAction = ButtonState(
                text = stringRes(R.string.vote_error_go_back),
                style = ButtonStyle.TERTIARY,
                onClick = ::goBackFromPreparationErrorSheet
            ),
            onBack = ::dismissPreparationErrorSheet
        )
    }

    private fun dismissPreparationErrorSheet() {
        preparationErrorSheet.value = null
    }

    private fun goBackFromPreparationErrorSheet() {
        dismissPreparationErrorSheet()
        onBack()
    }

    private fun resolveRound(rounds: List<VotingRound>): VotingRound? {
        val targetRoundId = args.roundId
        return when {
            targetRoundId.isNotEmpty() -> rounds.firstOrNull { it.id == targetRoundId }
            else -> rounds.firstOrNull { it.status == SessionStatus.ACTIVE }
        }
    }

    private fun createState(
        round: VotingRound,
        drafts: Map<Int, Int>,
        recovery: VotingRecoverySnapshot?
    ): VoteProposalListState {
        val mode = args.mode
        val proposals = round.proposals
        val displayedChoices = when (mode) {
            VoteProposalListMode.VOTED -> recovery?.effectiveChoices(proposals, drafts) ?: drafts

            else -> drafts
        }
        val votedCount = proposals.count { displayedChoices.containsKey(it.id) }

        return VoteProposalListState(
            mode = mode,
            roundTitle = stringRes(round.title),
            snapshotHeight = round.snapshotHeight.takeIf { it > 0 },
            votedCount = votedCount,
            totalCount = proposals.size,
            metaLine = when (mode) {
                VoteProposalListMode.VOTING -> buildMetaLine(round, recovery)
                VoteProposalListMode.VOTED -> buildVotedMetaLine(round, recovery)
                VoteProposalListMode.REVIEW -> null
            },
            description = round.description.takeIf { it.isNotEmpty() }?.let(::stringRes),
            discussionUrl = round.discussionUrl,
            onViewMore =
                round.description.takeIf { it.isNotEmpty() }?.let { description ->
                    {
                        navigationRouter.forward(
                            VotePollDescriptionArgs(
                                title = round.title,
                                description = description,
                                discussionUrl = round.discussionUrl,
                            )
                        )
                    }
                },
            proposals = proposals.map { buildProposalRow(it, displayedChoices, round.id) },
            ctaButton = buildCtaButton(mode, proposals, displayedChoices, round.id),
            onBack = ::onBack,
        )
    }

    private fun buildProposalRow(
        proposal: Proposal,
        drafts: Map<Int, Int>,
        roundId: String
    ): VoteProposalRowState {
        val draftOptionId = drafts[proposal.id]
        val badge = draftOptionId?.let { buildVoteBadge(proposal, it) }

        return VoteProposalRowState(
            id = proposal.id,
            zipNumber = proposal.zipNumber?.let(::stringRes),
            title = stringRes(proposal.title),
            description = stringRes(proposal.description),
            voteBadge = badge,
            onClick = { onProposalTapped(roundId, proposal.id) },
        )
    }

    private fun buildVoteBadge(
        proposal: Proposal,
        optionId: Int
    ): VoteVoteBadgeState {
        val badgeInfo = proposal.voteBadgeInfo(optionId)

        return VoteVoteBadgeState(
            label = stringRes(badgeInfo.label),
            color = badgeInfo.color
        )
    }

    private fun buildCtaButton(
        mode: VoteProposalListMode,
        proposals: List<Proposal>,
        drafts: Map<Int, Int>,
        roundId: String,
    ): ButtonState? {
        if (mode == VoteProposalListMode.VOTED) {
            return null
        }

        if (proposals.isEmpty()) {
            return null
        }

        if (mode == VoteProposalListMode.REVIEW) {
            val allDrafted = proposals.all { drafts.containsKey(it.id) }
            return if (allDrafted) {
                ButtonState(
                    text = stringRes(R.string.vote_proposal_list_confirm_submit),
                    style = ButtonStyle.PRIMARY,
                    onClick = {
                        navigationRouter.forward(
                            VoteConfirmSubmissionArgs(
                                roundIdHex = roundId,
                                choicesJson = drafts.toChoicesJson()
                            )
                        )
                    }
                )
            } else {
                null
            }
        }

        val draftCount = proposals.count { drafts.containsKey(it.id) }
        val firstUnanswered = proposals.firstOrNull { !drafts.containsKey(it.id) }

        return when {
            draftCount == 0 -> ButtonState(
                text = stringRes(R.string.vote_proposal_list_start_voting),
                style = ButtonStyle.PRIMARY,
                onClick = { onProposalTapped(roundId, proposals.first().id) }
            )

            draftCount < proposals.size -> ButtonState(
                text = stringRes(R.string.vote_proposal_list_continue_voting),
                style = ButtonStyle.PRIMARY,
                onClick = { firstUnanswered?.let { onProposalTapped(roundId, it.id) } }
            )

            else -> ButtonState(
                text = stringRes(R.string.vote_proposal_list_review_submit),
                style = ButtonStyle.PRIMARY,
                onClick = {
                    navigationRouter.forward(
                        VoteProposalListArgs(
                            roundId = roundId,
                            mode = VoteProposalListMode.REVIEW
                        )
                    )
                }
            )
        }
    }

    private fun buildMetaLine(
        round: VotingRound,
        recovery: VotingRecoverySnapshot?
    ): VoteProposalMetaLineState {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())
        val dateLabel = stringRes(R.string.vote_proposal_list_ends, formatter.format(round.votingEnd))
        val votingPowerLabel = recovery?.eligibleWeight?.let { weight ->
            stringRes(R.string.vote_proposal_list_voting_power, weight.toVotingWeightLabel())
        }

        return VoteProposalMetaLineState(
            leading = combineMetaLine(dateLabel, votingPowerLabel),
            trailing = buildTimeLeftLabel(round)
        )
    }

    private fun buildVotedMetaLine(
        round: VotingRound,
        recovery: VotingRecoverySnapshot?
    ): VoteProposalMetaLineState? {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())
        val votedAt = recovery?.submittedAtEpochSeconds?.let(Instant::ofEpochSecond)
        val votedLabel = votedAt?.let { instant ->
            stringRes(R.string.vote_proposal_list_voted, formatter.format(instant))
        }
        val votingPowerLabel = recovery?.eligibleWeight?.let { weight ->
            stringRes(R.string.vote_proposal_list_voting_power, weight.toVotingWeightLabel())
        }
        val dateLabel = votedLabel ?: stringRes(R.string.vote_proposal_list_ends, formatter.format(round.votingEnd))
        val trailing = buildTimeLeftLabel(round)

        return VoteProposalMetaLineState(
            leading = combineMetaLine(dateLabel, votingPowerLabel),
            trailing = trailing
        )
    }

    private fun combineMetaLine(
        dateLabel: StringResource,
        votingPowerLabel: StringResource?,
    ): StringResource =
        if (votingPowerLabel == null) {
            dateLabel
        } else {
            stringRes(R.string.vote_proposal_list_meta_line, dateLabel, votingPowerLabel)
        }

    private fun buildTimeLeftLabel(round: VotingRound): StringResource {
        val remaining = ChronoUnit.SECONDS.between(Instant.now(), round.votingEnd)
        return when {
            remaining <= 0 -> stringRes(R.string.vote_proposal_list_time_ended)
            remaining < 3600 -> stringRes(R.string.vote_proposal_list_time_minutes_left, remaining / 60)
            remaining < 86400 -> stringRes(R.string.vote_proposal_list_time_hours_left, remaining / 3600)
            remaining < 172800 -> stringRes(R.string.vote_proposal_list_time_day_left, remaining / 86400)
            else -> stringRes(R.string.vote_proposal_list_time_days_left, remaining / 86400)
        }
    }

    private fun onProposalTapped(
        roundId: String,
        proposalId: Int
    ) {
        if (roundId.isEmpty()) return

        navigationRouter.forward(
            VoteProposalDetailArgs(
                proposalId = proposalId,
                roundId = roundId,
                isEditingFromReview = args.mode == VoteProposalListMode.REVIEW,
                isReadOnly = args.mode == VoteProposalListMode.VOTED,
            )
        )
    }

    private fun onBack() {
        when (args.mode) {
            VoteProposalListMode.VOTED -> navigationRouter.backTo(VoteCoinholderPollingArgs::class)
            else -> navigationRouter.back()
        }
    }

    private fun snapshotHeightFor(roundId: String): Long =
        votingApiRepository.snapshot.value.rounds
            .firstOrNull { it.id == roundId }
            ?.snapshotHeight
            ?: 0L
}

private fun Map<Int, Int>.toChoicesJson(): String =
    JSONObject(toSortedMap().mapKeys { (proposalId, _) -> proposalId.toString() }).toString()

private fun Long.toVotingWeightLabel() = "%.4f ZEC".format(this / 100_000_000.0)

private fun VotingRoundPreparationResult.Ineligible.toIneligibilityReason(): VoteIneligibilityReason =
    if (bundleCount <= 0) {
        VoteIneligibilityReason.NO_NOTES
    } else {
        VoteIneligibilityReason.BALANCE_TOO_LOW
    }
