package co.electriccoin.zcash.ui.screen.accountlist

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.util.StringResource

@Immutable
data class AccountListState(
    val items: List<AccountListItem>?,
    val isLoading: Boolean,
    val addWalletButton: ButtonState?,
    override val onBack: () -> Unit,
) : ModalBottomSheetState

@Immutable
data class ZashiAccountListItemState(
    @DrawableRes val icon: Int,
    val title: StringResource,
    val subtitle: StringResource,
    val isSelected: Boolean,
    val onClick: () -> Unit
)

@Immutable
sealed interface AccountListItem {
    @Immutable
    data class Account(
        val state: ZashiAccountListItemState
    ) : AccountListItem

    @Immutable
    data class Other(
        val state: ListItemState
    ) : AccountListItem
}
