package co.electriccoin.zcash.ui.screen.receive

import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource

data class ReceiveState(
    val items: List<ReceiveAddressState>?,
    val isLoading: Boolean,
    val onBack: () -> Unit
)

data class ReceiveAddressState(
    val icon: Int,
    val title: StringResource,
    val subtitle: StyledStringResource,
    val isExpanded: Boolean,
    val colorMode: ColorMode,
    val infoIconButton: IconButtonState,
    val onClick: () -> Unit,
    val isShielded: Boolean,
    val onCopyClicked: () -> Unit,
    val onQrClicked: () -> Unit,
    val onRequestClicked: () -> Unit,
) {
    enum class ColorMode {
        ZASHI,

        /**
         * The indigo scheme of a hardware wallet's shielded address, Keystone and Ledger alike.
         */
        HW_WALLET,
        DEFAULT
    }
}
