package co.electriccoin.zcash.ui.screen.theme.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun ThemeSettingsScreen() {
    val vm = koinViewModel<ThemeSettingsVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state != null) { state?.onBack() }
    state?.let { ThemeSettingsView(state = it) }
}

@Serializable
data object ThemeSettingsArgs
