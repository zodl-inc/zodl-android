package co.electriccoin.zcash.ui.screen.swap.detail.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.usecase.GetReloadableSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.common.usecase.SwapData
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SwapSupportVM(
    private val getReloadableSwapQuote: GetReloadableSwapQuoteUseCase,
    private val args: SwapSupportArgs,
    private val navigationRouter: NavigationRouter,
    private val sendEmailUseCase: SendEmailUseCase,
    private val metadataRepository: MetadataRepository,
) : ViewModel() {
    val state: StateFlow<SwapSupportState?> =
        flow {
            val swapMetadata = metadataRepository.getSwapMetadata(args.depositAddress) ?: return@flow
            emitAll(getReloadableSwapQuote.observe(swapMetadata))
        }.map { swapData ->
            SwapSupportState(
                title = stringRes(R.string.reportSwap_title),
                message = stringRes(R.string.reportSwap_msg),
                reportIssueButton =
                    ButtonState(
                        text = stringRes(R.string.reportSwap_report),
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
