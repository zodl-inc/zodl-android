package co.electriccoin.zcash.ui.screen.connectledger.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue

@Composable
internal fun ColumnScope.LedgerErrorContent(
    state: LedgerErrorContentState,
    modifier: Modifier = Modifier,
    primaryModifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .weight(1f, false)
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.icon == R.drawable.ic_ledger_alert_circle) {
            Image(
                modifier = Modifier.size(44.dp),
                painter = painterResource(R.drawable.ic_ledger_alert_badge),
                contentDescription = null,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(ZashiColors.Surfaces.bgSecondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(state.icon),
                    contentDescription = null,
                    tint = ZashiColors.Text.textPrimary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.textXl,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.message.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        state.primary?.let { primary ->
            ZashiButton(
                state = primary,
                modifier = primaryModifier.fillMaxWidth(),
            )
        }
        state.secondary?.let { secondary ->
            ZashiButton(
                state = secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
