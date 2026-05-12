package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun VoteCoinholderPollingScreen() {
    val vm = koinViewModel<VoteCoinholderPollingVM>()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.onScreenEntered()
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(
        state = state,
        loading = { isLoading ->
            if (isLoading && state.content == null) {
                VoteCoinholderPollingLoadingView()
            }
        }
    ) {
        BackHandler { it.onBack() }
        VoteCoinholderPollingView(it)
    }
}

@Serializable
data object VoteCoinholderPollingArgs
