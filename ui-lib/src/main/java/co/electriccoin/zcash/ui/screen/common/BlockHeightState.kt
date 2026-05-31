package co.electriccoin.zcash.ui.screen.common

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

data class BlockHeightState(
    val title: StringResource?,
    val subtitle: StringResource = stringRes(R.string.wbh_subtitle),
    val message: StringResource = stringRes(R.string.wbh_message),
    val logo: Int?,
    val textFieldTitle: StringResource = stringRes(R.string.wbh_text_field_title),
    val textFieldHint: StringResource = stringRes(R.string.wbh_text_field_hint),
    val textFieldNote: StringResource = stringRes(R.string.wbh_text_field_note),
    val blockHeight: NumberTextFieldState,
    val primaryButton: ButtonState,
    val secondaryButton: ButtonState?,
    val dialogButton: IconButtonState?,
    val onBack: () -> Unit,
    val primaryButtonTestTag: String? = null,
    val blockHeightFieldTestTag: String? = null,
)
