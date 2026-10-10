package co.electriccoin.zcash.ui.screen.choosehwwallet

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes

data class ChooseHWWalletState(
    val title: StringResource,
    val subtitle: StringResource,
    val cards: List<HWWalletCardState>,
    val onBack: () -> Unit,
) {
    companion object {
        val preview =
            ChooseHWWalletState(
                title = stringRes("Connect Hardware Wallet"),
                subtitle =
                    stringRes(
                        "Which hardware wallet would you like to connect? " +
                            "Select your device to get started."
                    ),
                cards =
                    listOf(
                        HWWalletCardState.previewKeystone,
                        HWWalletCardState.previewLedger,
                    ),
                onBack = {},
            )

        val previewLedgerOnly = preview.copy(cards = listOf(HWWalletCardState.previewLedger))
    }
}

/**
 * One tappable vendor card; [image] is the whole card artwork, brand mark included.
 */
data class HWWalletCardState(
    val image: ImageResource,
    val contentDescription: StringResource,
    val testTag: String,
    val onClick: () -> Unit,
) {
    companion object {
        val previewKeystone =
            HWWalletCardState(
                image = imageRes(R.drawable.img_hw_wallet_keystone),
                contentDescription = stringRes("Keystone"),
                testTag = ChooseHWWalletTag.KEYSTONE_CARD,
                onClick = {},
            )

        val previewLedger =
            HWWalletCardState(
                image = imageRes(R.drawable.img_hw_wallet_ledger),
                contentDescription = stringRes("Ledger"),
                testTag = ChooseHWWalletTag.LEDGER_CARD,
                onClick = {},
            )
    }
}
