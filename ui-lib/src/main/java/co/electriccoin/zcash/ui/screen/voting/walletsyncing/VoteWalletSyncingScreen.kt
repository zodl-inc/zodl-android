package co.electriccoin.zcash.ui.screen.voting.walletsyncing

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun VoteWalletSyncingScreen() {
    val vm = koinViewModel<VoteWalletSyncingVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) {
        BackHandler { it.onBack() }
        VoteWalletSyncingView(it)
    }
}

@Serializable
data object VoteWalletSyncingArgs
