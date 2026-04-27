package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun VoteCoinholderPollingScreen() {
    val vm = koinViewModel<VoteCoinholderPollingVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { VoteCoinholderPollingView(it) }
}

@Serializable
data object VoteCoinholderPollingArgs
