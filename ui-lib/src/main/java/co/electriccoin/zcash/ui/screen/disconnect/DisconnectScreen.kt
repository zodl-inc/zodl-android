package co.electriccoin.zcash.ui.screen.disconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DisconnectScreen(args: DisconnectArgs) {
    val vm = koinViewModel<DisconnectVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { DisconnectView(it) }
}

/**
 * @param accountStorageKeyId The hardware account to remove, as
 *        [co.electriccoin.zcash.ui.common.model.toStorageKeyId] encodes it. Named explicitly
 *        because with two vendors connected there is no "the" hardware account to fall back on.
 */
@Serializable
data class DisconnectArgs(
    val accountStorageKeyId: String
)
