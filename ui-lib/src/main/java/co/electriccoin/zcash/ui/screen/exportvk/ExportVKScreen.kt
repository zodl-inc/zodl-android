package co.electriccoin.zcash.ui.screen.exportvk

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExportVKScreen() {
    val vm = koinViewModel<ExportVKVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state != null) { state?.onBack() }
    state?.let { ExportVKView(state = it) }
}

@Serializable
data object ExportVKArgs
