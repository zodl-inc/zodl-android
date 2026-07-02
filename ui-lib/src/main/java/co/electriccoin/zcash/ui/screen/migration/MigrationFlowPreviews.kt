package co.electriccoin.zcash.ui.screen.migration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.migration.battery.MigrationBatteryState
import co.electriccoin.zcash.ui.screen.migration.battery.MigrationBatteryView
import co.electriccoin.zcash.ui.screen.migration.notesplit.MigrationNoteSplitState
import co.electriccoin.zcash.ui.screen.migration.notesplit.MigrationNoteSplitView
import co.electriccoin.zcash.ui.screen.migration.notesplit.NoteSplitPhase
import co.electriccoin.zcash.ui.screen.migration.notification.MigrationNotificationState
import co.electriccoin.zcash.ui.screen.migration.notification.MigrationNotificationView
import co.electriccoin.zcash.ui.screen.migration.privacy.MigrationPrivacyState
import co.electriccoin.zcash.ui.screen.migration.privacy.MigrationPrivacyView
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewState
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewTransferState
import co.electriccoin.zcash.ui.screen.migration.review.MigrationReviewView
import co.electriccoin.zcash.ui.screen.migration.scheduled.MigrationScheduledState
import co.electriccoin.zcash.ui.screen.migration.scheduled.MigrationScheduledView
import co.electriccoin.zcash.ui.screen.migration.sending.MigrationSendingView
import co.electriccoin.zcash.ui.screen.migration.setup.MigrationSetupState
import co.electriccoin.zcash.ui.screen.migration.setup.MigrationSetupView
import co.electriccoin.zcash.ui.screen.migration.success.MigrationSuccessState
import co.electriccoin.zcash.ui.screen.migration.success.MigrationSuccessView

/**
 * Aggregated, side-by-side view of each migration flow's steps for eyeballing against Figma —
 * not a real screen, just wires each screen's own [androidx.compose.ui.tooling.preview.Preview]
 * state into one Row per flow. Reuse this to spot-check the whole flow at once instead of
 * clicking through each screen's individual preview.
 */
@Preview(name = "Migration – AUTOMATIC (Privacy) flow", widthDp = 3000, heightDp = 950, showBackground = true, backgroundColor = 0xFFDDDDDDL)
@Composable
private fun PrivacyFlowPreview() = ZcashTheme {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FlowStep("1 · Setup") { MigrationSetupView(previewSetupState(MigrationMode.AUTOMATIC)) }
        FlowStep("2 · Note Split") { MigrationNoteSplitView(previewNoteSplitState()) }
        FlowStep("3 · Battery") { MigrationBatteryView(previewBatteryState()) }
        FlowStep("4 · Notification") { MigrationNotificationView(previewNotificationState()) }
        FlowStep("5 · Tor Privacy") { MigrationPrivacyView(previewPrivacyState(MigrationMode.AUTOMATIC)) }
        FlowStep("6 · Review") { MigrationReviewView(previewReviewStateAutomatic()) }
        FlowStep("7 · Scheduled") { MigrationScheduledView(previewScheduledState()) }
    }
}

@Preview(name = "Migration – IMMEDIATE flow", widthDp = 2200, heightDp = 950, showBackground = true, backgroundColor = 0xFFDDDDDDL)
@Composable
private fun ImmediateFlowPreview() = ZcashTheme {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FlowStep("1 · Setup") { MigrationSetupView(previewSetupState(MigrationMode.IMMEDIATE)) }
        FlowStep("2 · Tor Privacy") { MigrationPrivacyView(previewPrivacyState(MigrationMode.IMMEDIATE)) }
        FlowStep("3 · Review") { MigrationReviewView(previewReviewStateImmediate()) }
        FlowStep("4 · Sending") { MigrationSendingView() }
        FlowStep("5 · Success") { MigrationSuccessView(previewSuccessState()) }
    }
}

@Composable
private fun FlowStep(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .width(393.dp)
                .height(852.dp)
                .border(1.dp, Color.Black)
                .background(Color.White),
        ) {
            content()
        }
    }
}

// ── Preview state fixtures ──────────────────────────────────────────────────────

private fun previewSetupState(mode: MigrationMode) = MigrationSetupState(
    orchardBalance = stringRes("12.458 ZEC"),
    fiatBalance = stringRes("$4,832.86"),
    isKeystone = false,
    mode = mode,
    onModeChange = {},
    onFindOutMore = {},
    onConfirm = {},
    onBack = {},
)

private fun previewNoteSplitState() = MigrationNoteSplitState(
    phase = NoteSplitPhase.EXPLAINER,
    isKeystone = false,
    splitAmount = stringRes("12.458 ZEC"),
    fee = stringRes("0.001 ZEC"),
    transactionId = null,
    onCopyTransactionId = {},
    onContinue = {},
    onBack = {},
)

private fun previewBatteryState() = MigrationBatteryState(
    onAllow = {},
    onSkip = {},
    onAutoSkip = {},
    onBack = {},
)

private fun previewNotificationState() = MigrationNotificationState(
    onAllow = {},
    onSkip = {},
    onAutoSkip = {},
    onBack = {},
)

private fun previewPrivacyState(mode: MigrationMode) = MigrationPrivacyState(
    mode = mode,
    useTor = mode == MigrationMode.AUTOMATIC,
    onTorToggle = {},
    onConfirm = {},
    onSkip = {},
    onBack = {},
)

private fun previewReviewStateAutomatic() = MigrationReviewState(
    mode = MigrationMode.AUTOMATIC,
    totalAmount = stringRes("12.458 ZEC"),
    estimatedDuration = stringRes("~8 min"),
    transfers = listOf(
        MigrationReviewTransferState(1, 5, stringRes("1.348 ZEC"), stringRes("$521.30"), stringRes("~10 mins")),
        MigrationReviewTransferState(2, 5, stringRes("1.052 ZEC"), stringRes("$406.86"), stringRes("~6 hours")),
        MigrationReviewTransferState(3, 5, stringRes("2.105 ZEC"), stringRes("$813.74"), stringRes("~12 hours")),
        MigrationReviewTransferState(4, 5, stringRes("1.897 ZEC"), stringRes("$733.51"), stringRes("~18 hours")),
        MigrationReviewTransferState(5, 5, stringRes("4.456 ZEC"), stringRes("$1,723.53"), stringRes("~24 hours")),
    ),
    onConfirm = {},
    onBack = {},
)

private fun previewReviewStateImmediate() = MigrationReviewState(
    mode = MigrationMode.IMMEDIATE,
    totalAmount = stringRes("12.458 ZEC"),
    estimatedDuration = stringRes("~1 min"),
    transfers = listOf(
        MigrationReviewTransferState(1, 1, stringRes("12.458 ZEC"), stringRes("$4,832.86"), stringRes("Send immediately")),
    ),
    isKeystone = false,
    fee = stringRes("0.0003 ZEC"),
    onConfirm = {},
    onBack = {},
)

private fun previewScheduledState() = MigrationScheduledState(
    totalAmount = stringRes("12.45800 ZEC"),
    transfersProgress = stringRes("0 of 5"),
    duration = stringRes("~8 min"),
    onDone = {},
)

private fun previewSuccessState() = MigrationSuccessState(
    onViewTransaction = {},
    onClose = {},
)
