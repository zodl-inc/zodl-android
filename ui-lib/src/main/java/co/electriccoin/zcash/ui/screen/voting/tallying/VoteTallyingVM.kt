package co.electriccoin.zcash.ui.screen.voting.tallying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.common.model.voting.VotingRound
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetAllVotingRoundsUseCase
import co.electriccoin.zcash.ui.common.viewmodel.Action
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val roundLce = mutableLce<VotingRound?>()

    val action = MutableStateFlow<Action?>(null)

    init {
        roundLce.execute {
            getAllRounds().firstOrNull { it.id == args.roundIdHex }
        }

        viewModelScope.launch {
            // Poll round status until finalized or tallying ends
            var pollCount = 0
            while (pollCount < 60) { // max ~5 minutes at 5s intervals
                delay(5_000L)
                pollCount++
                runCatching {
                    val updatedRound = getAllRounds().firstOrNull { it.id == args.roundIdHex }
                    if (updatedRound != null) {
                        val status = updatedRound.status
                        if (status == SessionStatus.COMPLETED || status == SessionStatus.CANCELLED) {
                            navigateToResults()
                            return@launch
                        }
                    }
                }
            }
            // Timeout: navigate anyway after 5 minutes
            navigateToResults()
        }
    }

    private fun navigateToResults() {
        action.value =
            Action {
                action.value = null
                navigationRouter.forward(VoteResultsArgs(roundIdHex = args.roundIdHex))
            }
    }

    val state: StateFlow<LceState<VoteTallyingState>> =
        roundLce.state
            .map { lce -> lce.success?.let { createState(it) } }
            .withLce(groupLce(roundLce)) { error ->
                errorStateMapper.mapToState(
                    error = error,
                    title = stringRes(R.string.vote_error_unable_to_load_round_title),
                    message = stringRes(R.string.vote_error_unable_to_load_round_message),
                    primaryStyle = ButtonStyle.PRIMARY,
                )
            }.stateIn(this)

    private fun createState(round: VotingRound): VoteTallyingState {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())
        return VoteTallyingState(
            roundTitle = stringRes(round.title),
            endedLabel = stringRes(formatter.format(round.votingEnd)),
            proposalCount = stringRes(round.proposals.size.toString()),
            onBack = ::onBack,
        )
    }

    private fun onBack() = navigationRouter.back()
}
