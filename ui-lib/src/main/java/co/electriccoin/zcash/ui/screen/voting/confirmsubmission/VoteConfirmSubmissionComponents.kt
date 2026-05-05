package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue

// ─── Details Card ─────────────────────────────────────────────────────────────

@Composable
internal fun DetailsCard(state: VoteConfirmSubmissionState) {
    val isIdle = state.status is VoteSubmissionStatus.Idle
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZashiColors.Surfaces.bgSecondary,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column {
            DetailRow("Poll", state.roundTitle.getValue())
            HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
            if (isIdle) {
                DetailRow("Amount", "0.00000001 ZEC")
                HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
                DetailRow("Fee", "0 ZEC")
            } else {
                DetailRow("Voting power", state.votingWeightZEC.getValue())
            }
            HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
            DetailRow("Voting hotkey", state.hotkeyAddress.getValue())
            if (isIdle) {
                HorizontalDivider(color = ZashiColors.Surfaces.strokeSecondary)
                MemoRow(state.memo.getValue())
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MemoRow(memo: String) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Memo", style = ZashiTypography.textSm, color = ZashiColors.Text.textSecondary)
        VerticalSpacer(4.dp)
        Text(
            memo,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─── Bottom Section ───────────────────────────────────────────────────────────

@Composable
internal fun BottomSection(state: VoteConfirmSubmissionState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                .padding(bottom = ZashiDimensions.Spacing.spacingMd)
    ) {
        when (val s = state.status) {
            is VoteSubmissionStatus.Authorizing -> {
                ProgressCard("Authorizing...", s.progress)
            }

            is VoteSubmissionStatus.Submitting -> {
                ProgressCard(
                    "Submitting vote ${s.current} of ${s.total}...",
                    s.progress
                )
            }

            else -> {}
        }
        if (state.status is VoteSubmissionStatus.Authorizing ||
            state.status is VoteSubmissionStatus.Submitting
        ) {
            VerticalSpacer(8.dp)
        }
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.ctaButton
        )
    }
}

@Composable
private fun ProgressCard(title: String, progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "submission_progress"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZashiColors.Surfaces.bgSecondary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
    ) {
        Column(modifier = Modifier.padding(ZashiDimensions.Spacing.spacingXl)) {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            VerticalSpacer(12.dp)
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                color = ZashiColors.Text.textPrimary,
                trackColor = ZashiColors.Surfaces.bgTertiary,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}
