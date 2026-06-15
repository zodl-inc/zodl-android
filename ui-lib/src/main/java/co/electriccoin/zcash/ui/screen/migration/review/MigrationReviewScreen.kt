package co.electriccoin.zcash.ui.screen.migration.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MigrationReviewScreen(args: MigrationReviewArgs) {
    val vm = koinViewModel<MigrationReviewVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { s ->
        BackHandler { s.onBack() }
        MigrationReviewView(s)
    }
}

@Composable
fun MigrationReviewView(state: MigrationReviewState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarCloseNavigation(onBack = state.onBack) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .scaffoldPadding(padding),
        ) {
            when (state.mode) {
                MigrationMode.IMMEDIATE -> ImmediateReviewContent(state)
                MigrationMode.AUTOMATIC -> PrivacyReviewContent(state)
            }
        }
    }
}

@Composable
private fun ColumnScope.ImmediateReviewContent(state: MigrationReviewState) {
    Text(
        text = "Review Transfer",
        style = ZashiTypography.header6,
        fontWeight = FontWeight.SemiBold,
        color = ZashiColors.Text.textPrimary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Your full Orchard balance will be transferred to Ironwood in a single on-chain transfer.",
        style = ZashiTypography.textSm,
        color = ZashiColors.Text.textTertiary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Your Transfer",
        style = ZashiTypography.textSm,
        fontWeight = FontWeight.SemiBold,
        color = ZashiColors.Text.textPrimary,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Once confirmed, this transfer cannot be cancelled.",
        style = ZashiTypography.textXs,
        color = ZashiColors.Text.textTertiary,
    )
    Spacer(Modifier.height(12.dp))
    state.transfers.firstOrNull()?.let { transfer ->
        TransferTimelineRow(
            transfer = transfer,
            isFirst = true,
            isLast = true,
            showTotalCount = true,
        )
    }
    Spacer(Modifier.height(16.dp))
    Spacer(Modifier.weight(1f))
    PrivacyDisclaimerCard()
    Spacer(Modifier.height(20.dp))
    ZashiButton(
        state = ButtonState(text = stringRes("Confirm"), onClick = state.onConfirm),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.PrivacyReviewContent(state: MigrationReviewState) {
    Text(
        text = "Transfer Plan",
        style = ZashiTypography.header6,
        fontWeight = FontWeight.SemiBold,
        color = ZashiColors.Text.textPrimary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Your balance splits into ${state.transfers.size} transfers over ~24 hours. Approve once and " +
            "we'll handle the rest — just keep the app running in the background. Amounts are randomized " +
            "for privacy. If we miss a window, Zodl will prompt you on next open.",
        style = ZashiTypography.textSm,
        color = ZashiColors.Text.textTertiary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Migration Progress",
        style = ZashiTypography.textSm,
        fontWeight = FontWeight.Medium,
        color = ZashiColors.Text.textPrimary,
    )
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressSummaryRow(label = "Destination", value = "Ironwood")
        ProgressSummaryRow(label = "Summary", value = "${state.transfers.size} transfers  ·  ~24 hours")
    }
    Spacer(Modifier.height(24.dp))
    state.transfers.forEachIndexed { i, transfer ->
        TransferTimelineRow(
            transfer = transfer,
            isFirst = i == 0,
            isLast = i == state.transfers.lastIndex,
            showTotalCount = false,
        )
    }
    Spacer(Modifier.weight(1f))
    ZashiButton(
        state = ButtonState(text = stringRes("Confirm"), onClick = state.onConfirm),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProgressSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ZashiTypography.textXs,
            color = ZashiColors.Text.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = ZashiTypography.textXs,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textPrimary,
        )
    }
}

@Composable
private fun TransferTimelineRow(
    transfer: MigrationReviewTransferState,
    isFirst: Boolean,
    isLast: Boolean,
    showTotalCount: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(bottom = if (isLast) 0.dp else 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) {
                if (isFirst) {
                    Column(modifier = Modifier.fillMaxHeight().padding(top = 24.dp)) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(10.dp)
                                .background(ZashiColors.Btns.Primary.btnPrimaryBg)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(2.dp)
                                .background(ZashiColors.Surfaces.strokePrimary)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = 24.dp)
                            .width(2.dp)
                            .background(ZashiColors.Surfaces.strokePrimary)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (isFirst) ZashiColors.Btns.Primary.btnPrimaryBg else ZashiColors.Surfaces.bgTertiary,
                        CircleShape
                    )
                    .border(2.dp, ZashiColors.Surfaces.bgPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${transfer.index}",
                    style = ZashiTypography.textXs,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFirst) ZashiColors.Btns.Primary.btnPrimaryFg else ZashiColors.Text.textTertiary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (showTotalCount) "Transfer ${transfer.index} of ${transfer.totalCount}" else "Transfer ${transfer.index}",
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            Text(
                text = transfer.scheduledLabel.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = transfer.amount.getValue(),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            transfer.fiatAmount?.let { fiat ->
                Text(
                    text = fiat.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun PrivacyDisclaimerCard() {
    val warningBg = Color(0xFFFEF6EE)
    val warningText = ZashiColors.Utility.WarningYellow.utilityOrange700
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(warningBg, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Privacy Disclaimer",
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.SemiBold,
                color = warningText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Your full balance will be revealed as crossing the pool boundary reveals the transaction amount. We recommend going back and selecting Migrate with Privacy instead.",
                style = ZashiTypography.textXs,
                color = warningText,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
            contentDescription = null,
            tint = warningText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@PreviewScreens
@Composable
private fun PreviewImmediate() = ZcashTheme {
    MigrationReviewView(
        state = MigrationReviewState(
            mode = MigrationMode.IMMEDIATE,
            totalAmount = stringRes("12.458 ZEC"),
            transfers = listOf(
                MigrationReviewTransferState(
                    1, 1, stringRes("12.458 ZEC"), stringRes("$4,832.86"), stringRes("Send immediately")
                ),
            ),
            onConfirm = {},
            onBack = {},
        )
    )
}

@PreviewScreens
@Composable
private fun PreviewPrivacy() = ZcashTheme {
    MigrationReviewView(
        state = MigrationReviewState(
            mode = MigrationMode.AUTOMATIC,
            totalAmount = stringRes("12.458 ZEC"),
            transfers = listOf(
                MigrationReviewTransferState(1, 5, stringRes("1.348 ZEC"), stringRes("$521.30"), stringRes("Ready now")),
                MigrationReviewTransferState(2, 5, stringRes("1.052 ZEC"), stringRes("$406.86"), stringRes("~6 hours")),
                MigrationReviewTransferState(3, 5, stringRes("2.105 ZEC"), stringRes("$813.74"), stringRes("~12 hours")),
                MigrationReviewTransferState(4, 5, stringRes("1.897 ZEC"), stringRes("$733.51"), stringRes("~18 hours")),
                MigrationReviewTransferState(5, 5, stringRes("4.456 ZEC"), stringRes("$1,723.53"), stringRes("~24 hours")),
            ),
            onConfirm = {},
            onBack = {},
        )
    )
}
