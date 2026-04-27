package co.electriccoin.zcash.ui.screen.voting.results

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun VoteResultsScreen(args: VoteResultsArgs) {
    val vm = koinViewModel<VoteResultsVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { VoteResultsView(it) }
}

// ─── Args ─────────────────────────────────────────────────────────────────────

@Serializable
data class VoteResultsArgs(
    val roundIdHex: String
)
