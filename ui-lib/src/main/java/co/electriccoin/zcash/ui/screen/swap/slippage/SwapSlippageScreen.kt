package co.electriccoin.zcash.ui.screen.swap.slippage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.SwapMode
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

@Composable
fun SwapSlippageScreen(args: SwapSlippageArgs) {
    val vm = koinViewModel<SwapSlippageVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    SwapSlippageView(state)
}

@Serializable
data class SwapSlippageArgs(
    val currentSlippage: String,
    val fiatAmount: String?,
    val mode: SwapMode,
    val requestId: String = UUID.randomUUID().toString()
)
