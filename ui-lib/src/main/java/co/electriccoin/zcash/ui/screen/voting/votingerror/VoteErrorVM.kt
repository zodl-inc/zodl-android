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
                content = VoteErrorState(
                    title = stringRes(R.string.vote_error_title_generic),
                    message = VotingErrorMapper.toUserFriendlyMessage(args.message),
                    actionButton = ButtonState(
                        text = stringRes(if (args.isRecoverable) R.string.vote_try_again else R.string.vote_dismiss),
                        style = ButtonStyle.PRIMARY,
                        onClick = if (args.isRecoverable) ::onRetry else ::onDismiss,
                    ),
                    onBack = ::onBack,
                ),
                isLoading = false,
            )
        )

    private fun onRetry() = navigationRouter.back()

    private fun onDismiss() = navigationRouter.backToRoot()

    private fun onBack() = navigationRouter.back()
}

class VoteConfigErrorVM(
    private val args: VoteConfigErrorArgs,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<LceState<VoteConfigErrorState>> =
        MutableStateFlow(
            LceState(
                content = VoteConfigErrorState(
                    title = VotingErrorMapper.toConfigErrorTitle(args.message),
                    message = VotingErrorMapper.toConfigErrorMessage(args.message),
                    dismissButton = ButtonState(
                        text = stringRes(R.string.vote_dismiss),
                        style = ButtonStyle.PRIMARY,
                        onClick = ::onDismiss,
                    ),
                    onBack = ::onBack,
                ),
                isLoading = false,
            )
        )

    private fun onDismiss() = navigationRouter.backToRoot()

    private fun onBack() = navigationRouter.back()
}
