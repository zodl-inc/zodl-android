package co.electriccoin.zcash.ui.screen.disconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun DisconnectScreen() {
    val vm = koinViewModel<DisconnectVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { DisconnectView(it) }
}

@Serializable
data object DisconnectArgs
