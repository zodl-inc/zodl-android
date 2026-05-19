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
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.repository.VotingApiRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoveryRepository
import co.electriccoin.zcash.ui.common.repository.VotingRecoverySnapshot
import co.electriccoin.zcash.ui.common.repository.toVotingAccountScopeId
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import co.electriccoin.zcash.ui.screen.voting.isDefaultVotingConfig
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VoteResultsRoundNotFoundException(
    roundId: String
) : Exception("Round $roundId not found")

class VoteResultsVM(
    private val args: VoteResultsArgs,
    private val getAllRounds: GetAllVotingRoundsUseCase,
    private val votingApiProvider: VotingApiProvider,
    private val votingApiRepository: VotingApiRepository,
    private val configurationRepository: ConfigurationRepository,
    private val votingChainConfigRepository: VotingChainConfigRepository,
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
            val round =
                votingApiRepository.snapshot.value.rounds
                    .firstOrNull { it.id == args.roundIdHex }
                    ?: getAllRounds().firstOrNull { it.id == args.roundIdHex }
                    ?: throw VoteResultsRoundNotFoundException(args.roundIdHex)
            val cachedTally = votingApiRepository.snapshot.value.tallyResultsByRoundId[args.roundIdHex]
            val tally =
                cachedTally
                    ?: votingApiProvider.fetchTallyResults(args.roundIdHex).also { results ->
                        votingApiRepository.storeTallyResults(args.roundIdHex, results)
                    }
            val accountUuid = getSelectedWalletAccount().sdkAccount.accountUuid.toVotingAccountScopeId()
            val recovery = votingRecoveryRepository.get(accountUuid, args.roundIdHex)

            ResultsData(round = round, tally = tally, recovery = recovery)
        }
    }

    val state =
        combine(
            resultsLce.state,
            votingApiRepository.snapshot,
            isOnDefaultConfigFlow()
        ) { lce, apiSnapshot, isOnDefaultConfig ->
            lce.success?.let { results ->
                val round = apiSnapshot.rounds.firstOrNull { it.id == results.round.id } ?: results.round
                buildState(
                    round = round,
                    tally = results.tally,
                    recovery = results.recovery
                )
            }
        }.withLce(groupLce(resultsLce)) { error ->
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
        val proposals =
            round.proposals.map { proposal ->
                buildProposalState(proposal, tally, recovery)
            }

        return VoteResultsState(
            roundTitle = stringRes(round.title),
            roundDescription = stringRes(round.description),
            votedMetaLine = buildVotedMetaLine(recovery),
            proposals = proposals,
            isLoadingResults = false,
            doneButton =
                ButtonState(
                    text = stringRes(R.string.vote_done),
                    style = ButtonStyle.PRIMARY,
                    onClick = ::onDone,
                ),
            onBack = ::onBack,
        )
    }

    private fun isOnDefaultConfigFlow() =
        combine(
            votingChainConfigRepository.state,
            configurationRepository.configurationFlow
        ) { chainConfig, configuration ->
            isDefaultVotingConfig(chainConfig, configuration)
        }

    private fun buildProposalState(
        proposal: Proposal,
        tally: TallyResults,
        recovery: VotingRecoverySnapshot?,
    ): VoteProposalResultState {
        val tallyProposal = tally.proposals.firstOrNull { it.proposalId == proposal.id }
        val totalWeight = tallyProposal?.options?.sumOf { it.weight } ?: 0L
        val displayWeight = totalWeight.coerceAtLeast(1L).toFloat()
        val maxWeight = tallyProposal?.options?.maxOfOrNull { it.weight } ?: 0L
        val hasVotes = totalWeight > 0L
        val winningOptions = tallyProposal?.options?.filter { it.weight == maxWeight && maxWeight > 0L }.orEmpty()
        val hasTie = winningOptions.size > 1

        val options =
            proposal.options.mapIndexed { index, option ->
                val weight = tallyProposal?.options?.firstOrNull { it.optionId == option.id }?.weight ?: 0L

                VoteOptionResultState(
                    label = stringRes(option.label),
                    amountZec = stringRes(R.string.vote_results_amount_zec, weight.toZec()),
                    fraction = if (hasVotes) weight / displayWeight else 0f,
                    isWinner = hasVotes && !hasTie && weight == maxWeight,
                )
            }

        val votedOptionId = recovery?.draftChoices?.get(proposal.id)
        val votedLabel =
            proposal.options
                .firstOrNull { it.id == votedOptionId }
                ?.label
                ?.let { stringRes(R.string.vote_results_voted_option, it) }

        return VoteProposalResultState(
            zipNumber = proposal.zipNumber?.let(::stringRes),
            title = stringRes(proposal.title),
            description = stringRes(proposal.description),
            options = options,
            totalZec = stringRes(R.string.vote_results_total_zec, totalWeight.toZec()),
            votedLabel = votedLabel,
        )
    }

    private fun buildVotedMetaLine(recovery: VotingRecoverySnapshot?): StringResource? {
        val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        val votedAt = recovery?.submittedAtEpochSeconds?.let(Instant::ofEpochSecond)
        val votedLabel =
            votedAt?.let { instant ->
                stringRes(R.string.vote_results_voted, formatter.format(instant))
            }
        val votingPowerLabel =
            recovery?.eligibleWeight?.let { weight ->
                stringRes(R.string.vote_results_voting_power, weight.toZec())
            }
        return when {
            votedLabel != null && votingPowerLabel != null -> {
                stringRes(R.string.vote_results_meta_line, votedLabel, votingPowerLabel)
            }

            votedLabel != null -> {
                votedLabel
            }

            else -> {
                votingPowerLabel
            }
        }
    }

    private fun onDone() = navigationRouter.backTo(VoteCoinholderPollingArgs::class)

    fun onBack() = navigationRouter.backTo(VoteCoinholderPollingArgs::class)
}

private fun Long.toZec(): Double = this / 100_000_000.0
