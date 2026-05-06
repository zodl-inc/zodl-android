package co.electriccoin.zcash.ui.screen.voting.ineligible

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
fun VoteIneligibleView(state: VoteIneligibleState) {
    BlankBgScaffold(
        topBar = { VoteIneligibleAppBar(state) },
        content = { padding ->
            VoteIneligibleContent(
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
private fun VoteIneligibleContent(
    state: VoteIneligibleState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = state.title.getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(12.dp)
        Text(
            text = state.body.getValue(),
            style = ZashiTypography.textMd,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(24.dp)
        Spacer(1f)

        ZashiButton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = ZashiDimensions.Spacing.spacingMd),
            state = state.closeButton
        )
    }
}

@Composable
private fun VoteIneligibleAppBar(state: VoteIneligibleState) {
    ZashiSmallTopAppBar(
        title = "Voting Eligibility",
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

@PreviewScreens
@Composable
private fun IneligiblePreview() =
    ZcashTheme {
        VoteIneligibleView(
            state =
                VoteIneligibleState(
                    title = stringRes(co.electriccoin.zcash.ui.R.string.vote_ineligible_title),
                    body = stringRes("Your wallet did not hold any shielded ZEC at the snapshot height."),
                    closeButton =
                        ButtonState(
                            text = stringRes(co.electriccoin.zcash.ui.R.string.vote_close),
                            style = ButtonStyle.PRIMARY
                        ) {},
                    onBack = {}
                )
        )
    }
