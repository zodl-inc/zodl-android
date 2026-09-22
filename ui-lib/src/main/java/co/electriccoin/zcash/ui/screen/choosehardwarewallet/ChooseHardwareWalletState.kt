package co.electriccoin.zcash.ui.screen.choosehardwarewallet

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.design.util.StringResource

data class ChooseHardwareWalletState(
    val title: StringResource,
    val subtitle: StringResource,
    val cards: List<HardwareWalletCardState>,
    val onBack: () -> Unit,
)

/**
 * One tappable vendor card. [wordmark] is drawn over [background]; a vendor whose brand mark is
 * baked into the background photo passes null.
 */
data class HardwareWalletCardState(
    @field:DrawableRes val background: Int,
    @field:DrawableRes val wordmark: Int?,
    val contentDescription: StringResource,
    val testTag: String,
    val onClick: () -> Unit,
)
