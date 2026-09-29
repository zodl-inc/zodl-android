package co.electriccoin.zcash.ui.screen.connectledger.connected

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun LedgerConnectedScreen() {
    val vm = koinViewModel<LedgerConnectedVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { }
    LedgerConnectedView(state)
}

@Serializable
data object LedgerConnectedArgs
