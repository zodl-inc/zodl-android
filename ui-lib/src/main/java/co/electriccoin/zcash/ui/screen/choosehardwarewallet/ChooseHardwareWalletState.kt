package co.electriccoin.zcash.ui.screen.choosehardwarewallet

import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource

data class ChooseHardwareWalletState(
    val title: StringResource,
    val subtitle: StringResource,
    val cards: List<HardwareWalletCardState>,
    val onBack: () -> Unit,
)

/**
 * One tappable vendor card; [image] is the whole card artwork, brand mark included.
 */
data class HardwareWalletCardState(
    val image: ImageResource,
    val contentDescription: StringResource,
    val testTag: String,
    val onClick: () -> Unit,
)
