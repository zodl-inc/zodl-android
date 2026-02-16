package co.electriccoin.zcash.ui.screen.rebrand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.swap.lock.EphemeralLockVM
import co.electriccoin.zcash.ui.screen.swap.lock.EphemeralLockView
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
fun RebrandScreen() {
    val vm = koinViewModel<RebrandVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    RebrandView(state)
}

@Serializable
data object RebrandArgs
