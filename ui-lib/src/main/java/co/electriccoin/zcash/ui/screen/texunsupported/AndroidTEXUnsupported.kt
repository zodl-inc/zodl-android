package co.electriccoin.zcash.ui.screen.texunsupported

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidTEXUnsupported(args: TEXUnsupportedArgs) {
    val vm = koinViewModel<TEXUnsupportedVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    TEXUnsupportedView(state)
}

@Serializable
data class TEXUnsupportedArgs(
    val isLedger: Boolean = false
)
