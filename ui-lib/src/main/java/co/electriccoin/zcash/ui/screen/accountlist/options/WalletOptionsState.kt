package co.electriccoin.zcash.ui.screen.accountlist.options

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * The "Wallet Options" sheet of a hardware wallet row on the Wallets & Hardware screen. Its one
 * option, disconnecting the hardware wallet, leads into the disconnect screen for that account.
 * The view-only feature's "Add Account" sheet is built from it as well.
 */
@Immutable
data class WalletOptionsState(
    val title: StringResource,
    val options: List<WalletOptionState>,
    val dismissButton: ButtonState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview: WalletOptionsState = preview(isLedger = false)

        val previewLedger: WalletOptionsState = preview(isLedger = true)

        fun create(
            isLedger: Boolean,
            onDisconnectClick: () -> Unit,
            onBack: () -> Unit,
        ) = WalletOptionsState(
            title = stringRes(R.string.hwWallet_options_title),
            options =
                listOf(
                    WalletOptionState(
                        icon = imageRes(R.drawable.ic_wallet_options_disconnect),
                        title = stringRes(R.string.hwWallet_options_disconnect_title),
                        subtitle =
                            if (isLedger) {
                                stringRes(R.string.hwWallet_options_disconnect_ledger)
                            } else {
                                stringRes(R.string.hwWallet_options_disconnect_keystone)
                            },
                        testTag = WalletOptionsTag.DISCONNECT,
                        onClick = onDisconnectClick,
                    )
                ),
            dismissButton =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_dismiss),
                    style = ButtonStyle.SECONDARY,
                    onClick = onBack,
                ),
            onBack = onBack,
        )

        private fun preview(isLedger: Boolean) =
            create(
                isLedger = isLedger,
                onDisconnectClick = {},
                onBack = {},
            )
    }
}

@Immutable
data class WalletOptionState(
    val icon: ImageResource,
    val title: StringResource,
    val subtitle: StringResource,
    val testTag: String,
    val onClick: () -> Unit,
)

private object WalletOptionsTag {
    const val DISCONNECT = "wallet_options_disconnect"
}
