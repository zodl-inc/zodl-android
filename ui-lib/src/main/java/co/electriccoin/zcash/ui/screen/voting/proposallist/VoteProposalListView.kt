package co.electriccoin.zcash.ui.screen.voting.proposallist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.accentColor
import java.text.NumberFormat
import java.util.Locale

@Composable
fun VoteProposalListView(state: VoteProposalListState) {
    BackHandler { state.onBack() }

    BlankBgScaffold(
        topBar = { AppBar(state) },
        content = { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scaffoldPadding(padding)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        VerticalSpacer(24.dp)
                        when (state.mode) {
                            VoteProposalListMode.VOTING,
                            VoteProposalListMode.VOTED ->
                                VotingHeader(
                                    state = state,
                                    onViewMore = state.onViewMore ?: {}
                                )

                            VoteProposalListMode.REVIEW -> ReviewHeader()
                        }
                        VerticalSpacer(24.dp)
                    }

                    items(state.proposals, key = { it.id }) { proposal ->
                        ProposalCard(
                            state = proposal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                        )
                        VerticalSpacer(16.dp)
                    }

                    if (state.ctaButton != null) {
                        item { VerticalSpacer(88.dp) }
                    }
                }

                state.ctaButton?.let { button ->
                    ZashiButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                            .padding(bottom = ZashiDimensions.Spacing.spacingMd),
                        state = button
                    )
                }
            }
        }
    )
}

@Composable
fun VoteProposalListLoadingView() {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = "Coinholder Polling",
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = {},
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                },
                colors = ZcashTheme.colors.topAppBarColors orDark
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    )
            )
        },
        content = { padding ->
            CircularScreenProgressIndicator(
                modifier = Modifier.scaffoldPadding(padding)
            )
        }
    )
}

@Composable
private fun VotingHeader(
    state: VoteProposalListState,
    onViewMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
    ) {
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

            state.snapshotHeight?.let { snapshotHeight ->
                Spacer(12.dp)
                Text(
                    text = "#${formatSnapshotHeight(snapshotHeight)}",
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
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        )

        state.metaLine?.let { metaLine ->
            Spacer(8.dp)
            HeaderMetaLine(metaLine)
        }

        state.description?.let { description ->
            Spacer(8.dp)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = description.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(4.dp)
                ViewMoreChip(onClick = onViewMore)
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
private fun ViewMoreChip(onClick: () -> Unit) {
    Surface(
        color = ZashiColors.Surfaces.bgSecondary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusMd),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = stringRes("View more").getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
                fontWeight = FontWeight.Medium
            )
            Spacer(4.dp)
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = ZashiColors.Text.textTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ReviewHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
    ) {
        Text(
            text = stringRes("Review your answers").getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(8.dp)
        Text(
            text = stringRes("Tap a question to edit any of your selections.").getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textSecondary,
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
    val ratio = (votedCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val bgColor = ZashiColors.Surfaces.bgQuaternary
    val fillColor = ZashiColors.Text.textPrimary
    val dotColor = ZashiColors.Text.textTertiary.copy(alpha = 0.35f)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barHeight = size.height
            val barWidth = size.width
            val dotRadius = barHeight / 2 * 0.6f

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
                for (index in 0 until total) {
                    val dotX = if (total == 1) {
                        0f
                    } else {
                        barWidth * index.toFloat() / (total - 1).toFloat()
                    }
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
        onClick = state.onClick,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZashiDimensions.Spacing.spacingXl)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                state.zipNumber?.let { zipNumber ->
                    ZipBadge(label = zipNumber.getValue())
                }

                Spacer(1f)

                state.voteBadge?.let { voteBadge ->
                    VoteBadge(state = voteBadge)
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
    val textColor = state.color.accentColor()
    val bgColor = textColor.copy(alpha = 0.12f)

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

private fun formatSnapshotHeight(height: Long): String =
    NumberFormat.getNumberInstance(Locale.US).format(height)

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
        colors = ZcashTheme.colors.topAppBarColors orDark
            ZcashTheme.colors.topAppBarColors.copyColors(
                containerColor = Color.Transparent
            )
    )
}
