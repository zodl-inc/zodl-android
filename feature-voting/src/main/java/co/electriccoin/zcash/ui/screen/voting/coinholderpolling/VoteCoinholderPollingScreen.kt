package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    LceRenderer(state = state) {
        BackHandler { it.onBack() }
        if (it.isInitialLoading) {
            VoteCoinholderPollingLoadingView(it)
        } else {
            // Cache-and-fresh (MOB-1808): rounds render immediately from the repository cache
            // while a refresh runs in the background (see VoteCoinholderPollingVM's `rounds`
            // derivation). `isLoading` no longer needs to block content behind a full-screen
            // shimmer for this case, so it drives the pull-to-refresh spinner instead — the
            // background refresh becomes visible feedback rather than a silent, unbounded wait.
            PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = it.onRefresh) {
                VoteCoinholderPollingView(it)
            }
        }
    }
}

@Serializable
data object VoteCoinholderPollingArgs
