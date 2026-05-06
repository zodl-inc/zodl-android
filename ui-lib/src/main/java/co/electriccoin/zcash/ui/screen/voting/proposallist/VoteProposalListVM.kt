package co.electriccoin.zcash.ui.screen.voting.proposallist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
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
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.ineligible.VoteIneligibilityReason
import co.electriccoin.zcash.ui.screen.voting.ineligible.VoteIneligibleArgs
import co.electriccoin.zcash.ui.screen.voting.polldescription.VotePollDescriptionArgs
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.VoteProposalDetailArgs
import co.electriccoin.zcash.ui.screen.voting.votingerror.VoteErrorArgs
import co.electriccoin.zcash.ui.screen.voting.walletsyncing.VoteWalletSyncingArgs
import kotlinx.coroutines.flow.Flow
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
    init {
        if (args.roundId.isNotEmpty() && args.mode == VoteProposalListMode.VOTING) {
            viewModelScope.launch {
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
                    navigationRouter.forward(
                        VoteErrorArgs(
                            message = throwable.message ?: "Unable to prepare this voting round.",
                            isRecoverable = true
                        )
                    )
                }
            }
        }
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
        }.map { content ->
            LceState(
                content = content,
                isLoading = content == null
            )
        }.stateIn(
            viewModel = this,
            initialValue = LceState(content = null, isLoading = true)
        )

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
        val dateStr = "Ends ${formatter.format(round.votingEnd)}"
        val votingPowerLabel = recovery?.eligibleWeight?.let { weight -> "Voting Power ${weight.toVotingWeightLabel()}" }
        val leading = listOfNotNull(dateStr, votingPowerLabel).joinToString("  ·  ")

        return VoteProposalMetaLineState(
            leading = stringRes(leading),
            trailing = stringRes(buildTimeLeftLabel(round))
        )
    }

    private fun buildVotedMetaLine(
        round: VotingRound,
        recovery: VotingRecoverySnapshot?
    ): VoteProposalMetaLineState? {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())
        val votedAt = recovery?.submittedAtEpochSeconds?.let(Instant::ofEpochSecond)
        val votedLabel = votedAt?.let { instant -> "Voted ${formatter.format(instant)}" }
        val votingPowerLabel = recovery?.eligibleWeight?.let { weight -> "Voting Power ${weight.toVotingWeightLabel()}" }
        val dateLabel = votedLabel ?: "Ends ${formatter.format(round.votingEnd)}"
        val leading = listOfNotNull(dateLabel, votingPowerLabel).joinToString("  ·  ")
        val trailing = buildTimeLeftLabel(round)

        return VoteProposalMetaLineState(
            leading = stringRes(leading),
            trailing = stringRes(trailing)
        )
    }

    private fun buildTimeLeftLabel(round: VotingRound): String {
        val remaining = ChronoUnit.SECONDS.between(Instant.now(), round.votingEnd)
        return when {
            remaining <= 0 -> "Ended"
            remaining < 3600 -> "${remaining / 60}m left"
            remaining < 86400 -> "${remaining / 3600}h left"
            else -> "${remaining / 86400} day${if (remaining / 86400 == 1L) "" else "s"} left"
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
