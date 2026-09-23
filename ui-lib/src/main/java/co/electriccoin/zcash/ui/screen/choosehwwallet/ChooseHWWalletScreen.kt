package co.electriccoin.zcash.ui.screen.choosehwwallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChooseHWWalletScreen() {
    val vm = koinViewModel<ChooseHWWalletVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    ChooseHWWalletView(state)
}

@Serializable
data object ChooseHWWalletArgs
