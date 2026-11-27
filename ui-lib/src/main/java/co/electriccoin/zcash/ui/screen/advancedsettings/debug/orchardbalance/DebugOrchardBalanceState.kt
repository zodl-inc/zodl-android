package co.electriccoin.zcash.ui.screen.advancedsettings.debug.orchardbalance

import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.StringResource

data class DebugOrchardBalanceState(
    val currentBalance: StringResource,
    val zecInput: TextFieldState,
    val setBalance: ButtonState,
    val onBack: () -> Unit,
)
