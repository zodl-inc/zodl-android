package co.electriccoin.zcash.ui.screen.viewingkey.addaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.accountlist.options.WalletOptionsState
import co.electriccoin.zcash.ui.screen.accountlist.options.WalletOptionsView
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun AddVKAccountScreen() {
    val vm = koinViewModel<AddVKAccountVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    WalletOptionsView(state)
}

@PreviewScreens
@Composable
private fun AddAccountPreview() =
    ZcashTheme {
        WalletOptionsView(WalletOptionsState.previewAddAccount)
    }

@PreviewScreens
@Composable
private fun HardwareWalletsConnectedPreview() =
    ZcashTheme {
        WalletOptionsView(WalletOptionsState.previewAddAccountHardwareWalletsConnected)
    }

/** The "Add Account" options sheet. */
@Serializable
internal data object AddVKAccountArgs
