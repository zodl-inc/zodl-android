package co.electriccoin.zcash.ui.screen.restore.seed

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.compose.SecureScreen
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun RestoreSeedScreen() {
    val vm = koinViewModel<RestoreSeedVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    val suggestionsState = vm.suggestionsState.collectAsStateWithLifecycle().value
    SecureScreen()
    BackHandler(state != null) { state?.onBack?.invoke() }
    if (state != null && suggestionsState != null) {
        state?.let { RestoreSeedView(it, suggestionsState) }
    }
}

@Serializable
data object RestoreSeedArgs
