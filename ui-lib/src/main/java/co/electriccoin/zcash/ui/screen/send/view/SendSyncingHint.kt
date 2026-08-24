package co.electriccoin.zcash.ui.screen.send.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

/**
 * Shown under the balance widget while the synchronizer is not yet SYNCED: the spendable balance the
 * form validates against is only refreshed per scanned batch, so it may still move.
 */
@Composable
fun SendSyncingHint(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = stringResource(id = R.string.send_syncingBalanceHint),
        style = ZashiTypography.textXs,
        color = ZashiColors.Text.textTertiary,
        textAlign = TextAlign.Center
    )
}

@PreviewScreens
@Composable
private fun SendSyncingHintPreview() =
    ZcashTheme {
        Box(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            SendSyncingHint(Modifier.fillMaxWidth())
        }
    }
