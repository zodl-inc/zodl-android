package co.electriccoin.zcash.ui.screen.connectledger.common

import androidx.annotation.DrawableRes
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResource

/**
 * The body every Ledger error sheet shares, whichever sheet hosts it.
 */
interface LedgerErrorContentState {
    @get:DrawableRes
    val icon: Int
    val title: StringResource
    val message: StringResource
    val primary: ButtonState
    val secondary: ButtonState?
}
