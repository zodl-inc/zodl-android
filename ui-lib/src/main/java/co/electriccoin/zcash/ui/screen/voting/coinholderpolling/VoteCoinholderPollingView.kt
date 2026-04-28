package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
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
fun VoteCoinholderPollingView(state: VoteCoinholderPollingState) {
    BlankBgScaffold(
        topBar = { AppBar(state) },
        content = { padding ->
            if (state.items.isEmpty()) {
                NoRoundsContent(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .scaffoldPadding(padding)
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .scaffoldPadding(padding),
                    verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacing2xl)
                ) {
                    items(state.items, key = { it.roundId }) { round ->
                        PollCard(round)
                    }
                }
            }
        }
    )
}

@Composable
private fun NoRoundsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.vote_poll_list_empty_title),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vote_poll_list_empty_subtitle),
            style = ZashiTypography.textMd,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PollCard(state: VotePollCardState) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { state.onAction() },
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
        color = ZashiColors.Surfaces.bgPrimary,
        border = BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary),
        shadowElevation = 1.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(ZashiDimensions.Spacing.spacingXl),
            verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingXl)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusBadge(state.status)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = state.dateLabel.getValue(),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Text.textTertiary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.votedLabel?.getValue() ?: "${state.votedCount} of ${state.proposalCount} voted",
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.Medium,
                        color = ZashiColors.Text.textPrimary
                    )
                    ProgressDots(votedCount = state.votedCount, total = state.proposalCount)
                }
            }

            if (state.description.getValue().isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.vote_poll_card_description_label),
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.Medium,
                        color = ZashiColors.Text.textTertiary
                    )
                    Text(
                        text = state.description.getValue(),
                        style = ZashiTypography.textSm,
                        color = ZashiColors.Text.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: VotePollCardStatus) {
    val (labelRes, iconTint, textColor, bgColor, borderColor) =
        when (status) {
            VotePollCardStatus.ACTIVE -> {
                StatusBadgeParams(
                    R.string.vote_poll_card_status_active,
                    ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                    ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                    ZashiColors.Utility.SuccessGreen.utilitySuccess50,
                    ZashiColors.Utility.SuccessGreen.utilitySuccess200,
                )
            }

            VotePollCardStatus.VOTED -> {
                StatusBadgeParams(
                    R.string.vote_poll_card_status_voted,
                    ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                    ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                    ZashiColors.Utility.SuccessGreen.utilitySuccess50,
                    ZashiColors.Utility.SuccessGreen.utilitySuccess200,
                )
            }

            VotePollCardStatus.CLOSED -> {
                StatusBadgeParams(
                    R.string.vote_poll_card_status_closed,
                    ZashiColors.Utility.ErrorRed.utilityError700,
                    ZashiColors.Utility.ErrorRed.utilityError700,
                    ZashiColors.Utility.ErrorRed.utilityError50,
                    ZashiColors.Utility.ErrorRed.utilityError200,
                )
            }
        }
    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_vote_clock),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(labelRes),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

private data class StatusBadgeParams(
    val labelRes: Int,
    val iconTint: Color,
    val textColor: Color,
    val bgColor: Color,
    val borderColor: Color,
)

@Composable
private fun ProgressDots(votedCount: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { index ->
            Surface(
                shape = CircleShape,
                color =
                    if (index < votedCount) {
                        ZashiColors.Utility.SuccessGreen.utilitySuccess500
                    } else {
                        ZashiColors.Utility.Gray.utilityGray200
                    },
                modifier = Modifier.size(8.dp)
            ) {}
        }
    }
}

@Composable
private fun AppBar(state: VoteCoinholderPollingState) {
    ZashiSmallTopAppBar(
        title = stringResource(R.string.vote_top_bar_title),
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
private fun CoinholderPollingPreview_WithRounds() =
    ZcashTheme {
        VoteCoinholderPollingView(
            state =
                VoteCoinholderPollingState(
                    items =
                        listOf(
                            VotePollCardState(
                                roundId = "abc123",
                                title = stringRes("ZF Grant Funding — Q3 2026"),
                                description = stringRes("Shielded vote on the allocation of Zcash Foundation grant funds for Q3 2026."),
                                status = VotePollCardStatus.ACTIVE,
                                dateLabel = stringRes("Closes May 15"),
                                votedLabel = null,
                                proposalCount = 11,
                                votedCount = 0,
                                onAction = {}
                            ),
                            VotePollCardState(
                                roundId = "def456",
                                title = stringRes("ZF Grant Funding — Q2 2026"),
                                description = stringRes("Completed vote on Q2 2026 grant allocation."),
                                status = VotePollCardStatus.VOTED,
                                dateLabel = stringRes("Closed Apr 10"),
                                votedLabel = stringRes("11 of 11 voted"),
                                proposalCount = 11,
                                votedCount = 11,
                                onAction = {}
                            ),
                            VotePollCardState(
                                roundId = "ghi789",
                                title = stringRes("ZF Grant Funding — Q1 2026"),
                                description = stringRes(""),
                                status = VotePollCardStatus.CLOSED,
                                dateLabel = stringRes("Closed Jan 20"),
                                votedLabel = null,
                                proposalCount = 11,
                                votedCount = 0,
                                onAction = {}
                            ),
                        ),
                    onBack = {}
                )
        )
    }

@PreviewScreens
@Composable
private fun CoinholderPollingPreview_Empty() =
    ZcashTheme {
        VoteCoinholderPollingView(
            state =
                VoteCoinholderPollingState(
                    items = emptyList(),
                    onBack = {}
                )
        )
    }
