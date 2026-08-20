package co.electriccoin.zcash.ui.screen.swap.info

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SwapRefundAddressInfoVM(
    private val args: SwapRefundAddressInfoArgs,
    private val blockchainProvider: BlockchainProvider,
    private val navigationRouter: NavigationRouter
) : ViewModel() {
    val state: StateFlow<SwapRefundAddressInfoState?> = MutableStateFlow(createState()).asStateFlow()

    private fun createState(): SwapRefundAddressInfoState {
        val tokenTicker = args.tokenTicker
        val chainTicker = args.chainTicker
        return SwapRefundAddressInfoState(
            message =
                if (tokenTicker != null && chainTicker != null) {
                    stringRes(
                        R.string.swap_refund_address_info_message,
                        tokenTicker,
                        blockchainProvider.getBlockchain(chainTicker).chainName
                    )
                } else {
                    stringRes(R.string.swap_refund_address_info_message_empty)
                },
            onBack = ::onBack
        )
    }

    private fun onBack() = navigationRouter.back()
}
