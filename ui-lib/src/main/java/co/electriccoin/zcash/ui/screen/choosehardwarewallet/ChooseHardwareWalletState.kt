package co.electriccoin.zcash.ui.screen.choosehardwarewallet

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes

data class ChooseHardwareWalletState(
    val title: StringResource,
    val subtitle: StringResource,
    val cards: List<HardwareWalletCardState>,
    val onBack: () -> Unit,
) {
    companion object {
        val preview =
            ChooseHardwareWalletState(
                title = stringRes("Connect Hardware Wallet"),
                subtitle =
                    stringRes(
                        "Which hardware wallet would you like to connect? " +
                            "Select your device to get started."
                    ),
                cards =
                    listOf(
                        HardwareWalletCardState.previewKeystone,
                        HardwareWalletCardState.previewLedger,
                    ),
                onBack = {},
            )

        val previewLedgerOnly = preview.copy(cards = listOf(HardwareWalletCardState.previewLedger))
    }
}

/**
 * One tappable vendor card; [image] is the whole card artwork, brand mark included.
 */
data class HardwareWalletCardState(
    val image: ImageResource,
    val contentDescription: StringResource,
    val testTag: String,
    val onClick: () -> Unit,
) {
    companion object {
        val previewKeystone =
            HardwareWalletCardState(
                image = imageRes(R.drawable.img_hardware_wallet_keystone),
                contentDescription = stringRes("Keystone"),
                testTag = ChooseHardwareWalletTag.KEYSTONE_CARD,
                onClick = {},
            )

        val previewLedger =
            HardwareWalletCardState(
                image = imageRes(R.drawable.img_hardware_wallet_ledger),
                contentDescription = stringRes("Ledger"),
                testTag = ChooseHardwareWalletTag.LEDGER_CARD,
                onClick = {},
            )
    }
}
