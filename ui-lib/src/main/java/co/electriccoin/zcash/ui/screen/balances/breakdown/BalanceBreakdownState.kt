package co.electriccoin.zcash.ui.screen.balances.breakdown

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.util.StringResource

data class BalanceBreakdownState(
    val title: StringResource,
    val subtitle: StringResource,
    val total: BalanceBreakdownItemState,
    val pools: List<BalanceBreakdownItemState>,
    val positive: ButtonState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState

data class BalanceBreakdownItemState(
    val title: StringResource,
    val amount: Zatoshi,
    /** Fiat equivalent; `null` when currency conversion is disabled or unavailable. */
    val fiat: StringResource?,
)
