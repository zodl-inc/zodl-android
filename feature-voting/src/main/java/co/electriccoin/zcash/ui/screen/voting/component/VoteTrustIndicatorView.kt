package co.electriccoin.zcash.ui.screen.voting.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.VoteTrustIndicator
import co.electriccoin.zcash.ui.design.R as DesignR

@Composable
fun VoteTrustIndicatorView(
    indicator: VoteTrustIndicator,
    modifier: Modifier = Modifier
) {
    val params =
        when (indicator) {
            VoteTrustIndicator.ZODL -> {
                TrustIndicatorParams(
                    label = stringRes(R.string.coinVote_pollsList_approvedByZodl),
                    note = null,
                    iconRes = DesignR.drawable.ic_item_zashi,
                    iconTint = Color.Unspecified,
                    iconSize = 24,
                    textColor = ZashiColors.Text.textPrimary
                )
            }

            VoteTrustIndicator.UNVERIFIED -> {
                TrustIndicatorParams(
                    label = stringRes(R.string.coinVote_pollsList_unverifiedSheetTitle),
                    // A user-selected custom voting config: its servers are reached without the OHTTP
                    // gateway (ZIP #10).
                    note = stringRes(R.string.coinVote_pollsList_customServerNotPrivate),
                    iconRes = DesignR.drawable.ic_info,
                    iconTint = ZashiColors.Text.textTertiary,
                    iconSize = 20,
                    textColor = ZashiColors.Text.textTertiary
                )
            }
        }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painter = painterResource(params.iconRes),
            contentDescription = null,
            tint = params.iconTint,
            modifier = Modifier.size(params.iconSize.dp)
        )
        Text(
            text = params.label.getValue(),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = params.textColor
        )
        params.note?.let { note ->
            Text(
                text = note.getValue(),
                style = ZashiTypography.textSm,
                color = params.textColor,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

private data class TrustIndicatorParams(
    val label: StringResource,
    val note: StringResource?,
    val iconRes: Int,
    val iconTint: Color,
    val iconSize: Int,
    val textColor: Color,
)
