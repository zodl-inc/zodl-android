package co.electriccoin.zcash.ui.screen.common

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import java.time.YearMonth

data class BirthdayPickerState(
    val title: StringResource?,
    val subtitle: StringResource = stringRes(R.string.restoreWallet_birthday_estimateDate_title),
    val message: StringResource,
    val logo: Int?,
    val selection: YearMonth,
    val onYearMonthChange: (YearMonth) -> Unit,
    val primaryButton: ButtonState,
    val secondaryButton: ButtonState?,
    val dialogButton: IconButtonState?,
    val onBack: () -> Unit,
)
