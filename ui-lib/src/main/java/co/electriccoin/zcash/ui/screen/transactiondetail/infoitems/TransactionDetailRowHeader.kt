package co.electriccoin.zcash.ui.screen.transactiondetail.infoitems

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
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
fun TransactionDetailRowHeader(
    title: StringResource,
    isExpanded: Boolean,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransactionDetailTitleHeader(
            state =
                TransactionDetailInfoHeaderState(
                    title = title
                )
        )
        Spacer(Modifier.weight(1f))
        TransactionDetailButtonHeader(
            state =
                TransactionDetailInfoHeaderButtonState(
                    isExpanded = isExpanded,
                    onClick = onButtonClick
                )
        )
    }
}

@Composable
fun TransactionDetailTitleHeader(
    state: TransactionDetailInfoHeaderState,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier,
        text = state.title.getValue(),
        style = ZashiTypography.textSm,
        fontWeight = FontWeight.Medium,
        color = ZashiColors.Text.textTertiary
    )
}

@Composable
fun TransactionDetailButtonHeader(
    state: TransactionDetailInfoHeaderButtonState,
    modifier: Modifier = Modifier
) {
    ZashiButton(
        modifier = modifier.height(36.dp),
        onClick = state.onClick,
        text =
            if (state.isExpanded) {
                stringResource(
                    R.string.general_less
                )
            } else {
                stringResource(co.electriccoin.zcash.ui.design.R.string.general_more)
            },
        trailingIcon = if (state.isExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down,
        colors = ZashiButtonDefaults.tertiaryColors(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp),
        style =
            ZashiTypography.textSm.copy(
                fontWeight = FontWeight.SemiBold,
            )
    )
}

data class TransactionDetailInfoHeaderState(
    val title: StringResource,
)

data class TransactionDetailInfoHeaderButtonState(
    val isExpanded: Boolean,
    val onClick: () -> Unit,
)

@PreviewScreens
@Composable
private fun TitlePreview() =
    ZcashTheme {
        BlankSurface {
            TransactionDetailTitleHeader(
                state =
                    TransactionDetailInfoHeaderState(
                        title = stringRes("Title")
                    )
            )
        }
    }

@PreviewScreens
@Composable
private fun ButtonPreview() =
    ZcashTheme {
        BlankSurface {
            TransactionDetailButtonHeader(
                state = TransactionDetailInfoHeaderButtonState(isExpanded = true, onClick = {})
            )
        }
    }
