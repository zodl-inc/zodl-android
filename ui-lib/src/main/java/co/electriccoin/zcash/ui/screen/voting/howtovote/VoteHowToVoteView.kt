package co.electriccoin.zcash.ui.screen.voting.howtovote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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

@Composable
fun VoteHowToVoteView(state: VoteHowToVoteState) {
    BackHandler { state.onBack() }
    BlankBgScaffold(
        topBar = { AppBar(state) },
        content = { padding ->
            Content(
                state = state,
                modifier =
                    Modifier
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
        HowToVoteHeaderIcons(isKeystone = state.isKeystoneUser)
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

        state.infoText?.let { info ->
            Spacer(8.dp)
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ZashiColors.Surfaces.bgSecondary,
                shape =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(ZashiDimensions.Radius.radiusXl)
            ) {
                Text(
                    text = info.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary,
                    modifier = Modifier.padding(ZashiDimensions.Spacing.spacingMd)
                )
            }
        }

        Spacer(1f)

        ZashiButton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = ZashiDimensions.Spacing.spacingMd),
            state = state.continueButton
        )
    }
}

@Composable
private fun HowToVoteHeaderIcons(isKeystone: Boolean) {
    Box(contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = CircleShape,
            color = ZashiColors.Text.textPrimary,
            modifier = Modifier.size(48.dp)
        ) {
            if (isKeystone) {
                Icon(
                    painter = painterResource(R.drawable.ic_item_keystone),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.zashi_logo_without_text),
                    contentDescription = null,
                    tint = ZashiColors.Surfaces.bgPrimary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = ZashiColors.Surfaces.bgTertiary,
            modifier =
                Modifier
                    .size(48.dp)
                    .offset(x = 36.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_radio_button_checked),
                contentDescription = null,
                tint = ZashiColors.Text.textPrimary,
                modifier = Modifier.padding(12.dp)
            )
        }
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
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
    )
}

@PreviewScreens
@Composable
private fun HowToVotePreview() =
    ZcashTheme {
        VoteHowToVoteView(
            state =
                VoteHowToVoteState(
                    title = stringRes("How to vote with Zodl"),
                    subtitle =
                        stringRes(
                            "Your ZEC gives you a voice. Shape the future of the Zcash network by voting on active proposals."
                        ),
                    steps =
                        listOf(
                            VoteStep(
                                number = "1",
                                title = stringRes("Voting on Proposals"),
                                description =
                                    stringRes(
                                        "Vote Support, Oppose, or Abstain on each question. " +
                                            "You can skip questions and change your vote before submitting."
                                    )
                            ),
                            VoteStep(
                                number = "2",
                                title = stringRes("Authorize and Submit"),
                                description =
                                    stringRes(
                                        "When you're ready, you'll confirm a small authorization transaction " +
                                            "and submit your vote in one step. After submission, your vote cannot be changed."
                                    )
                            ),
                        ),
                    infoText =
                        stringRes(
                            "Your balance at the snapshot time determines your voting weight. " +
                                "You don't need to move your funds anywhere."
                        ),
                    continueButton =
                        ButtonState(
                            text = stringRes("Continue"),
                            style = ButtonStyle.PRIMARY
                        ) {},
                    onBack = {}
                )
        )
    }
