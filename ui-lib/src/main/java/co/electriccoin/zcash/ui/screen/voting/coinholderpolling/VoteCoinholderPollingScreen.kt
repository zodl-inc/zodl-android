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
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // Mark retained content stale while another voting screen is on top. Returning to this
        // destination should show loading until the resume refresh has started.
        vm.onScreenExited()
    }
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.isLoading && state.error == null) {
        VoteCoinholderPollingLoadingView()
        return
    }
    LceRenderer(state = state) {
        BackHandler { it.onBack() }
        VoteCoinholderPollingView(it)
    }
}

@Serializable
data object VoteCoinholderPollingArgs
