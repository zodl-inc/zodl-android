package co.electriccoin.zcash.ui.screen.theme.darklook

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeDarkLookScreen(args: ThemeDarkLookArgs) {
    val vm = koinViewModel<ThemeDarkLookVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    ThemeDarkLookView(state)
}

@Serializable
data class ThemeDarkLookArgs(
    val mode: AppearanceMode
)
