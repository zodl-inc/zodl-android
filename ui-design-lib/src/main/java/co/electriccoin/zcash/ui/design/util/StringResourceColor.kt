package co.electriccoin.zcash.ui.design.util

import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.StringResourceColor.HINT_ERROR
import co.electriccoin.zcash.ui.design.util.StringResourceColor.NEGATIVE
import co.electriccoin.zcash.ui.design.util.StringResourceColor.POSITIVE
import co.electriccoin.zcash.ui.design.util.StringResourceColor.PRIMARY
import co.electriccoin.zcash.ui.design.util.StringResourceColor.QUARTERNARY
import co.electriccoin.zcash.ui.design.util.StringResourceColor.TERTIARY
import co.electriccoin.zcash.ui.design.util.StringResourceColor.WARNING

enum class StringResourceColor {
    PRIMARY,
    TERTIARY,
    POSITIVE,
    NEGATIVE,
    HINT_ERROR,
    QUARTERNARY,
    WARNING
}

@Composable
fun StringResourceColor.getColor() =
    when (this) {
        PRIMARY -> ZashiColors.Text.textPrimary
        TERTIARY -> ZashiColors.Text.textTertiary
        POSITIVE -> ZashiColors.Utility.SuccessGreen.utilitySuccess700
        NEGATIVE -> ZashiColors.Text.textError
        HINT_ERROR -> ZashiColors.Inputs.ErrorDefault.hint
        QUARTERNARY -> ZashiColors.Text.textQuaternary
        WARNING -> ZashiColors.Utility.WarningYellow.utilityOrange800
    }
