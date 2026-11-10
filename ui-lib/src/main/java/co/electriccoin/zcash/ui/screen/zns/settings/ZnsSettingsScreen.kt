package co.electriccoin.zcash.ui.screen.zns.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun ZnsSettingsScreen() {
    val vm = koinViewModel<ZnsSettingsVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(state != null) { state?.onDismiss?.invoke() }
    state?.let { ZnsSettingsView(state = it) }
}

@Serializable
object ZnsSettingsArgs
