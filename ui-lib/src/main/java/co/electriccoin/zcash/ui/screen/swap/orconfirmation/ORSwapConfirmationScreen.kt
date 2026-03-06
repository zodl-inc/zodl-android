package co.electriccoin.zcash.ui.screen.swap.orconfirmation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.error.DialogView
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun ORSwapConfirmationScreen() {
    val vm = koinViewModel<ORSwapConfirmationVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    val dialogState by vm.dialogState.collectAsStateWithLifecycle()
    BackHandler { state?.onBack?.invoke() }
    state?.let { ORSwapConfirmationView(it) }
    dialogState?.let { DialogView(state = it) }
}

@Serializable
data object ORSwapConfirmationArgs
