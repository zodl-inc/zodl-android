package co.electriccoin.zcash.ui.screen.disconnect

import androidx.activity.compose.BackHandler
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
    LceRenderer(state) {
        BackHandler { it.onBack() }
        DisconnectView(it)
    }
}

/**
 * @param accountKeyId The `AccountUuid.toStorageKeyId()` of the hardware wallet account to
 *        disconnect, as the Wallets & Hardware screen passes it; null disconnects the selected
 *        account, as Advanced Settings does. A non-null key requires the Wallets & Hardware screen
 *        to be on the back stack: a finished disconnect returns with `backTo(AccountListArgs::class)`,
 *        which does nothing when that screen is not there.
 */
@Serializable
data class DisconnectArgs(
    val accountKeyId: String? = null
)
