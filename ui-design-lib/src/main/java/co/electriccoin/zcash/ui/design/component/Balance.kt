package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.StringResource.Companion.NUMBER_FORMAT_LOCALE
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import java.text.DecimalFormatSymbols

/**
 * This accepts string with balance and displays it in the UI component styled according to the design
 * requirements. The function displays the balance within two parts.
 *
 * @param balance complete balance
 * @param isHideBalances Flag referring about the balance being hidden or not
 * @param hiddenBalancePlaceholder String holding the placeholder for the hidden balance
 * @param textStyle Styles for the integer and floating part of the balance
 * @param textColor Optional color to modify the default font color from [textStyle]
 * @param modifier Modifier to modify the Text UI element as needed
 */
@Suppress("LongParameterList")
@Composable
fun StyledBalance(
    balance: Zatoshi,
    modifier: Modifier = Modifier,
    isHideBalances: Boolean = false,
    showDust: Boolean = true,
    hiddenBalancePlaceholder: String = stringResource(id = R.string.general_hideBalancesMost),
    textColor: Color = Color.Unspecified,
    textStyle: BalanceTextStyle = StyledBalanceDefaults.textStyles(),
) {
    val content =
        if (isHideBalances) {
            buildAnnotatedString {
                withStyle(
                    style = textStyle.mostSignificantPart.toSpanStyle()
                ) {
                    append(hiddenBalancePlaceholder)
                }
            }
        } else {
            val balanceSplit = splitBalance(balance)

            buildAnnotatedString {
                withStyle(
                    style = textStyle.mostSignificantPart.toSpanStyle()
                ) {
                    append(balanceSplit.first)
                }
                if (showDust) {
                    withStyle(
                        style = textStyle.leastSignificantPart.toSpanStyle()
                    ) {
                        append(balanceSplit.second)
                    }
                }
            }
        }

    val resultModifier =
        Modifier
            .basicMarquee()
            .then(modifier)

    SelectionContainer {
        Text(
            text = content,
            color = textColor,
            maxLines = 1,
            modifier = resultModifier
        )
    }
}

private const val CUT_POSITION_OFFSET = 4

@Composable
private fun splitBalance(zatoshi: Zatoshi): Pair<String, String> {
    val balance = stringRes(zatoshi).getValue()
    val cutPosition =
        balance
            .indexOf(
                startIndex = 0,
                char = DecimalFormatSymbols(NUMBER_FORMAT_LOCALE).decimalSeparator,
                ignoreCase = true
            ).let { separatorPosition ->
                if (separatorPosition + CUT_POSITION_OFFSET < balance.length) {
                    separatorPosition + CUT_POSITION_OFFSET
                } else {
                    balance.length
                }
            }

    val firstPart =
        buildString {
            append(
                balance.take(cutPosition)
            )
        }

    val secondPart = balance.substring(startIndex = cutPosition)

    return Pair(firstPart, secondPart)
}

data class BalanceTextStyle(
    val mostSignificantPart: TextStyle,
    val leastSignificantPart: TextStyle
)

object StyledBalanceDefaults {
    @Stable
    @Composable
    fun textStyles(
        mostSignificantPart: TextStyle = ZcashTheme.extendedTypography.balanceWidgetStyles.first,
        leastSignificantPart: TextStyle = ZcashTheme.extendedTypography.balanceWidgetStyles.second,
    ) = BalanceTextStyle(
        mostSignificantPart = mostSignificantPart,
        leastSignificantPart = leastSignificantPart
    )
}

@Preview
@Composable
private fun StyledBalancePreview() =
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            Column {
                StyledBalance(
                    balance = Zatoshi(123456789),
                    isHideBalances = false,
                    modifier = Modifier
                )
            }
        }
    }

@Preview
@Composable
private fun HiddenStyledBalancePreview() =
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            Column {
                StyledBalance(
                    balance = Zatoshi(123456),
                    isHideBalances = true,
                    modifier = Modifier
                )
            }
        }
    }
