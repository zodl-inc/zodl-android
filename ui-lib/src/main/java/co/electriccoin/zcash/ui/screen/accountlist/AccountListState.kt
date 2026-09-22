package co.electriccoin.zcash.ui.screen.accountlist

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource

/**
 * The "Wallets & Hardware" sheet: one row per wallet plus, while a hardware vendor is still
 * unconnected, the "Connect Hardware Wallet" call to action.
 */
data class AccountListState(
    val items: List<ZashiAccountListItemState>?,
    val isLoading: Boolean,
    val addWalletButton: ButtonState?,
    override val onBack: () -> Unit,
) : ModalBottomSheetState

data class ZashiAccountListItemState(
    @field:DrawableRes val icon: Int,
    val title: StringResource,
    val subtitle: StyledStringResource,
    val isSelected: Boolean,
    val onClick: () -> Unit
)
