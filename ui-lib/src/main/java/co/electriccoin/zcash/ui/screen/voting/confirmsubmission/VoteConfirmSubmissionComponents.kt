package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiLinearProgressIndicator
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
internal fun VoteSubmissionDetailsCard(state: VoteConfirmSubmissionState) {
    val isIdle = state.status is VoteSubmissionStatus.Idle
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZashiColors.Surfaces.bgSecondary,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column {
            VoteSubmissionDetailRow(stringRes(R.string.vote_confirm_detail_poll), state.roundTitle.getValue())
            if (isIdle) {
                HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
                VoteSubmissionMemoRow(state.memo.getValue())
            } else {
                HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
                VoteSubmissionDetailRow(
                    stringRes(R.string.vote_confirm_detail_voting_power),
                    state.votingWeightZEC.getValue()
                )
            }
        }
    }
}

@Composable
private fun VoteSubmissionDetailRow(
    label: StringResource,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textSecondary,
            modifier = Modifier.weight(0.95f)
        )
        Text(
            text = value,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.35f)
        )
    }
}

@Composable
private fun VoteSubmissionMemoRow(memo: String) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringRes(R.string.vote_confirm_detail_memo).getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textSecondary
        )
        VerticalSpacer(4.dp)
        Text(
            memo,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun VoteSubmissionBottomSection(state: VoteConfirmSubmissionState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                .padding(bottom = ZashiDimensions.Spacing.spacingMd)
    ) {
        val submissionProgress = state.submissionProgress()
        val progressTitle: StringResource? =
            when (val status = state.status) {
                is VoteSubmissionStatus.Authorizing ->
                    stringRes(R.string.vote_confirm_status_authorizing)

                is VoteSubmissionStatus.Submitting ->
                    stringRes(
                        R.string.vote_confirm_status_submitting,
                        status.current,
                        status.total
                    )

                else -> null
            }

        if (progressTitle != null) {
            VoteSubmissionProgressCard(
                title = progressTitle,
                progress = submissionProgress,
                ctaButton = state.ctaButton
            )
        } else {
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = state.ctaButton
            )
        }
    }
}

private fun VoteConfirmSubmissionState.submissionProgress(): Float {
    val delegationWeight = 0.3f
    return when (val status = status) {
        is VoteSubmissionStatus.Authorizing -> {
            if (includesAuthorizationProgress) {
                status.progress * delegationWeight
            } else {
                status.progress
            }
        }

        is VoteSubmissionStatus.Submitting -> {
            val offset = if (includesAuthorizationProgress) delegationWeight else 0f
            (offset + status.progress * (1f - offset)).coerceIn(0f, 1f)
        }

        else -> {
            0f
        }
    }
}

@Composable
private fun VoteSubmissionProgressCard(
    title: StringResource,
    progress: Float,
    ctaButton: ButtonState,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "submission_progress"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZashiColors.Surfaces.bgSecondary,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 20.dp,
                    )
            ) {
                Text(
                    text = title.getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
                VerticalSpacer(12.dp)
                ZashiLinearProgressIndicator(progress = animatedProgress)
            }
            HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
            ZashiButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 8.dp,
                            top = 2.dp,
                            end = 8.dp,
                            bottom = 4.dp
                        ),
                state = ctaButton,
            )
        }
    }
}
