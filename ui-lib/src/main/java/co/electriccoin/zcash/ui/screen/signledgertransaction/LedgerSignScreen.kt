package co.electriccoin.zcash.ui.screen.signledgertransaction

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerSignScreen() {
    val vm = koinViewModel<LedgerSignVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LedgerSignSheet(state)
}

@Serializable
data object LedgerSignArgs
