package co.electriccoin.zcash.ui.screen.connectledger.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import co.electriccoin.zcash.ledger.R
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
 * went wrong; the Figma "Waiting Indicator".
 */
@Composable
internal fun LedgerInlineIssue(
    state: LedgerInlineIssueState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(INDICATOR_WIDTH.dp)
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
            modifier = Modifier.fillMaxWidth(),
            text = state.title.getValue(),
            style = ZashiTypography.textMd,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.message.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
    }
}

private const val INDICATOR_WIDTH = 280

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
                title = stringRes("Bluetooth is off on your device"),
                message = stringRes("Turn on Bluetooth in Settings to connect to your Ledger."),
            )

        val previewAccessRequired =
            LedgerInlineIssueState(
                icon = R.drawable.ic_ledger_bluetooth_on,
                title = stringRes("Zodl needs Bluetooth access to connect to your Ledger."),
                message = stringRes("Turn on Bluetooth in Settings to continue."),
            )

        val previewNoDevices =
            LedgerInlineIssueState(
                icon = R.drawable.ic_ledger_alert_circle,
                title = stringRes("No devices found"),
                message =
                    stringRes(
                        "We couldn't find any Ledger devices nearby. Make sure your Ledger " +
                            "hardware is unlocked and Bluetooth is turned on."
                    ),
            )

        val previewSomethingWentWrong =
            LedgerInlineIssueState(
                icon = R.drawable.ic_ledger_alert_circle,
                title = stringRes("Something Went Wrong"),
                message =
                    stringRes(
                        "Zodl couldn’t connect to your Ledger.\nMake sure it’s unlocked " +
                            "and nearby, then try again."
                    ),
            )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        LedgerInlineIssue(state = LedgerInlineIssueState.preview)
    }

@PreviewScreens
@Composable
private fun AccessRequiredPreview() =
    ZcashTheme {
        LedgerInlineIssue(state = LedgerInlineIssueState.previewAccessRequired)
    }

@PreviewScreens
@Composable
private fun SomethingWentWrongPreview() =
    ZcashTheme {
        LedgerInlineIssue(state = LedgerInlineIssueState.previewSomethingWentWrong)
    }
