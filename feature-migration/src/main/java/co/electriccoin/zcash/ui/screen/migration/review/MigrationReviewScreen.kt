package co.electriccoin.zcash.ui.screen.migration.review

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.migration.MigrationKeystoneRound
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.migration.MigrationPreparationDetails
import co.electriccoin.zcash.ui.common.model.migration.MigrationPreparationStepDetail
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIcons
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIconsState
import co.electriccoin.zcash.ui.screen.migration.component.MigrationFailureBottomSheet
import co.electriccoin.zcash.ui.screen.migration.component.MigrationPreparationDetailsBottomSheet
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import co.electriccoin.zcash.ui.design.R as DesignR

@Composable
fun MigrationReviewScreen(args: MigrationReviewArgs) {
    val vm = koinViewModel<MigrationReviewVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Hoisted outside LceRenderer (matching MigrationProgressScreen) so back-navigation is always
    // available, even when state.content is permanently null (e.g. NothingToMigrate on propose —
    // see the 2026-08-02 stale-banner/dead-end bug: without this, that failure path left the user
    // on a blank screen with no back arrow and no back gesture).
    BackHandler { state.content?.onBack?.invoke() ?: vm.navigateBack() }
    LceRenderer(
        state = state,
        // MOB-1623: the default loading slot is empty, so this screen showed a black frame while
        // the plan proposal/fetch was in flight. Guarded on content == null so a later
        // isLoading=true (e.g. a retry) doesn't flash the spinner back over content already shown.
        loading = { isLoading -> if (isLoading && state.content == null) CircularScreenProgressIndicator() },
    ) { s ->
        MigrationReviewView(s)
    }
}

@Composable
fun MigrationReviewView(state: MigrationReviewState) {
    // Purely a "is the details sheet open" UI toggle — mirrors MigrationProgressScreen's identical
    // hoisted local state for the same shared sheet.
    var isShowingPreparationDetails by remember { mutableStateOf(false) }
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState),
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
                navigationAction = { ZashiTopAppBarBackNavigation(onBack = state.onBack) },
            )
        }
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = ZashiDimensions.Spacing.spacing3xl),
            ) {
                when (state.mode) {
                    MigrationMode.IMMEDIATE -> {
                        ImmediateReviewContent(
                            state = state,
                            paddingValues = padding
                        )
                    }

                    MigrationMode.AUTOMATIC -> {
                        PrivacyReviewContent(
                            state = state,
                            paddingValues = padding,
                            onShowPreparationDetails = { isShowingPreparationDetails = true },
                        )
                    }
                }
            }
        }
    }
    MigrationFailureBottomSheet(state.failureSheet)
    MigrationPreparationDetailsBottomSheet(
        details =
            state.preparationDetails
                ?.takeIf { isShowingPreparationDetails }
                ?.copy(onDismiss = { isShowingPreparationDetails = false }),
    )
}

@Composable
private fun ImmediateReviewContent(
    state: MigrationReviewState,
    paddingValues: PaddingValues
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding() + ZashiDimensions.Spacing.spacing3xl),
    ) {
        // Content scrolls in this weighted region so the Confirm button stays pinned to the bottom
        // of the screen (matching PrivacyReviewContent), rather than floating right under the short
        // details card.
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = paddingValues.calculateTopPadding() + ZashiDimensions.Spacing.spacingLg),
        ) {
            WalletHeaderIcons(
                state =
                    WalletHeaderIconsState(
                        isKeystone = state.isKeystone,
                        badgeIcon = R.drawable.ic_migration_coins_swap,
                    )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringRes(DesignR.string.migrationReview_title).getValue(),
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(DesignR.string.migrationReview_immediateBody).getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
            ImmediateDetailsCard(amount = state.totalAmount, fee = state.fee)
        }
        Spacer(Modifier.height(24.dp))
        ZashiButton(
            state =
                ButtonState(
                    text =
                        stringRes(
                            if (state.isConfirming) {
                                DesignR.string.migrationReview_signing
                            } else {
                                DesignR.string.migrationReview_confirm
                            }
                        ),
                    isEnabled = !state.isConfirming,
                    isLoading = state.isConfirming,
                    onClick = state.onConfirm,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ImmediateDetailsCard(
    amount: StringResource,
    fee: StringResource?,
    // Overridable so callers whose [fee] is a placeholder (no real per-transfer fee field exists
    // on TransferProposal — see MigrationTransferReviewVM) can be honest that it's an estimate,
    // without relabeling the real, exact fee IMMEDIATE mode shows (Proposal.totalFeeRequired()).
    feeLabel: String = stringRes(DesignR.string.migrationReview_feeLabel).getValue(),
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ZashiColors.Surfaces.bgSecondary, RoundedCornerShape(16.dp)),
    ) {
        ImmediateDetailsRow(
            label = stringRes(DesignR.string.migrationReview_amountLabel).getValue(),
            value = amount.getValue(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ZashiColors.Surfaces.bgPrimary),
        )
        ImmediateDetailsRow(label = feeLabel, value = fee?.getValue().orEmpty())
    }
}

@Composable
internal fun ImmediateDetailsRow(label: String, value: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Text(
            text = value,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textPrimary,
        )
    }
}

@Composable
private fun PrivacyReviewContent(
    state: MigrationReviewState,
    paddingValues: PaddingValues,
    onShowPreparationDetails: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding() + ZashiDimensions.Spacing.spacingLg,
                    bottom = paddingValues.calculateBottomPadding() + ZashiDimensions.Spacing.spacing3xl
                ),
    ) {
        Text(
            text = stringRes(DesignR.string.migrationReview_confirmTransferPlanTitle).getValue(),
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text =
                stringRes(
                    DesignR.string.migrationReview_privacyBody,
                    state.transfers.size,
                    state.estimatedDuration.getValue()
                ).getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(Modifier.height(24.dp))
        state.keystoneRound?.let { round ->
            Text(
                text = stringRes(DesignR.string.migrationReview_roundOfTotal, round.current, round.total).getValue(),
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
        }
        // Only this list scrolls when it doesn't fit — header and Confirm button stay pinned.
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (state.preparationDetails != null) {
                // Multi-note wallet: collapse the per-preparation "Split balance N" rows into a
                // single "Split Balance" summary row with a "Show details" sheet (Figma "PR App
                // Designs Q3'26" node 5207:16023, 2026-08-03 — supersedes the earlier inline
                // chevron-expand design). Amount IS shown here (unlike the superseded design,
                // which omitted it) — Figma's summary row carries the same total/fiat pair every
                // other row does.
                item {
                    TransferTimelineRow(
                        title = stringRes(DesignR.string.migration_common_splitBalanceTitle).getValue(),
                        subtitle = state.preparationsSummarySubtitle ?: stringRes(""),
                        amount = state.totalAmount,
                        fiatAmount = state.totalFiatAmount,
                        // Figma PR App Designs Q3'26, node 5596:55884 (verified live): the Split
                        // Balance row uses the coins-swap/split icon — Transfer rows below it get
                        // numbered circle markers instead (already handled via the `index` param).
                        icon = R.drawable.ic_migration_coins_swap,
                        isFirst = true,
                        isLast = false,
                        onShowDetails = onShowPreparationDetails,
                    )
                }
            } else if (state.preparations.isNotEmpty()) {
                // Exactly one preparation: nothing to collapse, render the single "Split balance 1"
                // row as before.
                items(state.preparations) { prep ->
                    TransferTimelineRow(
                        title = stringRes(DesignR.string.migration_common_splitBalanceNumbered, prep.number).getValue(),
                        subtitle = prep.scheduledLabel,
                        amount = null,
                        fiatAmount = null,
                        icon = R.drawable.ic_migration_coins_swap,
                        isFirst = prep.number == 1,
                        isLast = false,
                    )
                }
            } else {
                // Single-note wallet (no split needed): keep the original collapsed "Split Balance"
                // row as a fallback so no regression on zero-preparations plans.
                item {
                    TransferTimelineRow(
                        title = stringRes(DesignR.string.migration_common_splitBalanceTitle).getValue(),
                        subtitle = stringRes("Ready now"),
                        amount = state.totalAmount,
                        fiatAmount = state.totalFiatAmount,
                        icon = R.drawable.ic_migration_coins_swap,
                        isFirst = true,
                        isLast = state.transfers.isEmpty(),
                    )
                }
            }
            items(state.transfers) { transfer ->
                TransferTimelineRow(
                    title = stringRes(DesignR.string.migration_common_transferTitle, transfer.index).getValue(),
                    subtitle = transfer.scheduledLabel,
                    amount = transfer.amount,
                    fiatAmount = transfer.fiatAmount,
                    index = transfer.index,
                    isFirst = false,
                    isLast = transfer.index == state.transfers.size,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        ZashiButton(
            state =
                ButtonState(
                    // Figma node 5596:55122: "Start Migration" for the idle state (AUTOMATIC
                    // mode only — ImmediateReviewContent's "Confirm" is a different screen/copy).
                    text =
                        stringRes(
                            if (state.isConfirming) {
                                DesignR.string.migrationReview_signing
                            } else {
                                DesignR.string.migrationReview_startMigration
                            }
                        ),
                    isEnabled = !state.isConfirming,
                    isLoading = state.isConfirming,
                    onClick = state.onConfirm,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun TransferTimelineRow(
    title: String,
    subtitle: StringResource,
    amount: StringResource?,
    fiatAmount: StringResource?,
    isFirst: Boolean,
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
                if (isFirst) {
                    // A weighted Box nested inside a Column here doesn't get a usable intrinsic
                    // height from the parent Row's IntrinsicSize.Min pass (weight is only
                    // meaningful once real constraints are known), so the "rest of the line"
                    // segment below the short active stub collapsed to ~0px. Paint both segments
                    // in one fillMaxHeight() Box instead — drawBehind uses the box's actual
                    // resolved size, sidestepping the intrinsic-measurement pass entirely.
                    val activeColor = ZashiColors.Btns.Primary.btnPrimaryBg
                    val inactiveColor = ZashiColors.Surfaces.strokePrimary
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .padding(top = 24.dp)
                                .width(2.dp)
                                .drawBehind {
                                    val activeHeight = 10.dp.toPx().coerceAtMost(size.height)
                                    drawRect(color = activeColor, size = Size(size.width, activeHeight))
                                    if (size.height > activeHeight) {
                                        drawRect(
                                            color = inactiveColor,
                                            topLeft = Offset(0f, activeHeight),
                                            size = Size(size.width, size.height - activeHeight),
                                        )
                                    }
                                }
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .padding(top = 24.dp)
                                .width(2.dp)
                                .background(ZashiColors.Surfaces.strokePrimary)
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .background(
                            if (isFirst) ZashiColors.Btns.Primary.btnPrimaryBg else ZashiColors.Surfaces.bgTertiary,
                            CircleShape
                        ).border(2.dp, ZashiColors.Surfaces.bgPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = if (isFirst) ZashiColors.Btns.Primary.btnPrimaryFg else ZashiColors.Text.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(
                        text = "$index",
                        style = ZashiTypography.textXs,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isFirst) ZashiColors.Btns.Primary.btnPrimaryFg else ZashiColors.Text.textTertiary,
                    )
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
                text = subtitle.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
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
private fun PreviewImmediate() =
    ZcashTheme {
        MigrationReviewView(
            state =
                MigrationReviewState(
                    mode = MigrationMode.IMMEDIATE,
                    totalAmount = stringRes("12.458 ZEC"),
                    estimatedDuration = stringRes("~1 min"),
                    transfers =
                        listOf(
                            MigrationReviewTransferState(
                                1,
                                1,
                                stringRes("12.458 ZEC"),
                                stringRes("$4,832.86"),
                                stringRes("Send immediately")
                            ),
                        ),
                    isKeystone = false,
                    fee = stringRes("0.0003 ZEC"),
                    onConfirm = {},
                    onBack = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun PreviewPrivacy() =
    ZcashTheme {
        MigrationReviewView(
            state =
                MigrationReviewState(
                    mode = MigrationMode.AUTOMATIC,
                    totalAmount = stringRes("12.458 ZEC"),
                    estimatedDuration = stringRes("~8 min"),
                    transfers =
                        listOf(
                            MigrationReviewTransferState(
                                index = 1,
                                totalCount = 5,
                                amount = stringRes("1.348 ZEC"),
                                fiatAmount = stringRes("$521.30"),
                                scheduledLabel = stringRes("~10 mins"),
                            ),
                            MigrationReviewTransferState(
                                index = 2,
                                totalCount = 5,
                                amount = stringRes("1.052 ZEC"),
                                fiatAmount = stringRes("$406.86"),
                                scheduledLabel = stringRes("~6 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 3,
                                totalCount = 5,
                                amount = stringRes("2.105 ZEC"),
                                fiatAmount = stringRes("$813.74"),
                                scheduledLabel = stringRes("~12 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 4,
                                totalCount = 5,
                                amount = stringRes("1.897 ZEC"),
                                fiatAmount = stringRes("$733.51"),
                                scheduledLabel = stringRes("~18 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 5,
                                totalCount = 5,
                                amount = stringRes("4.456 ZEC"),
                                fiatAmount = stringRes("$1,723.53"),
                                scheduledLabel = stringRes("~24 hours"),
                            ),
                        ),
                    onConfirm = {},
                    onBack = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun PreviewPrivacyWithKeystoneRound() =
    ZcashTheme {
        MigrationReviewView(
            state =
                MigrationReviewState(
                    mode = MigrationMode.AUTOMATIC,
                    totalAmount = stringRes("12.458 ZEC"),
                    estimatedDuration = stringRes("~8 min"),
                    transfers =
                        listOf(
                            MigrationReviewTransferState(
                                index = 1,
                                totalCount = 5,
                                amount = stringRes("1.348 ZEC"),
                                fiatAmount = stringRes("$521.30"),
                                scheduledLabel = stringRes("~10 mins"),
                            ),
                            MigrationReviewTransferState(
                                index = 2,
                                totalCount = 5,
                                amount = stringRes("1.052 ZEC"),
                                fiatAmount = stringRes("$406.86"),
                                scheduledLabel = stringRes("~6 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 3,
                                totalCount = 5,
                                amount = stringRes("2.105 ZEC"),
                                fiatAmount = stringRes("$813.74"),
                                scheduledLabel = stringRes("~12 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 4,
                                totalCount = 5,
                                amount = stringRes("1.897 ZEC"),
                                fiatAmount = stringRes("$733.51"),
                                scheduledLabel = stringRes("~18 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 5,
                                totalCount = 5,
                                amount = stringRes("4.456 ZEC"),
                                fiatAmount = stringRes("$1,723.53"),
                                scheduledLabel = stringRes("~24 hours"),
                            ),
                        ),
                    isKeystone = true,
                    keystoneRound = MigrationKeystoneRound(current = 1, total = 4),
                    onConfirm = {},
                    onBack = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun PreviewPrivacyWithPreparations() =
    ZcashTheme {
        MigrationReviewView(
            state =
                MigrationReviewState(
                    mode = MigrationMode.AUTOMATIC,
                    totalAmount = stringRes("12.458 ZEC"),
                    estimatedDuration = stringRes("~8 min"),
                    preparations =
                        listOf(
                            MigrationReviewPreparationState(1, stringRes("Ready now")),
                            MigrationReviewPreparationState(2, stringRes("Ready now")),
                        ),
                    transfers =
                        listOf(
                            MigrationReviewTransferState(
                                index = 1,
                                totalCount = 5,
                                amount = stringRes("1.348 ZEC"),
                                fiatAmount = stringRes("$521.30"),
                                scheduledLabel = stringRes("~10 mins"),
                            ),
                            MigrationReviewTransferState(
                                index = 2,
                                totalCount = 5,
                                amount = stringRes("1.052 ZEC"),
                                fiatAmount = stringRes("$406.86"),
                                scheduledLabel = stringRes("~6 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 3,
                                totalCount = 5,
                                amount = stringRes("2.105 ZEC"),
                                fiatAmount = stringRes("$813.74"),
                                scheduledLabel = stringRes("~12 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 4,
                                totalCount = 5,
                                amount = stringRes("1.897 ZEC"),
                                fiatAmount = stringRes("$733.51"),
                                scheduledLabel = stringRes("~18 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 5,
                                totalCount = 5,
                                amount = stringRes("4.456 ZEC"),
                                fiatAmount = stringRes("$1,723.53"),
                                scheduledLabel = stringRes("~24 hours"),
                            ),
                        ),
                    onConfirm = {},
                    onBack = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun PreviewPrivacyWithMultiStepSplitBalance() =
    ZcashTheme {
        // Renders PrivacyReviewContent directly (rather than via MigrationReviewView) — the
        // collapsed "Split Balance" row + "Show details" sheet (Figma "PR App Designs Q3'26" node
        // 5207:16023, 2026-08-03) replaces the old inline chevron-expand this preview used to
        // exercise.
        PrivacyReviewContent(
            paddingValues = PaddingValues(0.dp),
            state =
                MigrationReviewState(
                    mode = MigrationMode.AUTOMATIC,
                    totalAmount = stringRes("12.458 ZEC"),
                    estimatedDuration = stringRes("~8 min"),
                    preparationsSummarySubtitle = stringRes("in ~0 hours · 3 steps"),
                    preparationDetails =
                        MigrationPreparationDetails(
                            stepCount = 3,
                            totalAmount = stringRes("12.458 ZEC"),
                            steps =
                                listOf(
                                    MigrationPreparationStepDetail(
                                        title = stringRes("Transaction 1 of 3"),
                                        timeLabel = stringRes("in ~0 hours"),
                                        statusLabel = stringRes("Ready to send"),
                                    ),
                                    MigrationPreparationStepDetail(
                                        title = stringRes("Transaction 2 of 3"),
                                        timeLabel = stringRes("in ~1 hours"),
                                        statusLabel = stringRes("Preparing"),
                                    ),
                                    MigrationPreparationStepDetail(
                                        title = stringRes("Transaction 3 of 3"),
                                        timeLabel = stringRes("in ~2 hours"),
                                        statusLabel = stringRes("Waits on steps 1 & 2"),
                                    ),
                                ),
                            onDismiss = {},
                        ),
                    transfers =
                        listOf(
                            MigrationReviewTransferState(
                                index = 1,
                                totalCount = 5,
                                amount = stringRes("1.348 ZEC"),
                                fiatAmount = stringRes("$521.30"),
                                scheduledLabel = stringRes("~10 mins"),
                            ),
                            MigrationReviewTransferState(
                                index = 2,
                                totalCount = 5,
                                amount = stringRes("1.052 ZEC"),
                                fiatAmount = stringRes("$406.86"),
                                scheduledLabel = stringRes("~6 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 3,
                                totalCount = 5,
                                amount = stringRes("2.105 ZEC"),
                                fiatAmount = stringRes("$813.74"),
                                scheduledLabel = stringRes("~12 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 4,
                                totalCount = 5,
                                amount = stringRes("1.897 ZEC"),
                                fiatAmount = stringRes("$733.51"),
                                scheduledLabel = stringRes("~18 hours"),
                            ),
                            MigrationReviewTransferState(
                                index = 5,
                                totalCount = 5,
                                amount = stringRes("4.456 ZEC"),
                                fiatAmount = stringRes("$1,723.53"),
                                scheduledLabel = stringRes("~24 hours"),
                            ),
                        ),
                    onConfirm = {},
                    onBack = {},
                ),
            onShowPreparationDetails = {},
        )
    }
