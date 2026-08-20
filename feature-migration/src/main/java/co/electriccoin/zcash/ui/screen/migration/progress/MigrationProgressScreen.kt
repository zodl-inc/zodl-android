package co.electriccoin.zcash.ui.screen.migration.progress

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import co.electriccoin.zcash.ui.screen.migration.component.MigrationPreparationDetailsBottomSheet
import org.koin.androidx.compose.koinViewModel
import co.electriccoin.zcash.ui.design.R as DesignR

@Composable
fun MigrationProgressScreen() {
    val vm = koinViewModel<MigrationProgressVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { state.content?.onBack?.invoke() ?: vm.navigateBack() }
    LceRenderer(
        state = state,
        // Guarded on content == null (MOB-1623) so a later isLoading=true (e.g. a retry/refresh)
        // doesn't flash the full-screen spinner back over content that's already on screen.
        loading = { isLoading -> if (isLoading && state.content == null) CircularScreenProgressIndicator() },
    ) { MigrationProgressView(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationProgressView(state: MigrationProgressState) {
    // Hoisted here (a real @Composable), matching MigrationReviewScreen's identical pattern —
    // purely a "is the sheet open" UI toggle, not VM-owned state.
    var isShowingPreparationDetails by remember { mutableStateOf(false) }
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarCloseNavigation(onBack = state.onBack) },
            )
        }
    ) { padding ->
        Column(
            modifier =
                Modifier
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
            state.balanceTracker?.let { tracker ->
                MigrationBalanceTrackerCard(tracker)
                Spacer(Modifier.height(20.dp))
            }

            Column {
                // A multi-step note-split collapses into a single "Split Balance" row with a
                // "Show details" sheet (Figma "PR App Designs Q3'26" node 5207:16023, 2026-08-03,
                // mirroring MigrationReviewScreen's identical threshold) — state.preparations is
                // empty whenever state.preparationsSummary is non-null (see MigrationProgressState's
                // doc), so only one of these two branches ever renders.
                val summary = state.preparationsSummary
                if (summary != null) {
                    // Figma shows the total amount on this collapsed row too (unlike the
                    // superseded inline-expand design, which omitted it as "internal plumbing").
                    TransferProgressTimelineRow(
                        title = stringRes(DesignR.string.migration_common_splitBalanceTitle).getValue(),
                        statusLabel = summary.statusLabel,
                        isReadyNow = summary.isReadyNow,
                        amount = state.totalAmount,
                        fiatAmount = state.totalFiatAmount,
                        state = if (summary.isSent) TransferRowState.DONE else TransferRowState.ACTIVE,
                        isLast = state.transfers.isEmpty(),
                        onShowDetails = { isShowingPreparationDetails = true },
                    )
                } else {
                    // Render one "Split balance N" row per note-split (preparation) transaction,
                    // in broadcast/schedule order. When preparations is empty (single-note
                    // wallets), this block renders nothing and the transfers start immediately.
                    val firstUnsentPrepIndex = state.preparations.indexOfFirst { !it.isSent }
                    state.preparations.forEachIndexed { i, prep ->
                        // Preparation rows never surface an attention state.
                        val rowState =
                            when {
                                prep.isSent -> TransferRowState.DONE
                                i == firstUnsentPrepIndex -> TransferRowState.ACTIVE
                                else -> TransferRowState.IDLE
                            }
                        TransferProgressTimelineRow(
                            title =
                                stringRes(DesignR.string.migration_common_splitBalanceNumbered, prep.number)
                                    .getValue(),
                            statusLabel = prep.statusLabel,
                            isReadyNow = prep.isReadyNow,
                            amount = null,
                            fiatAmount = null,
                            state = rowState,
                            isLast = i == state.preparations.lastIndex && state.transfers.isEmpty(),
                        )
                    }
                }
                val activeIndex = state.transfers.indexOfFirst { !it.isSent }
                state.transfers.forEachIndexed { i, transfer ->
                    // Priority mirrors the row painter: sent wins, then genuine attention, then active.
                    val rowState =
                        when {
                            transfer.isSent -> TransferRowState.DONE
                            transfer.isAttention -> TransferRowState.ATTENTION
                            i == activeIndex -> TransferRowState.ACTIVE
                            else -> TransferRowState.IDLE
                        }
                    TransferProgressTimelineRow(
                        title = stringRes(DesignR.string.migration_common_transferTitle, transfer.index).getValue(),
                        statusLabel = transfer.statusLabel,
                        isReadyNow = transfer.isReadyNow,
                        amount = transfer.amount,
                        fiatAmount = transfer.fiatAmount,
                        index = transfer.index,
                        state = rowState,
                        isLast = i == state.transfers.lastIndex,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // No Send-now / Re-schedule buttons: the engine drives execution and the foreground
            // pass sends silently while this screen is open — the screen is a pure live status
            // view now. The only button is "Got it" on completion; genuine attention states
            // (expired / unprovable anchor) surface via the home banner → reschedule flow.
            Spacer(Modifier.height(24.dp))

            if (state.isComplete) {
                state.onDone?.let { done ->
                    ZashiButton(
                        state = ButtonState(text = stringRes(DesignR.string.migration_common_gotIt), onClick = done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    MigrationPreparationDetailsBottomSheet(
        details =
            state.preparationDetails
                ?.takeIf { isShowingPreparationDetails }
                ?.copy(onDismiss = { isShowingPreparationDetails = false }),
    )
}

/**
 * The live Orchard → Ironwood balance-tracker card (Figma "PR App Designs Q3'26" node 3480:7638),
 * shown above the transfer timeline so the migration's real-time progress is visible at a glance.
 */
@Composable
private fun MigrationBalanceTrackerCard(tracker: MigrationProgressBalanceTracker) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ZashiColors.Surfaces.bgSecondary, RoundedCornerShape(16.dp))
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringRes(DesignR.string.migrationProgress_orchardLabel).getValue(),
                style = ZashiTypography.textXs,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textTertiary,
            )
            Text(
                text = tracker.orchardAmount.getValue(),
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            tracker.orchardFiatAmount?.let {
                Text(
                    text = it.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary,
                )
            }
        }
        Icon(
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ZashiColors.Text.textTertiary,
            modifier =
                Modifier
                    .padding(horizontal = 12.dp)
                    .size(16.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = stringRes(DesignR.string.migrationProgress_ironwoodLabel).getValue(),
                style = ZashiTypography.textXs,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textTertiary,
            )
            Text(
                text = tracker.ironwoodAmount.getValue(),
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            tracker.ironwoodFiatAmount?.let {
                Text(
                    text = it.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary,
                )
            }
        }
    }
}

/**
 * Visual state of a single timeline row, resolved by priority: a sent row always paints as
 * [DONE], then a genuine [ATTENTION] state, then the currently-[ACTIVE] row, else [IDLE].
 */
private enum class TransferRowState { DONE, ATTENTION, ACTIVE, IDLE }

@Suppress("LongParameterList")
@Composable
private fun TransferProgressTimelineRow(
    title: String,
    statusLabel: StringResource,
    isReadyNow: Boolean,
    amount: StringResource?,
    fiatAmount: StringResource?,
    state: TransferRowState,
    isLast: Boolean,
    index: Int = 0,
    @DrawableRes icon: Int? = null,
    // Non-null only for the collapsed "Split Balance" summary row — renders a "Show details ⌄"
    // line under the subtitle (Figma "PR App Designs Q3'26" node 5207:16023), not a whole-row
    // click target.
    onShowDetails: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(bottom = if (isLast) 0.dp else 12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) {
                val connectorColor =
                    if (state == TransferRowState.DONE) {
                        ZashiColors.Utility.SuccessGreen.utilitySuccess500
                    } else {
                        ZashiColors.Surfaces.strokePrimary
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .padding(top = 24.dp)
                            .width(2.dp)
                            .background(connectorColor)
                )
            }
            val bgColor =
                when (state) {
                    TransferRowState.DONE -> ZashiColors.Utility.SuccessGreen.utilitySuccess500
                    TransferRowState.ATTENTION -> ZashiColors.Utility.WarningYellow.utilityOrange500
                    TransferRowState.ACTIVE -> ZashiColors.Btns.Primary.btnPrimaryBg
                    TransferRowState.IDLE -> ZashiColors.Surfaces.bgTertiary
                }
            val textColor =
                when (state) {
                    TransferRowState.ATTENTION, TransferRowState.ACTIVE -> ZashiColors.Btns.Primary.btnPrimaryFg
                    TransferRowState.DONE, TransferRowState.IDLE -> ZashiColors.Utility.Gray.utilityGray400
                }
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .background(bgColor, CircleShape)
                        .border(2.dp, ZashiColors.Surfaces.bgPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state == TransferRowState.DONE -> {
                        Icon(
                            painter = painterResource(co.electriccoin.zcash.migration.R.drawable.ic_migration_check),
                            contentDescription = null,
                            tint = ZashiColors.Btns.Primary.btnPrimaryFg,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    icon != null -> {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    else -> {
                        Text(
                            text = "$index",
                            style = ZashiTypography.textXs,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            Text(
                text = statusLabel.getValue(),
                style = ZashiTypography.textXs,
                color =
                    when {
                        state == TransferRowState.ATTENTION -> ZashiColors.Utility.WarningYellow.utilityOrange500

                        // "Ready now" is the one row subtitle Figma renders in the primary text
                        // color instead of the muted gray every other row uses.
                        isReadyNow -> ZashiColors.Text.textPrimary

                        else -> ZashiColors.Text.textTertiary
                    },
            )
            onShowDetails?.let { onClick ->
                Row(
                    modifier =
                        Modifier
                            .clickable(onClick = onClick)
                            .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringRes(DesignR.string.migration_common_showDetails).getValue(),
                        style = ZashiTypography.textXs,
                        fontWeight = FontWeight.SemiBold,
                        color = ZashiColors.Text.textPrimary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_down),
                        contentDescription = null,
                        tint = ZashiColors.Text.textPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            amount?.let {
                Text(
                    text = it.getValue(),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Text.textPrimary,
                )
            }
            fiatAmount?.let { fiat ->
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
private fun PreviewInProgress() =
    ZcashTheme {
        MigrationProgressView(
            state =
                MigrationProgressState(
                    title = stringRes("Migration Progress"),
                    subtitle =
                        stringRes(
                            "Your balance splits into 5 transfers over ~24 h. There are 3 remaining transfers."
                        ),
                    totalAmount = stringRes("10.458 ZEC"),
                    totalFiatAmount = stringRes("$4,053.46"),
                    transfers =
                        listOf(
                            MigrationProgressTransferState(
                                index = 1,
                                amount = stringRes("1.348 ZEC"),
                                statusLabel = stringRes("Sent"),
                                isReadyNow = false,
                                isAttention = false,
                                isSent = true,
                                fiatAmount = stringRes("$521.30"),
                            ),
                            MigrationProgressTransferState(
                                index = 2,
                                amount = stringRes("1.052 ZEC"),
                                statusLabel = stringRes("Ready now"),
                                isReadyNow = true,
                                isAttention = false,
                                isSent = false,
                                fiatAmount = stringRes("$406.86"),
                            ),
                            MigrationProgressTransferState(
                                index = 3,
                                amount = stringRes("2.105 ZEC"),
                                statusLabel = stringRes("~2 h"),
                                isReadyNow = false,
                                isAttention = false,
                                isSent = false,
                                fiatAmount = stringRes("$813.74"),
                            ),
                            MigrationProgressTransferState(
                                index = 4,
                                amount = stringRes("1.897 ZEC"),
                                statusLabel = stringRes("~5 h"),
                                isReadyNow = false,
                                isAttention = false,
                                isSent = false,
                                fiatAmount = stringRes("$733.51"),
                            ),
                            MigrationProgressTransferState(
                                index = 5,
                                amount = stringRes("4.056 ZEC"),
                                statusLabel = stringRes("Needs reschedule"),
                                isReadyNow = false,
                                isAttention = true,
                                isSent = false,
                                fiatAmount = stringRes("$1,568.05"),
                            ),
                        ),
                    isComplete = false,
                    onBack = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun PreviewComplete() =
    ZcashTheme {
        MigrationProgressView(
            state =
                MigrationProgressState(
                    title = stringRes("Migration Progress"),
                    subtitle = stringRes("Your balance splits into 5 transfers over 24 hours. All transfers complete."),
                    totalAmount = stringRes("10.458 ZEC"),
                    totalFiatAmount = stringRes("$4,053.46"),
                    transfers =
                        listOf(
                            MigrationProgressTransferState(
                                index = 1,
                                amount = stringRes("1.348 ZEC"),
                                statusLabel = stringRes("Sent ~24 h ago"),
                                isReadyNow = false,
                                isAttention = false,
                                isSent = true,
                                fiatAmount = stringRes("$521.30"),
                            ),
                            MigrationProgressTransferState(
                                index = 2,
                                amount = stringRes("1.052 ZEC"),
                                statusLabel = stringRes("Sent ~18 h ago"),
                                isReadyNow = false,
                                isAttention = false,
                                isSent = true,
                                fiatAmount = stringRes("$406.86"),
                            ),
                            MigrationProgressTransferState(
                                index = 3,
                                amount = stringRes("2.105 ZEC"),
                                statusLabel = stringRes("Sent ~12 h ago"),
                                isReadyNow = false,
                                isAttention = false,
                                isSent = true,
                                fiatAmount = stringRes("$813.74"),
                            ),
                            MigrationProgressTransferState(
                                index = 4,
                                amount = stringRes("1.897 ZEC"),
                                statusLabel = stringRes("Sent ~6 h ago"),
                                isReadyNow = false,
                                isAttention = false,
                                isSent = true,
                                fiatAmount = stringRes("$733.51"),
                            ),
                            MigrationProgressTransferState(
                                index = 5,
                                amount = stringRes("4.056 ZEC"),
                                statusLabel = stringRes("Sent"),
                                isReadyNow = false,
                                isAttention = false,
                                isSent = true,
                                fiatAmount = stringRes("$1,568.05"),
                            ),
                        ),
                    isComplete = true,
                    onBack = {},
                    onDone = {},
                )
        )
    }
