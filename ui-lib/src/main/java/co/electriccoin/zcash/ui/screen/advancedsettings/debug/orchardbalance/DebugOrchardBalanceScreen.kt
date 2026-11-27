package co.electriccoin.zcash.ui.screen.advancedsettings.debug.orchardbalance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun DebugOrchardBalanceScreen() {
    val vm = koinViewModel<DebugOrchardBalanceVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    DebugOrchardBalanceView(state = state)
}

@Serializable
data object DebugOrchardBalanceArgs
