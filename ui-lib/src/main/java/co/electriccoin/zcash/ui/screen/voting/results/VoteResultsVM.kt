package co.electriccoin.zcash.ui.screen.voting.results

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.Proposal
import co.electriccoin.zcash.ui.common.model.voting.TallyResults
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.common.model.voting.displayColor
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VoteResultsVM(
    private val args: VoteResultsArgs,
    private val getAllRounds: GetAllVotingRoundsUseCase,
    private val votingApiProvider: VotingApiProvider,
    private val votingApiRepository: VotingApiRepository,
    private val votingRecoveryRepository: VotingRecoveryRepository,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) : ViewModel() {
    private data class ResultsData(
        val round: VotingRound,
        val tally: TallyResults,
        val recovery: VotingRecoverySnapshot?,
    )

    private val resultsLce = mutableLce<ResultsData>()

    init {
        resultsLce.execute {
            val round = getAllRounds().firstOrNull { it.id == args.roundIdHex }
                ?: error("Round ${args.roundIdHex} not found")
            val cachedTally = votingApiRepository.snapshot.value.tallyResultsByRoundId[args.roundIdHex]
            val tally = runCatching {
                votingApiProvider.fetchTallyResults(args.roundIdHex)
            }.onSuccess { results ->
                votingApiRepository.storeTallyResults(args.roundIdHex, results)
            }.getOrElse { throwable ->
                cachedTally ?: throw throwable
            }
            val accountUuid = getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
            val recovery = votingRecoveryRepository.get(accountUuid, args.roundIdHex)

            ResultsData(round = round, tally = tally, recovery = recovery)
        }
    }

    val state =
        resultsLce.state
            .map { lce -> lce.success?.let { buildState(it.round, it.tally, it.recovery) } }
            .withLce(groupLce(resultsLce)) { error ->
                errorStateMapper.mapToState(
                    error = error,
                    title = stringRes(R.string.vote_error_results_unavailable_title),
                    message = stringRes(R.string.vote_error_results_unavailable_message),
                    primaryStyle = ButtonStyle.PRIMARY,
                )
            }.stateIn(this)

    private fun buildState(
        round: VotingRound,
        tally: TallyResults,
        recovery: VotingRecoverySnapshot?,
    ): VoteResultsState {
        val proposals = round.proposals.map { proposal ->
            buildProposalState(proposal, tally)
        }

        return VoteResultsState(
            roundTitle = stringRes(round.title),
            roundDescription = stringRes(round.description),
            votedMetaLine = buildVotedMetaLine(recovery),
            proposals = proposals,
            isLoadingResults = false,
            doneButton = ButtonState(
                text = stringRes(R.string.vote_done),
                style = ButtonStyle.PRIMARY,
                onClick = ::onDone,
            ),
            onBack = ::onBack,
        )
    }

    private fun buildProposalState(
        proposal: Proposal,
        tally: TallyResults,
    ): VoteProposalResultState {
        val tallyProposal = tally.proposals.firstOrNull { it.proposalId == proposal.id }
        val totalWeight = tallyProposal?.options?.sumOf { it.weight } ?: 0L
        val displayWeight = totalWeight.coerceAtLeast(1L).toFloat()
        val maxWeight = tallyProposal?.options?.maxOfOrNull { it.weight } ?: 0L
        val hasVotes = totalWeight > 0L
        val winningOptions = tallyProposal?.options?.filter { it.weight == maxWeight && maxWeight > 0L }.orEmpty()
        val hasTie = winningOptions.size > 1

        val options = proposal.options.mapIndexed { index, option ->
            val weight = tallyProposal?.options?.firstOrNull { it.optionId == option.id }?.weight ?: 0L
            val color = option.displayColor(position = index, total = proposal.options.size)

            VoteOptionResultState(
                label = stringRes(option.label),
                amountZec = stringRes(R.string.vote_results_amount_zec, weight.toZec()),
                fraction = if (hasVotes) weight / displayWeight else 0f,
                color = color,
                isWinner = hasVotes && !hasTie && weight == maxWeight,
            )
        }

        val winner = proposal.options
            .zip(options)
            .firstOrNull { (_, optionState) -> optionState.isWinner }

        return VoteProposalResultState(
            zipNumber = proposal.zipNumber?.let(::stringRes),
            title = stringRes(proposal.title),
            description = stringRes(proposal.description),
            options = options,
            totalZec = stringRes(R.string.vote_results_total_zec, totalWeight.toZec()),
            winnerLabel = when {
                hasTie -> stringRes(R.string.vote_results_tie)
                else -> winner?.first?.label?.let(::stringRes)
            },
            winnerColor = winner?.second?.color ?: VoteOptionDisplayColor.GRAY,
            showWinnerSeal = hasVotes && !hasTie && winner != null,
        )
    }

    private fun buildVotedMetaLine(recovery: VotingRecoverySnapshot?): StringResource? {
        val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        val votedAt = recovery?.submittedAtEpochSeconds?.let(Instant::ofEpochSecond)
        val votedLabel = votedAt?.let { instant ->
            stringRes(R.string.vote_results_voted, formatter.format(instant))
        }
        val votingPowerLabel = recovery?.eligibleWeight?.let { weight ->
            stringRes(R.string.vote_results_voting_power, weight.toZec())
        }
        return when {
            votedLabel != null && votingPowerLabel != null ->
                stringRes(R.string.vote_results_meta_line, votedLabel, votingPowerLabel)

            votedLabel != null -> votedLabel
            else -> votingPowerLabel
        }
    }

    private fun onDone() = navigationRouter.backTo(VoteCoinholderPollingArgs::class)

    private fun onBack() = navigationRouter.backTo(VoteCoinholderPollingArgs::class)
}

private fun Long.toZec(): Double = this / 100_000_000.0
