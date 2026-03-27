package co.electriccoin.zcash.ui.screen.disconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun DisconnectScreen() {
    val vm = koinViewModel<DisconnectVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    state?.let { DisconnectView(it) }
}

@Serializable
data object DisconnectArgs
