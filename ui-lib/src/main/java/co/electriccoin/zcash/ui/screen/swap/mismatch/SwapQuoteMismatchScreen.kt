package co.electriccoin.zcash.ui.screen.swap.mismatch

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchType
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapQuoteMismatchScreen(args: SwapQuoteMismatchArgs) {
    val vm = koinViewModel<SwapQuoteMismatchVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    SwapQuoteMismatchView(state)
}

/**
 * @param requestId defeats the navigation router's duplicate-command debounce, so a second rejected
 * quote with identical details still opens the sheet.
 */
@Serializable
data class SwapQuoteMismatchArgs(
    val provider: String,
    val mode: SwapMode,
    val originTokenTicker: String,
    val originChainTicker: String,
    val destinationTokenTicker: String,
    val destinationChainTicker: String,
    val mismatchType: SwapQuoteMismatchType,
    val depositAddress: String?,
    val requestId: String = UUID.randomUUID().toString()
)
