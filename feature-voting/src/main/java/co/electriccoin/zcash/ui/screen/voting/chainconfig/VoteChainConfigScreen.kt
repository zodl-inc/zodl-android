package co.electriccoin.zcash.ui.screen.voting.chainconfig

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun VoteChainConfigScreen() {
    val vm = koinViewModel<VoteChainConfigVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(state != null) { state?.onBack() }
    VoteChainConfigView(state)
}

@Serializable
data object VoteChainConfigArgs
