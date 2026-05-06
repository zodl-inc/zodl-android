package co.electriccoin.zcash.ui.screen.voting.confirmsubmission

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.component.VoteHeaderIconStyle
import co.electriccoin.zcash.ui.screen.voting.component.VoteWalletHeaderIcons
import co.electriccoin.zcash.ui.screen.voting.component.VoteWalletHeaderIconsState

@Composable
fun VoteConfirmSubmissionView(state: VoteConfirmSubmissionState) {
    BackHandler { state.onBack() }

    ZashiConfirmationBottomSheet(state = state.errorSheet)

    val navTitle = when (state.status) {
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
                colors = ZcashTheme.colors.topAppBarColors orDark
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    )
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scaffoldPadding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                ) {
                    VerticalSpacer(24.dp)
                    HeaderSection(state)
                    VerticalSpacer(24.dp)
                    VoteSubmissionDetailsCard(state)
                    if (state.status.isInFlight()) {
                        VerticalSpacer(16.dp)
                        Text(
                            text = "Vote submission is in progress, please don't leave this " +
                                "screen until it is finished.",
                            style = ZashiTypography.textSm,
                            color = ZashiColors.Text.textSecondary,
                        )
                    }
                    VerticalSpacer(24.dp)
                }

                VoteSubmissionBottomSection(state)
            }
        }
    )
}

@Composable
fun VoteConfirmSubmissionLoadingView() {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = "Submission",
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
private fun HeaderSection(state: VoteConfirmSubmissionState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        VoteWalletHeaderIcons(
            state =
                VoteWalletHeaderIconsState(
                    isKeystone = state.isKeystoneUser,
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

private fun headerTitle(status: VoteSubmissionStatus) = when (status) {
    is VoteSubmissionStatus.Idle -> "Confirm & Submit"
    is VoteSubmissionStatus.LocalAuthorizing -> "Authorizing vote..."
    is VoteSubmissionStatus.Authorizing, is VoteSubmissionStatus.Submitting -> "Submitting vote..."
    is VoteSubmissionStatus.Completed -> "Submission Confirmed!"
    is VoteSubmissionStatus.LocalAuthFailed -> "Authentication Failed"
    is VoteSubmissionStatus.ProtocolAuthFailed -> "Authorization Failed"
    is VoteSubmissionStatus.SubmissionFailed -> "Submission Failed"
}

private fun headerSubtitle(state: VoteConfirmSubmissionState) = when (val status = state.status) {
    is VoteSubmissionStatus.Idle ->
        if (state.isKeystoneUser) {
            "Review before signing the voting authorization with your Keystone. " +
                "This is final. Your vote will be published and cannot be changed."
        } else {
            "Review before confirming the voting authorization. " +
                "This is final. Your vote will be published and cannot be changed."
        }

    is VoteSubmissionStatus.LocalAuthorizing,
    is VoteSubmissionStatus.Authorizing,
    is VoteSubmissionStatus.Submitting ->
        "Vote submission is in progress, please don't leave this screen until it is finished."

    is VoteSubmissionStatus.Completed ->
        "Your vote was successfully published and cannot be changed."

    is VoteSubmissionStatus.LocalAuthFailed -> status.error

    is VoteSubmissionStatus.ProtocolAuthFailed -> status.error

    is VoteSubmissionStatus.SubmissionFailed -> status.error
}

private fun previewState(status: VoteSubmissionStatus) = VoteConfirmSubmissionState(
    status = status,
    roundTitle = stringRes("NU7 Sentiment Poll"),
    votingWeightZEC = stringRes("1.2500 ZEC"),
    hotkeyAddress = stringRes("zs1xk9...f7q2m"),
    isKeystoneUser = false,
    includesAuthorizationProgress = true,
    memo = stringRes("I am authorizing this hotkey managed by my wallet to vote on NU7 Sentiment Poll with 1.2500 ZEC."),
    ctaButton = ButtonState(
        text = stringRes("Confirm"),
        style = ButtonStyle.PRIMARY,
        onClick = {}
    ),
    errorSheet = null,
    onBack = {},
)

@PreviewScreens
@Composable
private fun ConfirmSubmissionPreviewIdle() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Idle)) }

@PreviewScreens
@Composable
private fun ConfirmSubmissionPreviewAuthorizing() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Authorizing(0.45f))) }

@PreviewScreens
@Composable
private fun ConfirmSubmissionPreviewSubmitting() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Submitting(5, 11, 0.45f))) }

@PreviewScreens
@Composable
private fun ConfirmSubmissionPreviewCompleted() =
    ZcashTheme { VoteConfirmSubmissionView(previewState(VoteSubmissionStatus.Completed)) }

@PreviewScreens
@Composable
private fun ConfirmSubmissionPreviewFailed() =
    ZcashTheme {
        VoteConfirmSubmissionView(
            previewState(VoteSubmissionStatus.SubmissionFailed("Network error. Please try again."))
        )
    }
