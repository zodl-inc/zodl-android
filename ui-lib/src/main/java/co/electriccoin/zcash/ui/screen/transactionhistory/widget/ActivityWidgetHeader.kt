package co.electriccoin.zcash.ui.screen.transactionhistory.widget

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun ActivityWidgetHeader(state: TransactionHistoryWidgetHeaderState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = state.title.getValue(),
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
            style = ZashiTypography.textLg
        )
        if (state.button != null) {
            ZashiButton(
                state = state.button,
                defaultPrimaryColors = ZashiButtonDefaults.tertiaryColors(),
                style =
                    ZashiTypography.textSm.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = CircleShape,
                minHeight = 32.dp
            )
        } else {
            VerticalSpacer(32.dp)
        }
    }
}

data class TransactionHistoryWidgetHeaderState(
    val title: StringResource,
    val button: ButtonState?
)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ActivityWidgetHeader(
                TransactionHistoryWidgetHeaderState(
                    title = stringRes("Transactions"),
                    button =
                        ButtonState(
                            text = stringRes("See All"),
                            trailingIcon = co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right_small,
                            onClick = {}
                        )
                )
            )
        }
    }

@PreviewScreens
@Composable
private fun PreviewWithoutButton() =
    ZcashTheme {
        BlankSurface {
            ActivityWidgetHeader(
                TransactionHistoryWidgetHeaderState(
                    title = stringRes("Transactions"),
                    button = null
                )
            )
        }
    }
