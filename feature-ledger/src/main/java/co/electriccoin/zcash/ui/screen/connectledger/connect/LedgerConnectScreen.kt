package co.electriccoin.zcash.ui.screen.connectledger.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun LedgerConnectScreen() {
    val vm = koinViewModel<LedgerConnectVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LedgerConnectView(state)
}

@Serializable
data object LedgerConnectArgs
