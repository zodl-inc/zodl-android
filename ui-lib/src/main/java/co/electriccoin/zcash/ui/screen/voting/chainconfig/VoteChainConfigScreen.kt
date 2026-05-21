package co.electriccoin.zcash.ui.screen.voting.chainconfig

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun VoteChainConfigScreen() {
    val vm = koinViewModel<VoteChainConfigVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { content ->
        BackHandler { content.onBack() }
        VoteChainConfigView(content)
    }
}

@Serializable
data object VoteChainConfigArgs
