package co.electriccoin.zcash.ui.screen.connectledger.turnon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun LedgerTurnOnScreen() {
    val vm = koinViewModel<LedgerTurnOnVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LedgerTurnOnView(state)
}

@Serializable
data object LedgerTurnOnArgs
