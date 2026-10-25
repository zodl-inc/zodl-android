package co.electriccoin.zcash.ui.screen.viewingkey.remove

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun RemoveVKAccountScreen(args: RemoveVKAccountArgs) {
    val vm = koinViewModel<RemoveVKAccountVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    RemoveVKAccountView(state)
}

/**
 * The sheet that confirms removing the view-only account [accountKeyId] names
 * (`AccountUuid.toStorageKeyId()`). The route carries only that id, never the account's viewing key.
 */
@Serializable
internal data class RemoveVKAccountArgs(
    val accountKeyId: String
)
