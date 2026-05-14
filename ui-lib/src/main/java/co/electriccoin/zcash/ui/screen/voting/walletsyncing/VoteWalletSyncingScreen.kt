package co.electriccoin.zcash.ui.screen.voting.walletsyncing

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VoteWalletSyncingScreen(args: VoteWalletSyncingArgs) {
    val vm = koinViewModel<VoteWalletSyncingVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        VoteWalletSyncingView(it)
    }
}

@Serializable
data class VoteWalletSyncingArgs(
    val roundId: String,
)
