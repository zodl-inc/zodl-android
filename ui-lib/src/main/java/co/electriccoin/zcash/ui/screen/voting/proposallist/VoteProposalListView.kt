package co.electriccoin.zcash.ui.screen.voting.proposallist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.common.CommonShimmerLoadingScreen
import co.electriccoin.zcash.ui.screen.voting.VoteColors
import co.electriccoin.zcash.ui.screen.voting.answerColors
import co.electriccoin.zcash.ui.screen.voting.component.VoteAppBar
import co.electriccoin.zcash.ui.screen.voting.component.VoteViewMoreChip
import co.electriccoin.zcash.ui.screen.voting.component.ZipBadge
import java.text.NumberFormat
import java.util.Locale

private const val DOT_FILL_RATIO = 0.6f

@Composable
fun VoteProposalListView(state: VoteProposalListState) {
    BlankBgScaffold(
        topBar = {
            VoteAppBar(
                title = stringResource(R.string.vote_top_bar_title),
                onBack = state.onBack,
            )
        },
        content = { padding ->
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = padding.calculateTopPadding()),
                    contentPadding =
                        PaddingValues(
                            start = ZashiDimensions.Spacing.spacing3xl,
                            top = ZashiDimensions.Spacing.spacingLg,
                            end = ZashiDimensions.Spacing.spacing3xl,
                            bottom = ZashiDimensions.Spacing.spacing3xl
                        ),
                ) {
                    item {
                        when (state.mode) {
                            VoteProposalListMode.VOTING,
                            VoteProposalListMode.VOTED -> {
                                VotingHeader(
                                    state = state,
                                    onViewMore = state.onViewMore ?: {}
                                )
                            }

                            VoteProposalListMode.REVIEW -> {
                                ReviewHeader()
                            }
                        }
                        VerticalSpacer(24.dp)
                    }

                    items(state.proposals.orEmpty(), key = { it.id }) { proposal ->
                        ProposalCard(
                            state = proposal,
                            modifier = Modifier.fillMaxWidth()
                        )
                        VerticalSpacer(8.dp)
                    }

                    if (state.ctaButton != null) {
                        item { VerticalSpacer(88.dp) }
                    }
                }

                state.ctaButton?.let { button ->
                    ZashiButton(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = ZashiDimensions.Spacing.spacing3xl,
                                    end = ZashiDimensions.Spacing.spacing3xl,
                                    bottom = padding.calculateBottomPadding() + ZashiDimensions.Spacing.spacing3xl
                                ),
                        state = button
                    )
                }
            }
        }
    )
}

@Composable
fun VoteProposalListLoadingView(state: VoteProposalListState) {
    BlankBgScaffold(
        topBar = {
            VoteAppBar(
                title = stringResource(R.string.vote_top_bar_title),
                onBack = state.onBack,
            )
        },
        content = { padding ->
            CommonShimmerLoadingScreen(
                shimmerItemsCount = 6,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .scaffoldScrollPadding(padding),
                showDivider = false,
            )
        }
    )
}

@Composable
private fun VotingHeader(
    state: VoteProposalListState,
    onViewMore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = state.roundTitle.getValue(),
                style = ZashiTypography.textXl,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            state.snapshotHeight?.let { snapshotHeight ->
                Spacer(12.dp)
                Text(
                    text = "#${formatSnapshotHeight(snapshotHeight)}",
                    style = ZashiTypography.textXl,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(12.dp)

        state.metaLine?.let { metaLine ->
            Spacer(8.dp)
            HeaderMetaLine(metaLine)
        }

        state.description?.let { description ->
            Spacer(8.dp)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = description.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(4.dp)
                VoteViewMoreChip(onClick = onViewMore)
            }
        }
    }
}

@Composable
private fun HeaderMetaLine(state: VoteProposalMetaLineState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = state.leading.getValue(),
            style = ZashiTypography.textXs,
            color = ZashiColors.Text.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        state.trailing?.let { trailing ->
            Spacer(8.dp)
            Text(
                text = trailing.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReviewHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.vote_proposal_list_review_title),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(8.dp)
        Text(
            text = stringResource(R.string.vote_proposal_list_review_subtitle),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
        )
    }
}

@Composable
private fun VoteProgressBar(
    votedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val total = totalCount.coerceAtLeast(1)
    val ratio = if (totalCount > 0) votedCount.toFloat() / totalCount else 0f
    val bgColor = ZashiColors.Surfaces.bgQuaternary
    val fillColor = ZashiColors.Text.textPrimary
    val dotColor = ZashiColors.Utility.Gray.utilityGray300

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barHeight = size.height
            val barWidth = size.width
            val dotRadius = barHeight / 2 * DOT_FILL_RATIO

            drawRoundRect(
                color = bgColor,
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2)
            )

            val fillWidth = barWidth * ratio
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = fillColor,
                    size = Size(fillWidth.coerceAtLeast(barHeight), barHeight),
                    cornerRadius = CornerRadius(barHeight / 2)
                )
            }

            if (total > 1) {
                for (index in 1 until total) {
                    val dotX = barWidth * index.toFloat() / total
                    if (dotX > fillWidth) {
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(dotX, barHeight / 2)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalCard(
    state: VoteProposalRowState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = ZashiColors.Surfaces.bgPrimary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
        border = BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary),
        onClick = state.onClick,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(ZashiDimensions.Spacing.spacingXl),
            verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingLg)
        ) {
            state.zipNumber?.let { ZipBadge(label = it.getValue()) }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.description.getValue().isNotEmpty()) {
                    Text(
                        text = state.description.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            state.voteBadge?.let { YourVoteContainer(badge = it) }
        }
    }
}

private const val YOUR_VOTE_SHORT_LABEL_MAX_CHARS = 10

@Composable
private fun YourVoteContainer(badge: VoteVoteBadgeState) {
    val colors = badge.color.answerColors()
    val label = badge.label.getValue()
    val isShort = label.length <= YOUR_VOTE_SHORT_LABEL_MAX_CHARS
    val containerModifier =
        Modifier
            .fillMaxWidth()
            .background(colors.bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)

    if (isShort) {
        Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(colors)
            Value(label, colors)
        }
    } else {
        Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Label(colors)
            Value(label, colors)
        }
    }
}

@Composable
private fun Value(label: String, colors: VoteColors) {
    Text(
        text = label,
        style = ZashiTypography.textXs,
        fontWeight = FontWeight.SemiBold,
        color = colors.textColor,
    )
}

@Composable
private fun Label(colors: VoteColors) {
    Text(
        text = stringResource(R.string.vote_proposal_list_your_vote),
        style = ZashiTypography.textXxs,
        fontWeight = FontWeight.Medium,
        color = colors.labelColor,
    )
}

private fun formatSnapshotHeight(height: Long): String =
    NumberFormat.getNumberInstance(Locale.US).format(height)

private fun previewProposals(withBadge: Boolean) =
    listOf(
        VoteProposalRowState(
            id = 1,
            zipNumber = stringRes("ZIP-317"),
            title = stringRes("Proportional Transfer Fee Mechanism"),
            description =
                stringRes(
                    "Replace the current fixed fee with a proportional " +
                        "fee based on the number of logical actions " +
                        "fee based on the number of logical actions."
                ),
            voteBadge =
                if (withBadge) {
                    VoteVoteBadgeState(stringRes("Support"), VoteOptionDisplayColor.SUPPORT)
                } else {
                    null
                },
            onClick = {}
        ),
        VoteProposalRowState(
            id = 2,
            zipNumber = stringRes("ZIP-320"),
            title = stringRes("Memo Encryption Upgrade"),
            description = stringRes("Upgrade the memo field encryption to use a more secure algorithm."),
            voteBadge =
                if (withBadge) {
                    VoteVoteBadgeState(
                        stringRes("As soon as possible after NSM activation"),
                        VoteOptionDisplayColor.OPPOSE
                    )
                } else {
                    null
                },
            onClick = {}
        ),
        VoteProposalRowState(
            id = 3,
            zipNumber = null,
            title = stringRes("Community Governance Proposal"),
            description = stringRes(""),
            voteBadge =
                if (withBadge) {
                    VoteVoteBadgeState(stringRes("Abstain"), VoteOptionDisplayColor.ABSTAIN)
                } else {
                    null
                },
            onClick = {}
        ),
    )

private fun previewState(
    mode: VoteProposalListMode,
    withBadge: Boolean = false,
    withCta: Boolean = false,
) = VoteProposalListState.preview.copy(
    mode = mode,
    proposals = previewProposals(withBadge),
    ctaButton = if (withCta) ButtonState(text = stringRes("Submit votes")) else null,
)

@PreviewScreens
@Composable
private fun VoteProposalListVotingPreview() =
    ZcashTheme { VoteProposalListView(previewState(VoteProposalListMode.VOTING)) }

@PreviewScreens
@Composable
private fun VoteProposalListVotingWithCtaPreview() =
    ZcashTheme { VoteProposalListView(previewState(VoteProposalListMode.VOTING, withCta = true)) }

@PreviewScreens
@Composable
private fun VoteProposalListVotedPreview() =
    ZcashTheme { VoteProposalListView(previewState(VoteProposalListMode.VOTED, withBadge = true)) }

@PreviewScreens
@Composable
private fun VoteProposalListReviewPreview() =
    ZcashTheme { VoteProposalListView(previewState(VoteProposalListMode.REVIEW, withBadge = true, withCta = true)) }

@PreviewScreens
@Composable
private fun VoteProposalListLoadingPreview() =
    ZcashTheme { VoteProposalListLoadingView(state = VoteProposalListState.preview) }
