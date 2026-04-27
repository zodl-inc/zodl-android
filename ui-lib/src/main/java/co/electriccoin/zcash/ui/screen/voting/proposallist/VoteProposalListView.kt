package co.electriccoin.zcash.ui.screen.voting.proposallist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun VoteProposalListView(state: VoteProposalListState) {
    BackHandler { state.onBack() }
    var showDescriptionSheet by rememberSaveable { mutableStateOf(false) }

    BlankBgScaffold(
        topBar = { AppBar(state) },
        content = { padding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .scaffoldPadding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        VerticalSpacer(24.dp)
                        when (state.mode) {
                            VoteProposalListMode.VOTING -> {
                                VotingHeader(
                                    state = state,
                                    onViewMore = { showDescriptionSheet = true }
                                )
                            }

                            VoteProposalListMode.REVIEW -> {
                                ReviewHeader()
                            }
                        }
                        VerticalSpacer(24.dp)
                    }
                    items(state.proposals, key = { it.id }) { proposal ->
                        ProposalCard(
                            state = proposal,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                        )
                        VerticalSpacer(16.dp)
                    }
                    // Space for the floating CTA
                    if (state.ctaButton != null) {
                        item { VerticalSpacer(88.dp) }
                    }
                }

                if (state.ctaButton != null) {
                    ZashiButton(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                                .padding(bottom = ZashiDimensions.Spacing.spacingMd),
                        state = state.ctaButton
                    )
                }
            }
        }
    )

    if (showDescriptionSheet) {
        DescriptionBottomSheet(
            state = state,
            onDismiss = { showDescriptionSheet = false }
        )
    }
}

// ─── Headers ─────────────────────────────────────────────────────────────────

@Composable
private fun VotingHeader(
    state: VoteProposalListState,
    onViewMore: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
    ) {
        // Title row: round title (left) + #snapshotHeight (right)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = state.roundTitle.getValue(),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (state.snapshotHeight != null) {
                Spacer(12.dp)
                Text(
                    text = "#${formatSnapshotHeight(state.snapshotHeight)}",
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(12.dp)
        VoteProgressBar(
            votedCount = state.votedCount,
            totalCount = state.totalCount,
            modifier = Modifier.fillMaxWidth().height(10.dp)
        )
        if (state.metaLine != null) {
            Spacer(8.dp)
            Text(
                text = state.metaLine.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
            )
        }
        if (state.description != null) {
            Spacer(8.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.description.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(4.dp)
                ZashiButton(
                    state =
                        ButtonState(
                            text = stringRes("View more"),
                            style = ButtonStyle.TERTIARY,
                            onClick = onViewMore
                        )
                )
            }
        }
    }
}

@Composable
private fun ReviewHeader() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
    ) {
        Text(
            text = stringRes("Review and submit vote").getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(8.dp)
        Text(
            text =
                stringRes(
                    "Tap on the question to edit any of your answers. " +
                        "After you review your answers, tap on Confirm & Submit."
                ).getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textSecondary,
        )
    }
}

// ─── Progress Bar ─────────────────────────────────────────────────────────────

@Composable
private fun VoteProgressBar(
    votedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val ratio = if (totalCount > 0) votedCount.toFloat() / totalCount else 0f
    Box(modifier = modifier) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val barHeight = size.height
            val barWidth = size.width
            val dotRadius = barHeight / 2 * 0.6f // dots are 60% of bar height radius

            // Background track
            drawRoundRect(
                color = Color(0xFF3C3D3F),
                size =
                    androidx.compose.ui.geometry
                        .Size(barWidth, barHeight),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(barHeight / 2)
            )

            // Filled portion
            val fillWidth = barWidth * ratio
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = Color(0xFFE5E5E5),
                    size =
                        androidx.compose.ui.geometry
                            .Size(fillWidth.coerceAtLeast(barHeight), barHeight),
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(barHeight / 2)
                )
            }

            // Per-proposal indicator dots — shown for unvoted proposals (ahead of fill)
            if (totalCount > 1) {
                for (i in 1 until totalCount) {
                    val dotX = barWidth * i.toFloat() / totalCount
                    val isAheadOfFill = dotX > fillWidth
                    if (isAheadOfFill) {
                        drawCircle(
                            color = Color(0xFF6B7280),
                            radius = dotRadius,
                            center =
                                androidx.compose.ui.geometry
                                    .Offset(dotX, barHeight / 2)
                        )
                    }
                }
            }
        }
    }
}

// ─── Proposal Card ───────────────────────────────────────────────────────────

@Composable
private fun ProposalCard(
    state: VoteProposalRowState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = ZashiColors.Surfaces.bgPrimary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
        onClick = state.onClick,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(ZashiDimensions.Spacing.spacingXl)
        ) {
            // ZIP badge row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.zipNumber != null) {
                    ZipBadge(label = state.zipNumber.getValue())
                }
                Spacer(1f)
                if (state.voteBadge != null) {
                    VoteBadge(state = state.voteBadge)
                }
            }
            Spacer(12.dp)
            Text(
                text = state.title.getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.description.getValue().isNotEmpty()) {
                Spacer(4.dp)
                Text(
                    text = state.description.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ZipBadge(label: String) {
    Surface(
        color = ZashiColors.Surfaces.bgTertiary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusMd),
    ) {
        Text(
            text = label,
            style = ZashiTypography.textXs,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun VoteBadge(state: VoteVoteBadgeState) {
    val (bgColor, textColor) =
        when (state.type) {
            VoteVoteBadgeType.SUPPORT -> {
                Pair(
                    ZashiColors.Utility.SuccessGreen.utilitySuccess50,
                    ZashiColors.Utility.SuccessGreen.utilitySuccess700
                )
            }

            VoteVoteBadgeType.OPPOSE -> {
                Pair(
                    ZashiColors.Utility.ErrorRed.utilityError50,
                    ZashiColors.Utility.ErrorRed.utilityError700
                )
            }

            VoteVoteBadgeType.ABSTAIN -> {
                Pair(
                    ZashiColors.Utility.HyperBlue.utilityBlueDark50,
                    ZashiColors.Utility.HyperBlue.utilityBlueDark700
                )
            }
        }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusMd),
    ) {
        Text(
            text = state.label.getValue(),
            style = ZashiTypography.textXs,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ─── Description Bottom Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DescriptionBottomSheet(
    state: VoteProposalListState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ZashiColors.Surfaces.bgPrimary,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                    .padding(bottom = ZashiDimensions.Spacing.spacingMd)
        ) {
            Text(
                text = state.roundTitle.getValue(),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(16.dp)
            if (state.description != null) {
                Text(
                    text = state.description.getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textPrimary,
                )
            }
            if (state.discussionUrl != null) {
                Spacer(16.dp)
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    state =
                        ButtonState(
                            text = stringRes("View Forum Discussions"),
                            style = ButtonStyle.TERTIARY,
                            onClick = { uriHandler.openUri(state.discussionUrl) }
                        )
                )
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** Formats snapshot height with comma grouping, e.g. 2800000 → "2,800,000". */
private fun formatSnapshotHeight(height: Long): String =
    java.text.NumberFormat
        .getNumberInstance(java.util.Locale.US)
        .format(height)

// ─── App Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun AppBar(state: VoteProposalListState) {
    ZashiSmallTopAppBar(
        title = "Coinholder Polling",
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = state.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        colors =
            ZcashTheme.colors.topAppBarColors orDark
                ZcashTheme.colors.topAppBarColors.copyColors(
                    containerColor = Color.Transparent
                )
    )
}

// ─── Previews ────────────────────────────────────────────────────────────────

@PreviewScreens
@Composable
private fun ProposalListVotingPreview() =
    ZcashTheme {
        VoteProposalListView(
            state =
                VoteProposalListState(
                    mode = VoteProposalListMode.VOTING,
                    roundTitle = stringRes("NU7 Sentiment Poll"),
                    snapshotHeight = 2_800_000L,
                    votedCount = 1,
                    totalCount = 3,
                    metaLine = stringRes("Ends May 15, 2026  ·  2 days left"),
                    description =
                        stringRes(
                            "This poll gauges community sentiment on three ZIPs proposed for inclusion in NU7."
                        ),
                    discussionUrl = null,
                    proposals =
                        listOf(
                            VoteProposalRowState(
                                id = 0,
                                zipNumber = stringRes("ZIP-227"),
                                title = stringRes("Zcash Shielded Assets (ZSAs)"),
                                description = stringRes("Extend the Orchard protocol to support user-defined assets."),
                                voteBadge = VoteVoteBadgeState(stringRes("Support"), VoteVoteBadgeType.SUPPORT),
                                onClick = {}
                            ),
                            VoteProposalRowState(
                                id = 1,
                                zipNumber = stringRes("ZIP-235"),
                                title = stringRes("Network Sustainability Mechanism"),
                                description = stringRes("Redirect a portion of the block subsidy to a sustainability fund."),
                                voteBadge = null,
                                onClick = {}
                            ),
                            VoteProposalRowState(
                                id = 2,
                                zipNumber = stringRes("ZIP-236"),
                                title = stringRes("Orchard Quantum Recoverability"),
                                description = stringRes("Add a post-quantum fallback mechanism to Orchard notes."),
                                voteBadge = null,
                                onClick = {}
                            ),
                        ),
                    ctaButton =
                        ButtonState(
                            text = stringRes("Continue Voting"),
                            style = ButtonStyle.PRIMARY,
                            onClick = {}
                        ),
                    onBack = {}
                )
        )
    }

@PreviewScreens
@Composable
private fun ProposalListReviewPreview() =
    ZcashTheme {
        VoteProposalListView(
            state =
                VoteProposalListState(
                    mode = VoteProposalListMode.REVIEW,
                    roundTitle = stringRes("NU7 Sentiment Poll"),
                    snapshotHeight = null,
                    votedCount = 3,
                    totalCount = 3,
                    metaLine = null,
                    description = null,
                    discussionUrl = null,
                    proposals =
                        listOf(
                            VoteProposalRowState(
                                id = 0,
                                zipNumber = stringRes("ZIP-227"),
                                title = stringRes("Zcash Shielded Assets (ZSAs)"),
                                description = stringRes("Extend the Orchard protocol to support user-defined assets."),
                                voteBadge = VoteVoteBadgeState(stringRes("Support"), VoteVoteBadgeType.SUPPORT),
                                onClick = {}
                            ),
                            VoteProposalRowState(
                                id = 1,
                                zipNumber = stringRes("ZIP-235"),
                                title = stringRes("Network Sustainability Mechanism"),
                                description = stringRes("Redirect a portion of the block subsidy to a sustainability fund."),
                                voteBadge = VoteVoteBadgeState(stringRes("Oppose"), VoteVoteBadgeType.OPPOSE),
                                onClick = {}
                            ),
                            VoteProposalRowState(
                                id = 2,
                                zipNumber = stringRes("ZIP-236"),
                                title = stringRes("Orchard Quantum Recoverability"),
                                description = stringRes("Add a post-quantum fallback mechanism to Orchard notes."),
                                voteBadge = VoteVoteBadgeState(stringRes("Abstain"), VoteVoteBadgeType.ABSTAIN),
                                onClick = {}
                            ),
                        ),
                    ctaButton =
                        ButtonState(
                            text = stringRes("Confirm & Submit"),
                            style = ButtonStyle.PRIMARY,
                            onClick = {}
                        ),
                    onBack = {}
                )
        )
    }
