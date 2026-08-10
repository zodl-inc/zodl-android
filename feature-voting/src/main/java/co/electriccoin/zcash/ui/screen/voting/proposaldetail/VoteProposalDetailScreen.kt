package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VoteProposalDetailScreen(args: VoteProposalDetailArgs) {
    val vm = koinViewModel<VoteProposalDetailVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        VoteProposalDetailView(it)
    }
}

@Serializable
data class VoteProposalDetailArgs(
    val proposalId: Int,
    val roundId: String,
    val isEditingFromReview: Boolean = false,
    val isReadOnly: Boolean = false,
)
