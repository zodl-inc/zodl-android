package co.electriccoin.zcash.ui.screen.voting.results

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VoteResultsScreen(args: VoteResultsArgs) {
    val vm = koinViewModel<VoteResultsVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        VoteResultsView(it)
    }
}

@Serializable
data class VoteResultsArgs(
    val roundIdHex: String,
)
