package co.electriccoin.zcash.ui.screen.deletewallet

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun ResetZashiScreen() {
    val vm = koinViewModel<ResetZashiVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { state?.onBack() }
    state?.let { ResetZashiView(it) }
}

@Serializable
data object ResetZashiArgs
