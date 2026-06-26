package co.electriccoin.zcash.ui.screen.common

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiDisclaimerState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

data class KeepOpenState(
    val subtitle: StringResource,
    val disclaimer: ZashiDisclaimerState,
    val checkboxLabel: StringResource,
    val isChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val button: ButtonState,
    val title: StringResource = stringRes(R.string.restoreInfo_title),
    val description: StringResource,
    val bullet1: StringResource = stringRes(R.string.keep_open_bullet_1),
    val bullet2: StringResource = stringRes(R.string.keep_open_bullet_2),
)
