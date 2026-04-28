package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.R
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
import co.electriccoin.zcash.ui.screen.voting.component.VoteHeaderIconStyle
import co.electriccoin.zcash.ui.screen.voting.component.VoteWalletHeaderIcons
import co.electriccoin.zcash.ui.screen.voting.component.VoteWalletHeaderIconsState

// ─── View ─────────────────────────────────────────────────────────────────────

@Composable
fun VoteConfirmSubmissionView(state: VoteConfirmSubmissionState) {
    val navTitle =
        when (state.status) {
            is VoteSubmissionStatus.Idle -> "Confirmation"
            else -> "Submission"
        }

    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = navTitle,
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
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .scaffoldPadding(padding)
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                ) {
                    VerticalSpacer(24.dp)
                    HeaderSection(state)
                    VerticalSpacer(24.dp)
                    DetailsCard(state)
                    if (state.status is VoteSubmissionStatus.Authorizing ||
                        state.status is VoteSubmissionStatus.Submitting
                    ) {
                        VerticalSpacer(16.dp)
                        Text(
                            text =
                                "Vote submission is in progress, please don\u2019t leave this " +
                                    "screen until it is finished.",
                            style = ZashiTypography.textSm,
                            color = ZashiColors.Text.textSecondary,
                        )
                    }
                    VerticalSpacer(24.dp)
                }

                BottomSection(state)
            }
        }
    )
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection(state: VoteConfirmSubmissionState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        VoteWalletHeaderIcons(
            state =
                state.walletHeaderIcons.copy(
                    style =
                        if (state.status is VoteSubmissionStatus.Completed) {
                            VoteHeaderIconStyle.Confirmed
                        } else {
                            VoteHeaderIconStyle.ThumbsUp
                        }
                )
        )
        Spacer(24.dp)
        Text(
            text = headerTitle(state.status),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(8.dp)
        Text(
            text = headerSubtitle(state),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textSecondary,
        )
    }
}

private fun headerTitle(status: VoteSubmissionStatus) =
    when (status) {
        is VoteSubmissionStatus.Idle -> "Confirm & Submit"
        is VoteSubmissionStatus.Authorizing, is VoteSubmissionStatus.Submitting -> "Submitting vote..."
        is VoteSubmissionStatus.Completed -> "Submission Confirmed!"
        is VoteSubmissionStatus.Failed -> "Submission Failed"
    }

private fun headerSubtitle(state: VoteConfirmSubmissionState) =
    when (val s = state.status) {
        is VoteSubmissionStatus.Idle -> {
            if (state.walletHeaderIcons.isKeystone) {
                "Review before signing the voting authorization with your Keystone. " +
                    "This is final. Your vote will be published and cannot be changed."
            } else {
                "Review before confirming the voting authorization. " +
                    "This is final. Your vote will be published and cannot be changed."
            }
        }

        is VoteSubmissionStatus.Authorizing, is VoteSubmissionStatus.Submitting -> {
            "Vote submission is in progress, please don\u2019t leave this screen until it is finished."
        }

        is VoteSubmissionStatus.Completed -> {
            "Your vote was successfully published and cannot be changed."
        }

        is VoteSubmissionStatus.Failed -> {
            s.error
        }
    }

// ─── Details Card ─────────────────────────────────────────────────────────────

@Composable
private fun DetailsCard(state: VoteConfirmSubmissionState) {
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
private fun BottomSection(state: VoteConfirmSubmissionState) {
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
                ProgressCard("Submitting vote ${s.current} of ${s.total}...", s.progress)
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
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = ZashiColors.Text.textPrimary,
                trackColor = ZashiColors.Surfaces.bgTertiary,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

private fun previewState(status: VoteSubmissionStatus) =
    VoteConfirmSubmissionState(
        status = status,
        roundTitle = stringRes("NU7 Sentiment Poll"),
        votingWeightZEC = stringRes("1.2500 ZEC"),
        hotkeyAddress = stringRes("zs1xk9...f7q2m"),
        walletHeaderIcons = VoteWalletHeaderIconsState(isKeystone = false),
        memo = stringRes("I am authorizing this hotkey managed by my wallet to vote on NU7 Sentiment Poll with 1.2500 ZEC."),
        ctaButton = ButtonState(stringRes("Confirm"), ButtonStyle.PRIMARY) {},
        onBack = {},
    )

@PreviewScreens @Composable
private fun ConfirmSubmissionPreview_Idle() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Idle)) }

@PreviewScreens @Composable
private fun ConfirmSubmissionPreview_Authorizing() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Authorizing(0.45f))) }

@PreviewScreens @Composable
private fun ConfirmSubmissionPreview_Submitting() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Submitting(5, 11, 0.45f))) }

@PreviewScreens @Composable
private fun ConfirmSubmissionPreview_Completed() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Completed)) }

@PreviewScreens @Composable
private fun ConfirmSubmissionPreview_Failed() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Failed("Network error. Please try again."))) }
