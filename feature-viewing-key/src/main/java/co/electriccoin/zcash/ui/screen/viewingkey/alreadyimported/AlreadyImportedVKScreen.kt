package co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun AlreadyImportedVKScreen() {
    val vm = koinViewModel<AlreadyImportedVKVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    AlreadyImportedVKView(state)
}

/** The sheet shown when the key belongs to an account the wallet already holds. */
@Serializable
internal data object AlreadyImportedVKArgs
