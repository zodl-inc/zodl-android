package co.electriccoin.zcash.ui.screen.swap.mismatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.common.usecase.SwapQuoteMismatchReport
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SwapQuoteMismatchVM(
    private val args: SwapQuoteMismatchArgs,
    private val navigationRouter: NavigationRouter,
    private val sendEmailUseCase: SendEmailUseCase,
) : ViewModel() {
    val state: StateFlow<SwapQuoteMismatchState?> =
        MutableStateFlow<SwapQuoteMismatchState?>(
            SwapQuoteMismatchState(
                title = stringRes(R.string.swap_mismatch_title),
                paragraphs =
                    listOf(
                        stringRes(R.string.swap_mismatch_msg_1),
                        stringRes(R.string.swap_mismatch_msg_2),
                        stringRes(R.string.swap_mismatch_msg_3)
                    ),
                goBackButton =
                    ButtonState(
                        text = stringRes(R.string.swap_mismatch_goBack),
                        onClick = ::onBack
                    ),
                reportButton =
                    ButtonState(
                        text = stringRes(R.string.send_report),
                        onClick = ::onReport
                    ),
                onBack = ::onBack
            )
        ).asStateFlow()

    private fun onBack() = navigationRouter.back()

    private fun onReport() {
        viewModelScope.launch {
            sendEmailUseCase(
                SwapQuoteMismatchReport(
                    provider = args.provider,
                    mode = args.mode,
                    originTokenTicker = args.originTokenTicker,
                    originChainTicker = args.originChainTicker,
                    destinationTokenTicker = args.destinationTokenTicker,
                    destinationChainTicker = args.destinationChainTicker,
                    mismatchType = args.mismatchType,
                    depositAddress = args.depositAddress
                )
            )
            onBack()
        }
    }
}
