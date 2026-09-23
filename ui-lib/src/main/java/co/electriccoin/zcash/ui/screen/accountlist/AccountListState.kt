package co.electriccoin.zcash.ui.screen.accountlist

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress

/**
 * The "Wallets & Hardware" sheet: one row per wallet plus, while a hardware vendor is still
 * unconnected, the "Connect Hardware Wallet" call to action.
 */
data class AccountListState(
    val items: List<ZashiAccountListItemState>?,
    val isLoading: Boolean,
    val addWalletButton: ButtonState?,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        val preview =
            AccountListState(
                items =
                    listOf(
                        ZashiAccountListItemState(
                            icon = R.drawable.ic_item_zashi,
                            title = stringRes("Zodl"),
                            subtitle = stringResByAddress("u1078r23uvtj8xj6dpdx"),
                            isSelected = false,
                            onClick = {}
                        ),
                        ZashiAccountListItemState(
                            icon = R.drawable.ic_item_keystone,
                            title = stringRes("Keystone"),
                            subtitle = stringResByAddress("uR3vXiqpNwqisJqK88de"),
                            isSelected = true,
                            onClick = {}
                        ),
                        ZashiAccountListItemState(
                            icon = R.drawable.ic_item_ledger,
                            title = stringRes("Ledger"),
                            subtitle = stringResByAddress("u18EgiqpBzgfeFqB6cde"),
                            isSelected = false,
                            onClick = {}
                        ),
                    ),
                isLoading = false,
                addWalletButton = ButtonState(stringRes("Connect Hardware Wallet")),
                onBack = {},
            )

        val previewMaxed = preview.copy(addWalletButton = null)

        val previewLoading =
            AccountListState(
                items = null,
                isLoading = true,
                addWalletButton = null,
                onBack = {},
            )
    }
}

data class ZashiAccountListItemState(
    @field:DrawableRes val icon: Int,
    val title: StringResource,
    val subtitle: StyledStringResource,
    val isSelected: Boolean,
    val onClick: () -> Unit
)
