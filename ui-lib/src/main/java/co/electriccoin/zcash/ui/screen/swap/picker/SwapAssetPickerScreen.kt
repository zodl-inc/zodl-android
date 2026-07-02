package co.electriccoin.zcash.ui.screen.swap.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.SwapProvider
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

@Composable
fun SwapAssetPickerScreen(args: SwapAssetPickerArgs) {
    val vm = koinViewModel<SwapAssetPickerVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    SwapAssetPickerView(state)
}

@Serializable
data class SwapAssetPickerArgs(
    val onlyChainTicker: String?,
    // MOB-1396: when set, the picker sources assets from that provider's repository and offers only its
    // assets (the NEAR-only Pay flow passes NEAR). Null uses the aggregator across all providers.
    val provider: SwapProvider? = null,
    val requestId: String = UUID.randomUUID().toString()
)
