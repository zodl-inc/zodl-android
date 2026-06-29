package co.electriccoin.zcash.ui.screen.swap.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    // MOB-1396: when true the picker offers only NEAR-quotable assets (used by the NEAR-only Pay flow).
    val nearOnly: Boolean = false,
    val requestId: String = UUID.randomUUID().toString()
)
