package co.electriccoin.zcash.ui.screen.voting.howtovote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.Spacer
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
import co.electriccoin.zcash.ui.screen.voting.component.VoteWalletHeaderIcons
import co.electriccoin.zcash.ui.screen.voting.component.VoteWalletHeaderIconsState
import co.electriccoin.zcash.ui.R as AppR

@Composable
fun VoteHowToVoteView(state: VoteHowToVoteState) {
    BackHandler { state.onBack() }
    BlankBgScaffold(
        topBar = { AppBar(state) },
        content = { padding ->
            Content(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(padding)
            )
        }
    )
}

@Composable
private fun Content(
    state: VoteHowToVoteState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        VoteWalletHeaderIcons(state = state.walletHeaderIcons)
        Spacer(24.dp)
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        state.subtitle?.let { subtitle ->
            Spacer(8.dp)
            Text(
                text = subtitle.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary
            )
        }
        Spacer(24.dp)

        state.steps.forEach { step ->
            StepRow(step = step)
            Spacer(16.dp)
        }

        Spacer(1f)

        state.infoText?.let { info ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZashiDimensions.Spacing.spacing2xl)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = ZashiColors.Text.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(8.dp)
                Text(
                    text = info.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(ZashiDimensions.Spacing.spacingXl)
        }

        ZashiButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ZashiDimensions.Spacing.spacingMd),
            state = state.continueButton
        )
    }
}

@Composable
private fun StepRow(step: VoteStep) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = ZashiColors.Btns.Primary.btnPrimaryBg,
            modifier = Modifier.size(28.dp)
        ) {
            Text(
                text = step.number,
                style = ZashiTypography.textSm,
                color = ZashiColors.Btns.Primary.btnPrimaryFg,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp)
            )
        }
        Spacer(12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title.getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(4.dp)
            Text(
                text = step.description.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary
            )
        }
    }
}

@Composable
private fun AppBar(state: VoteHowToVoteState) {
    ZashiSmallTopAppBar(
        title = stringResource(AppR.string.vote_top_bar_title),
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

@PreviewScreens
@Composable
private fun HowToVotePreview() =
    ZcashTheme {
        VoteHowToVoteView(
            state = VoteHowToVoteState(
                title = stringRes("How to vote with Zodl"),
                subtitle = stringRes(
                    "Your ZEC gives you a voice. Shape the future of the Zcash network by voting on active proposals."
                ),
                steps = listOf(
                    VoteStep(
                        number = "1",
                        title = stringRes("Voting on Proposals"),
                        description = stringRes(
                            "Vote Support, Oppose, or Abstain on each question. " +
                                "You can skip questions and change your vote before submitting."
                        )
                    ),
                    VoteStep(
                        number = "2",
                        title = stringRes("Authorize and Submit"),
                        description = stringRes(
                            "When you're ready, you'll confirm a small authorization transaction " +
                                "and submit your vote in one step. After submission, your vote cannot be changed."
                        )
                    ),
                ),
                infoText = stringRes(
                    "Your balance at the snapshot time determines your voting weight. " +
                        "You don't need to move your funds anywhere."
                ),
                walletHeaderIcons = VoteWalletHeaderIconsState(isKeystone = false),
                continueButton = ButtonState(
                    text = stringRes("Continue"),
                    style = ButtonStyle.PRIMARY
                ) {},
                onBack = {}
            )
        )
    }
