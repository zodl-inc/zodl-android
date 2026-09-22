package co.electriccoin.zcash.ui.screen.choosehardwarewallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChooseHardwareWalletScreen() {
    val vm = koinViewModel<ChooseHardwareWalletVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    ChooseHardwareWalletView(state)
}

@Serializable
data object ChooseHardwareWalletArgs
