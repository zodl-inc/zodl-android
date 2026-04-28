package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun VoteConfirmSubmissionScreen(args: VoteConfirmSubmissionArgs) {
    val vm = koinViewModel<VoteConfirmSubmissionVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        val inProgress =
            it.status is VoteSubmissionStatus.Authorizing ||
                it.status is VoteSubmissionStatus.Submitting
        BackHandler(enabled = !inProgress) { it.onBack() }
        VoteConfirmSubmissionView(it)
    }
}

// ─── Args ─────────────────────────────────────────────────────────────────────

@Serializable
data class VoteConfirmSubmissionArgs(
    val roundIdHex: String,
    val choicesJson: String,
)
