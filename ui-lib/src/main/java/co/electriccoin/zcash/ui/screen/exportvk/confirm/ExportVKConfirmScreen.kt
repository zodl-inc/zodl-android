package co.electriccoin.zcash.ui.screen.exportvk.confirm

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportVKConfirmScreen() {
    val vm = koinViewModel<ExportVKConfirmVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    ExportVKConfirmView(state = state)
}

@Serializable
data object ExportVKConfirmArgs
