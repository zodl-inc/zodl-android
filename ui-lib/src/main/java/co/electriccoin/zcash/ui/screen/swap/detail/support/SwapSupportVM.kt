package co.electriccoin.zcash.ui.screen.swap.detail.support

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.usecase.GetORSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.SwapData
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.util.EmailUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SwapSupportVM(
    getORSwapQuote: GetORSwapQuoteUseCase,
    private val args: SwapSupportArgs,
    private val navigationRouter: NavigationRouter,
    private val context: Context,
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
        val status = swapData.status ?: return

        val intent =
            EmailUtil
                .newMailActivityIntent(
                    recipientAddress = context.getString(R.string.support_email_address),
                    messageSubject = context.getString(R.string.transaction_detail_support_email_subject),
                    messageBody =
                        context.getString(
                            R.string.transaction_detail_support_email_body,
                            args.depositAddress,
                            status.amountInFormatted.toString() + " " + status.quote.originAsset.tokenTicker,
                            status.amountOutFormatted.toString() + " " + status.quote.destinationAsset.tokenTicker,
                        )
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

        context.startActivity(intent)
        onBack()
    }
}
