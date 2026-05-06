package co.electriccoin.zcash.ui.screen.voting.votingerror

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VoteErrorScreen(args: VoteErrorArgs) {
    val vm = koinViewModel<VoteErrorVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { VoteErrorView(it) }
}

@Serializable
data class VoteErrorArgs(
    val message: String,
    val isRecoverable: Boolean = true,
)
