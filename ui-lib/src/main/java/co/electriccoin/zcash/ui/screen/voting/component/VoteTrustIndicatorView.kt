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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.screen.voting.VoteTrustIndicator

@Composable
fun VoteTrustIndicatorView(
    indicator: VoteTrustIndicator,
    modifier: Modifier = Modifier
) {
    val params =
        when (indicator) {
            VoteTrustIndicator.ZODL ->
                TrustIndicatorParams(
                    labelRes = R.string.vote_poll_card_trust_zodl,
                    iconRes = R.drawable.ic_vote_check_verified_solid,
                    iconTint = ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                    textColor = ZashiColors.Text.textPrimary
                )

            VoteTrustIndicator.UNVERIFIED ->
                TrustIndicatorParams(
                    labelRes = R.string.vote_poll_card_trust_unverified,
                    iconRes = R.drawable.ic_alert_circle,
                    iconTint = ZashiColors.Utility.WarningYellow.utilityOrange700,
                    textColor = ZashiColors.Utility.WarningYellow.utilityOrange700
                )
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
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(params.labelRes),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = params.textColor
        )
    }
}

private data class TrustIndicatorParams(
    val labelRes: Int,
    val iconRes: Int,
    val iconTint: Color,
    val textColor: Color,
)
