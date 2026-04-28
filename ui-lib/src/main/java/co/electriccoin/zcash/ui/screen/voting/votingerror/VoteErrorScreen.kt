package co.electriccoin.zcash.ui.screen.voting.votingerror

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

// ─── VotingErrorScreen ────────────────────────────────────────────────────────

@Composable
fun VoteErrorScreen(args: VoteErrorArgs) {
    val vm = koinViewModel<VoteErrorVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        VoteErrorView(it)
    }
}

@Serializable
data class VoteErrorArgs(
    val message: String,
    val isRecoverable: Boolean = true
)

// ─── VoteConfigErrorScreen (R10) ───────────────────────────────────────────

/**
 * Shown when the app is incompatible with the current voting round config
 * (e.g. round_id / proposals_hash mismatch). Terminal state — dismiss only.
 * Mirrors iOS VotingConfigErrorView.
 */
@Composable
fun VoteConfigErrorScreen(args: VoteConfigErrorArgs) {
    val vm = koinViewModel<VoteConfigErrorVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { VoteConfigErrorView(it) }
}

@Serializable
data class VoteConfigErrorArgs(
    val message: String = ""
)
