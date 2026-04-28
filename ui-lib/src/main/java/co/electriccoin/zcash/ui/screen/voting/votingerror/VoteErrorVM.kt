package co.electriccoin.zcash.ui.screen.voting.votingerror

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoteErrorVM(
    private val args: VoteErrorArgs,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LceState<VoteErrorState>> =
        MutableStateFlow(
            LceState(
                content =
                    VoteErrorState(
                        title = stringRes(R.string.vote_error_something_went_wrong),
                        message = stringRes(VotingErrorMapper.toUserFriendlyMessage(args.message)),
                        actionButton =
                            if (args.isRecoverable) {
                                ButtonState(
                                    text = stringRes(R.string.vote_retry),
                                    style = ButtonStyle.PRIMARY,
                                    onClick = ::onRetry
                                )
                            } else {
                                ButtonState(
                                    text = stringRes(R.string.vote_dismiss),
                                    style = ButtonStyle.PRIMARY,
                                    onClick = ::onClose
                                )
                            },
                        onBack = ::onBack,
                    ),
                isLoading = false
            )
        )

    private fun onRetry() = navigationRouter.back()

    private fun onClose() = navigationRouter.backToRoot()

    private fun onBack() = navigationRouter.back()
}

class VoteConfigErrorVM(
    private val args: VoteConfigErrorArgs,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LceState<VoteConfigErrorState>> =
        MutableStateFlow(
            LceState(
                content =
                    VoteConfigErrorState(
                        message =
                            stringRes(
                                args.message.ifBlank {
                                    "This wallet version is not compatible with the current voting round. " +
                                        "Please update the app to participate."
                                }
                            ),
                        dismissButton =
                            ButtonState(
                                text = stringRes(R.string.vote_dismiss),
                                style = ButtonStyle.PRIMARY,
                                onClick = ::onDismiss
                            ),
                        onBack = ::onBack,
                    ),
                isLoading = false
            )
        )

    private fun onDismiss() = navigationRouter.backToRoot()

    private fun onBack() = navigationRouter.back()
}
