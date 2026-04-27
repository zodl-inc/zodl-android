package co.electriccoin.zcash.ui.screen.voting.howtovote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun VoteHowToVoteScreen() {
    val vm = koinViewModel<VoteHowToVoteVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { VoteHowToVoteView(it) }
}

@Serializable
data object VoteHowToVoteArgs
