package co.electriccoin.zcash.ui.screen.connectledger.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.connectledger.scan.LedgerDeviceScanTag

/**
 * The issue a page keeps showing behind its error sheet, so dismissing the sheet does not hide what
 * went wrong.
 */
@Composable
internal fun LedgerInlineIssue(
    state: LedgerInlineIssueState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = 24.dp)
                .testTag(LedgerDeviceScanTag.INLINE_ISSUE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(state.icon),
            contentDescription = null,
            tint = ZashiColors.Text.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.textMd,
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
    }
}

data class LedgerInlineIssueState(
    @get:DrawableRes
    val icon: Int,
    val title: StringResource,
    val message: StringResource,
) {
    companion object {
        val preview =
            LedgerInlineIssueState(
                icon = R.drawable.ic_ledger_bluetooth_off,
                title = stringRes("Bluetooth Off"),
                message = stringRes("Turn on Bluetooth in Settings to connect to your Ledger."),
            )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        LedgerInlineIssue(state = LedgerInlineIssueState.preview)
    }
