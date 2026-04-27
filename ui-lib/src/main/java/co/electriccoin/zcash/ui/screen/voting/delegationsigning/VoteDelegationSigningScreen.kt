package co.electriccoin.zcash.ui.screen.voting.delegationsigning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun VoteDelegationSigningScreen(args: VoteDelegationSigningArgs) {
    val vm = koinViewModel<VoteDelegationSigningVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { VoteDelegationSigningView(it) }
}

@Serializable
data class VoteDelegationSigningArgs(
    val roundIdHex: String
)
