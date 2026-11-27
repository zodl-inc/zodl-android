package co.electriccoin.zcash.ui.screen.swap.refundwarning

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.SwapMode
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapRefundWarningScreen(args: SwapRefundWarningArgs) {
    val vm = koinViewModel<SwapRefundWarningVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    SwapRefundWarningView(state)
}

@Serializable
data class SwapRefundWarningArgs(
    val mode: SwapMode,
    val requestId: String = UUID.randomUUID().toString()
)
