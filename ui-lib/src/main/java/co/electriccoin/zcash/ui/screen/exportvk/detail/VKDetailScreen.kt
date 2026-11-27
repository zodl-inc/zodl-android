package co.electriccoin.zcash.ui.screen.exportvk.detail

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.VKType
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VKDetailScreen(args: VKDetailArgs) {
    val vm = koinViewModel<VKDetailVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state != null) { state?.onBack() }
    state?.let { VKDetailView(state = it) }
}

@Serializable
data class VKDetailArgs(
    val type: VKType
)
