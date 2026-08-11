package co.electriccoin.zcash.ui.screen.swap.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SwapRefundAddressInfoScreen(args: SwapRefundAddressInfoArgs) {
    val vm = koinViewModel<SwapRefundAddressInfoVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    SwapRefundAddressInfoView(state)
}

@Serializable
data class SwapRefundAddressInfoArgs(
    val tokenTicker: String?,
    val chainTicker: String?
)
