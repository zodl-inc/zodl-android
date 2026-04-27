package co.electriccoin.zcash.ui.screen.voting.howtovote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.provider.HasSeenHowToVoteStorageProvider
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VoteHowToVoteVM(
    private val navigationRouter: NavigationRouter,
    private val hasSeenHowToVote: HasSeenHowToVoteStorageProvider,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) : ViewModel() {
    private val _state =
        MutableStateFlow<LceState<VoteHowToVoteState>>(
            LceState(content = null, isLoading = true)
        )
    val state: StateFlow<LceState<VoteHowToVoteState>> = _state

    init {
        viewModelScope.launch {
            val isKeystone = getSelectedWalletAccount() is KeystoneAccount
            val walletName = if (isKeystone) "Keystone" else "Zodl"
            _state.value =
                LceState(
                    content =
                        VoteHowToVoteState(
                            title = stringRes("How to vote with $walletName"),
                            subtitle =
                                stringRes(
                                    "Your ZEC gives you a voice. Shape the future of the Zcash " +
                                        "network by voting on active proposals."
                                ),
                            steps =
                                listOf(
                                    VoteStep(
                                        number = "1",
                                        title = stringRes("Voting on Proposals"),
                                        description =
                                            stringRes(
                                                "Vote Support, Oppose, or Abstain on each question. " +
                                                    "You can skip questions and change your vote before submitting."
                                            )
                                    ),
                                    VoteStep(
                                        number = "2",
                                        title = stringRes("Authorize and Submit"),
                                        description =
                                            stringRes(
                                                "When you're ready, you'll confirm a small authorization transaction " +
                                                    "and submit your vote in one step. After submission, your vote cannot be changed."
                                            )
                                    ),
                                ),
                            infoText =
                                stringRes(
                                    "Your balance at the snapshot time determines your voting weight. " +
                                        "You don't need to move your funds anywhere."
                                ),
                            isKeystoneUser = isKeystone,
                            continueButton =
                                ButtonState(
                                    text = stringRes("Continue"),
                                    style = ButtonStyle.PRIMARY,
                                    onClick = ::onContinue
                                ),
                            onBack = ::onBack,
                        ),
                    isLoading = false
                )
        }
    }

    private fun onContinue() {
        viewModelScope.launch {
            hasSeenHowToVote.store(true)
            navigationRouter.forward(VoteCoinholderPollingArgs)
        }
    }

    private fun onBack() = navigationRouter.back()
}
