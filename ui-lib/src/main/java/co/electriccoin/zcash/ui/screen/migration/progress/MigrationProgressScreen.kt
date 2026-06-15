package co.electriccoin.zcash.ui.screen.migration.progress

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun MigrationProgressScreen() {
    val vm = koinViewModel<MigrationProgressVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { state.content?.onBack?.invoke() ?: vm.navigateBack() }
    LceRenderer(state) { MigrationProgressView(it) }
}

@Composable
fun MigrationProgressView(state: MigrationProgressState) {
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
            Text(
                text = state.title.getValue(),
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.subtitle.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(24.dp))

            if (state.totalCount > 0) {
                LinearProgressIndicator(
                    progress = { state.completedCount.toFloat() / state.totalCount },
                    modifier = Modifier.fillMaxWidth(),
                    color = ZashiColors.Btns.Brand.btnBrandFg,
                )
                Spacer(Modifier.height(8.dp))
                state.progressSummary?.let { summary ->
                    Text(
                        text = summary.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            val activeIndex = state.transfers.indexOfFirst { !it.isSent }
            state.transfers.forEachIndexed { i, transfer ->
                TransferProgressTimelineRow(
                    transfer = transfer,
                    isActive = i == activeIndex,
                    isLast = i == state.transfers.lastIndex,
                )
            }

            if (state.hasOverdue) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Sending now will delay your wallet's sync about 10 mins. To protect your privacy, we need to decouple syncing and transaction broadcasting.",
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary,
                )
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(24.dp))

            if (state.isComplete) {
                state.onDone?.let { done ->
                    ZashiButton(
                        state = ButtonState(text = stringRes("Got it"), onClick = done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                state.onSendNow?.let { send ->
                    ZashiButton(
                        state = ButtonState(text = stringRes("Send now"), onClick = send),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.onReschedule?.let { reschedule ->
                    Spacer(Modifier.height(8.dp))
                    ZashiButton(
                        state = ButtonState(text = stringRes("Re-schedule"), onClick = reschedule),
                        modifier = Modifier.fillMaxWidth(),
                        defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
                    )
                }
            }

            state.onSimulateTransfer?.let { simulate ->
                Spacer(Modifier.height(16.dp))
                ZashiButton(
                    state = ButtonState(
                        text = stringRes("[DEBUG] Simulate Next Transfer"),
                        onClick = simulate,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
                )
            }
            state.onResetMigration?.let { reset ->
                Spacer(Modifier.height(8.dp))
                ZashiButton(
                    state = ButtonState(
                        text = stringRes("[DEBUG] Reset Migration"),
                        onClick = reset,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    defaultPrimaryColors = ZashiButtonDefaults.destructive2Colors(),
                )
            }
        }
    }
}

@Composable
private fun TransferProgressTimelineRow(
    transfer: MigrationProgressTransferState,
    isActive: Boolean,
    isLast: Boolean,
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
                val connectorColor =
                    if (transfer.isSent) ZashiColors.Utility.SuccessGreen.utilitySuccess500 else ZashiColors.Surfaces.strokePrimary
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(top = 24.dp)
                        .width(2.dp)
                        .background(connectorColor)
                )
            }
            val bgColor = when {
                transfer.isSent -> ZashiColors.Utility.SuccessGreen.utilitySuccess500
                transfer.isOverdue -> ZashiColors.Utility.WarningYellow.utilityOrange500
                isActive -> ZashiColors.Btns.Primary.btnPrimaryBg
                else -> ZashiColors.Surfaces.bgTertiary
            }
            val textColor = when {
                transfer.isOverdue || isActive -> ZashiColors.Btns.Primary.btnPrimaryFg
                else -> ZashiColors.Utility.Gray.utilityGray400
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(bgColor, CircleShape)
                    .border(2.dp, ZashiColors.Surfaces.bgPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (transfer.isSent) {
                    Icon(
                        painter = painterResource(R.drawable.ic_migration_check),
                        contentDescription = null,
                        tint = ZashiColors.Btns.Primary.btnPrimaryFg,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = "${transfer.index}",
                        style = ZashiTypography.textXs,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Transfer ${transfer.index}",
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            Text(
                text = transfer.statusLabel.getValue(),
                style = ZashiTypography.textXs,
                color = if (transfer.isOverdue) ZashiColors.Utility.WarningYellow.utilityOrange500 else ZashiColors.Text.textTertiary,
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

@PreviewScreens
@Composable
private fun PreviewResume() = ZcashTheme {
    MigrationProgressView(
        state = MigrationProgressState(
            title = stringRes("Resume Migration"),
            subtitle = stringRes("Transfer 3 of 5 was scheduled 6 hours ago but wasn't sent. Send now or reschedule."),
            transfers = listOf(
                MigrationProgressTransferState(1, 5, stringRes("1.348 ZEC"), stringRes("Sent 6h ago"), false, true, stringRes("$521.30")),
                MigrationProgressTransferState(2, 5, stringRes("1.052 ZEC"), stringRes("Sent 18 min ago"), false, true, stringRes("$406.86")),
                MigrationProgressTransferState(3, 5, stringRes("2.105 ZEC"), stringRes("Overdue · 6h ago"), true, false, stringRes("$813.74")),
                MigrationProgressTransferState(4, 5, stringRes("1.897 ZEC"), stringRes("~10 hours"), false, false, stringRes("$733.51")),
                MigrationProgressTransferState(5, 5, stringRes("4.056 ZEC"), stringRes("~16 hours"), false, false, stringRes("$1,568.05")),
            ),
            completedCount = 2,
            totalCount = 5,
            isComplete = false,
            hasOverdue = true,
            progressSummary = stringRes("2 of 5 transfers complete  ~40% complete"),
            onBack = {},
            onSendNow = {},
            onReschedule = {},
        )
    )
}

@PreviewScreens
@Composable
private fun PreviewComplete() = ZcashTheme {
    MigrationProgressView(
        state = MigrationProgressState(
            title = stringRes("Migration Progress"),
            subtitle = stringRes("Your balance splits into 5 transfers over 24 hours. All transfers complete."),
            transfers = listOf(
                MigrationProgressTransferState(1, 5, stringRes("1.348 ZEC"), stringRes("Sent 24h ago"), false, true, stringRes("$521.30")),
                MigrationProgressTransferState(2, 5, stringRes("1.052 ZEC"), stringRes("Sent 18h ago"), false, true, stringRes("$406.86")),
                MigrationProgressTransferState(3, 5, stringRes("2.105 ZEC"), stringRes("Sent 12h ago"), false, true, stringRes("$813.74")),
                MigrationProgressTransferState(4, 5, stringRes("1.897 ZEC"), stringRes("Sent 6h ago"), false, true, stringRes("$733.51")),
                MigrationProgressTransferState(5, 5, stringRes("4.056 ZEC"), stringRes("Sent 18 min ago"), false, true, stringRes("$1,568.05")),
            ),
            completedCount = 5,
            totalCount = 5,
            isComplete = true,
            hasOverdue = false,
            progressSummary = stringRes("5 of 5 transfers complete  ~100% complete"),
            onBack = {},
            onDone = {},
        )
    )
}
