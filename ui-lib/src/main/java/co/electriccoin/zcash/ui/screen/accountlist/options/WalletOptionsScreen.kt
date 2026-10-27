package co.electriccoin.zcash.ui.screen.accountlist.options

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun WalletOptionsScreen(args: WalletOptionsArgs) {
    val vm = koinViewModel<WalletOptionsVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    WalletOptionsView(state)
}

/**
 * @param accountKeyId The hardware wallet account's `AccountUuid.toStorageKeyId()`.
 */
@Serializable
internal data class WalletOptionsArgs(
    val accountKeyId: String
)
