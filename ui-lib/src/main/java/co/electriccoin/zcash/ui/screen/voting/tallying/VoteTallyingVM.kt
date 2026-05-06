package co.electriccoin.zcash.ui.screen.voting.tallying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VoteTallyingVM(
    private val args: VoteTallyingArgs,
    private val getAllRounds: GetAllVotingRoundsUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val roundLce = mutableLce<VotingRound>()

    init {
        roundLce.execute {
            getAllRounds().firstOrNull { it.id == args.roundIdHex }
                ?: error("Round ${args.roundIdHex} not found")
        }

        viewModelScope.launch {
            repeat(MAX_POLL_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)
                val round = runCatching {
                    getAllRounds().firstOrNull { it.id == args.roundIdHex }
                }.getOrNull() ?: return@launch

                if (round.status == SessionStatus.COMPLETED || round.status == SessionStatus.CANCELLED) {
                    navigationRouter.replace(VoteResultsArgs(roundIdHex = args.roundIdHex))
                    return@launch
                }
            }

            navigationRouter.replace(VoteResultsArgs(roundIdHex = args.roundIdHex))
        }
    }

    val state =
        roundLce.state
            .map { lce -> lce.success?.let(::buildState) }
            .withLce(groupLce(roundLce)) { error ->
                errorStateMapper.mapToState(
                    error = error,
                    title = stringRes(R.string.vote_error_unable_to_load_round_title),
                    message = stringRes(R.string.vote_error_unable_to_load_round_message),
                    primaryStyle = ButtonStyle.PRIMARY,
                )
            }.stateIn(this)

    private fun buildState(round: VotingRound): VoteTallyingState {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())
        return VoteTallyingState(
            roundTitle = stringRes(round.title),
            endedLabel = stringRes(formatter.format(round.votingEnd)),
            proposalCount = stringRes(round.proposals.size.toString()),
            onBack = ::onBack,
        )
    }

    private fun onBack() = navigationRouter.backTo(VoteCoinholderPollingArgs::class)

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val MAX_POLL_ATTEMPTS = 60
    }
}
