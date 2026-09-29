package co.electriccoin.zcash.ui.screen.connectledger.openapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun LedgerOpenAppScreen() {
    val vm = koinViewModel<LedgerOpenAppVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LedgerOpenAppView(state)
}

@Serializable
data object LedgerOpenAppArgs
