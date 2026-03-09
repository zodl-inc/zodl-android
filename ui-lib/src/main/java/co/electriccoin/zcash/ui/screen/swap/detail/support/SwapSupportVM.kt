package co.electriccoin.zcash.ui.screen.swap.detail.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.GetORSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.common.usecase.SwapData
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SwapSupportVM(
    getORSwapQuote: GetORSwapQuoteUseCase,
    private val args: SwapSupportArgs,
    private val navigationRouter: NavigationRouter,
    private val sendEmailUseCase: SendEmailUseCase,
) : ViewModel() {
    val state: StateFlow<SwapSupportState?> =
        getORSwapQuote
            .observe(args.depositAddress)
            .map { swapData ->
                SwapSupportState(
                    title = stringRes(R.string.transaction_detail_support_disclaimer_title),
                    message = stringRes(R.string.transaction_detail_support_disclaimer_message),
                    reportIssueButton =
                        ButtonState(
                            text = stringRes(R.string.transaction_detail_report_issue),
                            onClick = { onReportIssue(swapData) }
                        ),
                    onBack = ::onBack
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
                initialValue = null
            )

    private fun onBack() = navigationRouter.back()

    private fun onReportIssue(swapData: SwapData) {
        viewModelScope.launch {
            sendEmailUseCase.invoke(swapData)
            onBack()
        }
    }
}
