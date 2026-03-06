package co.electriccoin.zcash.ui.screen.swap.detail.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SwapSupportScreen(args: SwapSupportArgs) {
    val vm = koinViewModel<SwapSupportVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    SwapSupportView(state)
}

@Serializable
data class SwapSupportArgs(
    val depositAddress: String
)
