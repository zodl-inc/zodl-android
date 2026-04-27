package co.electriccoin.zcash.ui.screen.voting.proposallist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VoteProposalListScreen(args: VoteProposalListArgs) {
    val vm = koinViewModel<VoteProposalListVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { VoteProposalListView(it) }
}

@Serializable
data class VoteProposalListArgs(
    val roundId: String = "",
    val isReviewMode: Boolean = false,
)
