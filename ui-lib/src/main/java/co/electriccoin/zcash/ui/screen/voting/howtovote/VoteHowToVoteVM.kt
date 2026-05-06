package co.electriccoin.zcash.ui.screen.voting.howtovote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.provider.HasSeenHowToVoteStorageProvider
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import co.electriccoin.zcash.ui.screen.voting.component.VoteWalletHeaderIconsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VoteHowToVoteVM(
    private val navigationRouter: NavigationRouter,
    private val hasSeenHowToVote: HasSeenHowToVoteStorageProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LceState<VoteHowToVoteState>>(
        LceState(content = null, isLoading = true)
    )

    val state: StateFlow<LceState<VoteHowToVoteState>> = mutableState

    init {
        viewModelScope.launch {
            val isKeystone = getSelectedWalletAccount() is KeystoneAccount
            val walletName = if (isKeystone) "Keystone" else "Zodl"

            mutableState.value = LceState(
                content = VoteHowToVoteState(
                    title = stringRes(R.string.vote_how_to_vote_title, walletName),
                    subtitle = stringRes(R.string.vote_how_to_vote_subtitle),
                    steps = listOf(
                        VoteStep(
                            number = "1",
                            title = stringRes(R.string.vote_how_to_vote_step1_title),
                            description = stringRes(R.string.vote_how_to_vote_step1_description)
                        ),
                        VoteStep(
                            number = "2",
                            title = stringRes(R.string.vote_how_to_vote_step2_title),
                            description = stringRes(R.string.vote_how_to_vote_step2_description)
                        ),
                    ),
                    infoText = stringRes(R.string.vote_how_to_vote_disclaimer),
                    walletHeaderIcons = VoteWalletHeaderIconsState(isKeystone = isKeystone),
                    continueButton = ButtonState(
                        text = stringRes(R.string.vote_continue),
                        style = ButtonStyle.PRIMARY,
                        onClick = ::onContinue
                    ),
                    onBack = ::onBack
                ),
                isLoading = false
            )
        }
    }

    private fun onContinue() {
        viewModelScope.launch {
            hasSeenHowToVote.store(true)
            navigationRouter.replace(VoteCoinholderPollingArgs)
        }
    }

    private fun onBack() = navigationRouter.back()
}
