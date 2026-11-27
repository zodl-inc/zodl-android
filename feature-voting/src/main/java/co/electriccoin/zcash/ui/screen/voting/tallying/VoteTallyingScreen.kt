package co.electriccoin.zcash.ui.screen.voting.tallying

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VoteTallyingScreen(args: VoteTallyingArgs) {
    val vm = koinViewModel<VoteTallyingVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        VoteTallyingView(it)
    }
}

@Serializable
data class VoteTallyingArgs(
    val roundIdHex: String,
)
