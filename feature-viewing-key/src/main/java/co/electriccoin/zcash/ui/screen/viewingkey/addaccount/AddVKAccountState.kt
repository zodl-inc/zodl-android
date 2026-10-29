package co.electriccoin.zcash.ui.screen.viewingkey.addaccount

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.accountlist.options.WalletOptionState
import co.electriccoin.zcash.ui.screen.accountlist.options.WalletOptionsState
import co.electriccoin.zcash.viewingkey.R

/**
 * The "Add Account" options sheet, drawn by the Wallet Options sheet's view: connecting a hardware
 * wallet while a vendor is still unconnected, and importing a full viewing key.
 */
internal fun WalletOptionsState.Companion.addAccount(
    canConnectHardwareWallet: Boolean,
    onConnectHardwareWalletClick: () -> Unit,
    onImportClick: () -> Unit,
    onBack: () -> Unit,
) = WalletOptionsState(
    title = stringRes(R.string.view_only_add_account),
    options =
        listOfNotNull(
            WalletOptionState(
                icon = imageRes(R.drawable.view_only_ic_option_hardware_wallet),
                title = stringRes(R.string.view_only_add_account_hardware_title),
                subtitle = stringRes(R.string.view_only_add_account_hardware_subtitle),
                testTag = AddVKAccountTag.CONNECT_HARDWARE_WALLET,
                onClick = onConnectHardwareWalletClick,
            ).takeIf { canConnectHardwareWallet },
            WalletOptionState(
                icon = imageRes(R.drawable.view_only_ic_option_import),
                title = stringRes(R.string.view_only_add_account_import_title),
                subtitle = stringRes(R.string.view_only_add_account_import_subtitle),
                testTag = AddVKAccountTag.IMPORT_VIEWING_KEY,
                onClick = onImportClick,
            ),
        ),
    dismissButton =
        ButtonState(
            text = stringRes(R.string.view_only_add_account_dismiss),
            style = ButtonStyle.SECONDARY,
            onClick = onBack,
        ),
    onBack = onBack,
)

internal val WalletOptionsState.Companion.previewAddAccount: WalletOptionsState
    get() = addAccountPreview(canConnectHardwareWallet = true)

internal val WalletOptionsState.Companion.previewAddAccountHardwareWalletsConnected: WalletOptionsState
    get() = addAccountPreview(canConnectHardwareWallet = false)

private fun WalletOptionsState.Companion.addAccountPreview(canConnectHardwareWallet: Boolean) =
    addAccount(
        canConnectHardwareWallet = canConnectHardwareWallet,
        onConnectHardwareWalletClick = {},
        onImportClick = {},
        onBack = {},
    )

internal object AddVKAccountTag {
    const val CONNECT_HARDWARE_WALLET = "view_only_add_account_connect_hardware_wallet"
    const val IMPORT_VIEWING_KEY = "view_only_add_account_import_viewing_key"
}
