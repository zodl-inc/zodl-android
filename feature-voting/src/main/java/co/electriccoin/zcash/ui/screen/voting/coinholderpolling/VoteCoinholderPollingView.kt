package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.error
import co.electriccoin.zcash.ui.common.model.voting.SessionStatus
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.common.CommonShimmerLoadingScreen
import co.electriccoin.zcash.ui.screen.voting.VoteTrustIndicator
import co.electriccoin.zcash.ui.screen.voting.component.VoteAppBar
import co.electriccoin.zcash.ui.screen.voting.component.VoteTrustIndicatorView

@Composable
fun VoteCoinholderPollingView(state: VoteCoinholderPollingState) {
    ZashiConfirmationBottomSheet(state = state.configErrorSheet)
    ZashiConfirmationBottomSheet(state = state.unverifiedPollWarningSheet)
    ZashiConfirmationBottomSheet(state = state.noRoundsSheet)

    BlankBgScaffold(
        topBar = {
            VoteAppBar(
                title = stringResource(R.string.coinVote_common_screenTitle),
                onBack = state.onBack,
                onConfigSettings = state.onConfigSettings,
            )
        },
        content = { padding ->
            val activeRounds = state.activeRounds.orEmpty()
            val pastRounds = state.pastRounds.orEmpty()
            if (activeRounds.isEmpty() && pastRounds.isEmpty()) {
                CommonShimmerLoadingScreen(
                    shimmerItemsCount = 8,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .scaffoldScrollPadding(padding),
                    showDivider = false,
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding()),
                    contentPadding =
                        PaddingValues(
                            start = ZashiDimensions.Spacing.spacing3xl,
                            top = ZashiDimensions.Spacing.spacingLg,
                            end = ZashiDimensions.Spacing.spacing3xl,
                            bottom = padding.calculateBottomPadding() + ZashiDimensions.Spacing.spacing3xl
                        ),
                    verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacing4xl)
                ) {
                    items(
                        activeRounds,
                        key = { it.roundId },
                        contentType = { "pollcard" }
                    ) { round ->
                        PollCard(round)
                    }
                    items(
                        pastRounds,
                        key = { it.roundId },
                        contentType = { "pollcard" }
                    ) { round ->
                        PollCard(round)
                    }
                }
            }
        }
    )
}

@Composable
fun VoteCoinholderPollingLoadingView(state: VoteCoinholderPollingState) {
    BlankBgScaffold(
        topBar = {
            VoteAppBar(
                title = stringResource(R.string.coinVote_common_screenTitle),
                onBack = state.onBack,
                onConfigSettings = state.onConfigSettings,
            )
        },
        content = { padding ->
            CommonShimmerLoadingScreen(
                shimmerItemsCount = 8,
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
private fun PollCard(state: VotePollCardState) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth(),
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
            verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacing2xl)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.title.getValue(),
                        style = ZashiTypography.textMd,
                        color = ZashiColors.Text.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.description.getValue().isNotEmpty()) {
                        Text(
                            text = state.description.getValue(),
                            style = ZashiTypography.textSm,
                            color = ZashiColors.Text.textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.trustIndicator != null) {
                    VoteTrustIndicatorView(state.trustIndicator)
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                PollActionButton(state)
            }
        }
    }
}

@Composable
private fun PollActionButton(state: VotePollCardState) {
    val actionText =
        when (state.status) {
            VotePollCardStatus.ACTIVE -> stringRes(R.string.coinVote_pollsList_enterPoll)
            VotePollCardStatus.VOTED -> stringRes(R.string.coinVote_proposalList_ctaReviewAnswers)
            VotePollCardStatus.CLOSED -> stringRes(R.string.coinVote_common_viewResults)
        }

    ZashiButton(
        modifier = Modifier.height(40.dp),
        state =
            ButtonState(
                text = actionText,
                style =
                    when (state.status) {
                        VotePollCardStatus.ACTIVE -> ButtonStyle.PRIMARY
                        else -> ButtonStyle.TERTIARY
                    },
                isEnabled = state.isActionEnabled,
                onClick = state.onAction
            ),
        style = ZashiTypography.textSm,
    )
}

@Composable
private fun StatusBadge(status: VotePollCardStatus) {
    val params =
        when (status) {
            VotePollCardStatus.ACTIVE -> {
                StatusBadgeParams(
                    labelRes = R.string.coinVote_pollsList_statusActive,
                    iconTint = ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                    textColor = ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                    bgColor = ZashiColors.Utility.SuccessGreen.utilitySuccess50,
                    borderColor = ZashiColors.Utility.SuccessGreen.utilitySuccess200,
                )
            }

            VotePollCardStatus.VOTED -> {
                StatusBadgeParams(
                    labelRes = R.string.coinVote_common_voted,
                    iconTint = ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                    textColor = ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                    bgColor = ZashiColors.Utility.SuccessGreen.utilitySuccess50,
                    borderColor = ZashiColors.Utility.SuccessGreen.utilitySuccess200,
                )
            }

            VotePollCardStatus.CLOSED -> {
                StatusBadgeParams(
                    labelRes = R.string.coinVote_pollsList_statusClosed,
                    iconTint = ZashiColors.Utility.ErrorRed.utilityError700,
                    textColor = ZashiColors.Utility.ErrorRed.utilityError700,
                    bgColor = ZashiColors.Utility.ErrorRed.utilityError50,
                    borderColor = ZashiColors.Utility.ErrorRed.utilityError200,
                )
            }
        }
    Surface(
        shape = CircleShape,
        color = params.bgColor,
        border = BorderStroke(1.dp, params.borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_vote_clock),
                contentDescription = null,
                tint = params.iconTint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(params.labelRes),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = params.textColor
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

@PreviewScreens
@Composable
private fun CoinholderPollingPreviewWithRounds() =
    ZcashTheme {
        VoteCoinholderPollingView(
            state =
                VoteCoinholderPollingState.preview.copy(
                    pastRounds =
                        listOf(
                            VotePollCardState.preview.copy(
                                roundId = "def456",
                                roundNumber = 2,
                                title = stringRes("ZF Grant Funding — Q2 2026"),
                                status = VotePollCardStatus.CLOSED,
                                sessionStatus = SessionStatus.COMPLETED,
                                votedLabel = stringRes("2 of 2 voted"),
                                votedCount = 2,
                            ),
                            VotePollCardState.preview.copy(
                                roundId = "ghi789",
                                roundNumber = 1,
                                title = stringRes("ZF Grant Funding — Q1 2026"),
                                status = VotePollCardStatus.VOTED,
                                sessionStatus = SessionStatus.COMPLETED,
                                trustIndicator = VoteTrustIndicator.UNVERIFIED,
                            ),
                        ),
                )
        )
    }

@PreviewScreens
@Composable
private fun CoinholderPollingPreviewEmpty() =
    ZcashTheme {
        VoteCoinholderPollingView(
            state =
                VoteCoinholderPollingState.preview.copy(
                    activeRounds = emptyList(),
                    pastRounds = emptyList(),
                    noRoundsSheet =
                        ZashiConfirmationState.error(
                            title = stringRes(R.string.coinVote_pollsList_emptyTitle),
                            message = stringRes(R.string.coinVote_pollsList_emptyMessage),
                            primaryText = stringRes(R.string.coinVote_common_refresh),
                            primaryStyle = ButtonStyle.SECONDARY,
                            secondaryText = stringRes(R.string.coinVote_common_gotIt),
                            secondaryStyle = ButtonStyle.PRIMARY,
                            onPrimary = {},
                            onSecondary = {},
                            onBack = {},
                        )
                )
        )
    }

@PreviewScreens
@Composable
private fun CoinholderPollingPreviewLoading() =
    ZcashTheme {
        VoteCoinholderPollingLoadingView(state = VoteCoinholderPollingState.preview)
    }
