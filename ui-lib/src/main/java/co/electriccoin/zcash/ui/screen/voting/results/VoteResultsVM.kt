package co.electriccoin.zcash.ui.screen.voting.results

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.VoteOptionLabels
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VoteResultsVM(
    private val args: VoteResultsArgs,
    private val getAllRounds: GetAllVotingRoundsUseCase,
    private val votingApiProvider: VotingApiProvider,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private data class ResultsData(
        val roundTitle: String,
        val roundDescription: String,
        val votingEnd: java.time.Instant,
        val proposals: List<co.electriccoin.zcash.ui.common.model.voting.Proposal>,
        val tally: co.electriccoin.zcash.ui.common.model.voting.TallyResults?,
    )

    private val dataLce = mutableLce<ResultsData>()

    init {
        dataLce.execute {
            val round =
                getAllRounds().firstOrNull { it.id == args.roundIdHex }
                    ?: error("Round ${args.roundIdHex} not found")
            val tally = runCatching { votingApiProvider.fetchTallyResults(args.roundIdHex) }.getOrNull()
            ResultsData(round.title, round.description, round.votingEnd, round.proposals, tally)
        }
    }

    val state: StateFlow<LceState<VoteResultsState>> =
        dataLce.state
            .map { lce ->
                lce.success?.let {
                    buildState(it.roundTitle, it.roundDescription, it.votingEnd, it.proposals, it.tally)
                }
            }.withLce(groupLce(dataLce)) {
                errorStateMapper.mapToState(
                    error = it,
                    title = stringRes(R.string.vote_error_results_unavailable_title),
                    message = stringRes(R.string.vote_error_results_unavailable_message),
                    primaryStyle = ButtonStyle.PRIMARY,
                )
            }.stateIn(this)

    private fun buildState(
        roundTitle: String,
        roundDescription: String,
        votingEnd: java.time.Instant,
        proposals: List<co.electriccoin.zcash.ui.common.model.voting.Proposal>,
        tally: co.electriccoin.zcash.ui.common.model.voting.TallyResults?,
    ): VoteResultsState {
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        val proposalResults =
            proposals.map { proposal ->
                val tallyProposal = tally?.proposals?.firstOrNull { it.proposalId == proposal.id }
                val optionCount = proposal.options.size
                val totalWeight =
                    tallyProposal
                        ?.options
                        ?.sumOf { it.weight }
                        ?.toFloat()
                        ?.coerceAtLeast(1f) ?: 1f
                val maxWeight = tallyProposal?.options?.maxOfOrNull { it.weight } ?: 0L
                val hasVotes = (tallyProposal?.options?.sumOf { it.weight } ?: 0L) > 0L

                val optionResults =
                    proposal.options.mapIndexed { index, voteOption ->
                        val weight = tallyProposal?.options?.firstOrNull { it.optionId == voteOption.id }?.weight ?: 0L
                        val fraction = if (hasVotes) weight.toFloat() / totalWeight else 0f
                        val isAbstain = voteOption.label.lowercase().contains(VoteOptionLabels.ABSTAIN)
                        val color =
                            when {
                                isAbstain -> {
                                    VoteOptionColor.ABSTAIN
                                }

                                optionCount == 2 -> {
                                    if (index == 0) VoteOptionColor.SUPPORT else VoteOptionColor.OPPOSE
                                }

                                else -> {
                                    when (index % 3) {
                                        0 -> VoteOptionColor.SUPPORT
                                        1 -> VoteOptionColor.OPPOSE
                                        else -> VoteOptionColor.OTHER
                                    }
                                }
                            }
                        val isWinner = hasVotes && weight == maxWeight
                        VoteOptionResultState(
                            label = stringRes(voteOption.label),
                            amountZEC = stringRes(R.string.vote_results_amount_zec, weight / 100_000_000.0),
                            fraction = fraction,
                            color = color,
                            isWinner = isWinner,
                        )
                    }

                val totalZatoshi = tallyProposal?.options?.sumOf { it.weight } ?: 0L
                val winnerOption =
                    if (hasVotes) {
                        proposal.options.getOrNull(
                            optionResults.indexOfFirst { it.isWinner }
                        )
                    } else {
                        null
                    }

                VoteProposalResultState(
                    zipNumber = proposal.zipNumber?.let { stringRes(it) },
                    title = stringRes(proposal.title),
                    description = stringRes(proposal.description),
                    options = optionResults,
                    totalZEC = stringRes(R.string.vote_results_total_zec, totalZatoshi / 100_000_000.0),
                    winnerLabel = winnerOption?.let { stringRes(it.label) },
                    winnerColor = optionResults.firstOrNull { it.isWinner }?.color ?: VoteOptionColor.OTHER,
                )
            }

        return VoteResultsState(
            roundTitle = stringRes(roundTitle),
            roundDescription = stringRes(roundDescription),
            metaLine = stringRes(R.string.vote_results_ended, dateFormatter.format(votingEnd)),
            proposals = proposalResults,
            isLoadingResults = tally == null,
            doneButton =
                ButtonState(
                    text = stringRes(R.string.vote_done),
                    style = ButtonStyle.PRIMARY,
                    onClick = ::onDone,
                ),
            onBack = ::onBack,
        )
    }

    private fun onDone() = navigationRouter.backTo(VoteCoinholderPollingArgs::class)

    private fun onBack() = navigationRouter.back()
}
