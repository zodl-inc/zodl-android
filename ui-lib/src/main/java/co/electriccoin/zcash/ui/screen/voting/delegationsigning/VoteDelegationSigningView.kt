package co.electriccoin.zcash.ui.screen.voting.delegationsigning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
fun VoteDelegationSigningView(state: VoteDelegationSigningState) {
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
    state: VoteDelegationSigningState,
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

        if (state.proofProgress != null) {
            LinearProgressIndicator(
                progress = { state.proofProgress },
                modifier = Modifier.fillMaxWidth(),
                color = ZashiColors.Btns.Primary.btnPrimaryBg,
                trackColor = ZashiColors.Surfaces.bgSecondary,
            )
            Spacer(8.dp)
        }

        Text(
            text = state.statusLabel.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )

        Spacer(24.dp)
        Spacer(1f)

        ZashiButton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = ZashiDimensions.Spacing.spacingMd),
            state = state.generateButton
        )
    }
}

@Composable
private fun AppBar(state: VoteDelegationSigningState) {
    ZashiSmallTopAppBar(
        title = "Delegation",
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
private fun DelegationSigningIdlePreview() =
    ZcashTheme {
        VoteDelegationSigningView(
            state =
                VoteDelegationSigningState(
                    title = stringRes(co.electriccoin.zcash.ui.R.string.vote_delegation_signing_title),
                    body = stringRes("Generate a zero-knowledge proof to delegate your vote anonymously."),
                    statusLabel = stringRes(co.electriccoin.zcash.ui.R.string.vote_delegation_signing_status_ready),
                    proofProgress = null,
                    generateButton =
                        ButtonState(
                            text = stringRes(co.electriccoin.zcash.ui.R.string.vote_delegation_signing_cta),
                            style = ButtonStyle.PRIMARY,
                            isEnabled = false
                        ) {},
                    onBack = {}
                )
        )
    }

@PreviewScreens
@Composable
private fun DelegationSigningInProgressPreview() =
    ZcashTheme {
        VoteDelegationSigningView(
            state =
                VoteDelegationSigningState(
                    title = stringRes(co.electriccoin.zcash.ui.R.string.vote_delegation_signing_title),
                    body = stringRes("Generate a zero-knowledge proof to delegate your vote anonymously."),
                    statusLabel = stringRes("Generating proof… 60%"),
                    proofProgress = 0.6f,
                    generateButton =
                        ButtonState(
                            text = stringRes(co.electriccoin.zcash.ui.R.string.vote_delegation_signing_cta),
                            style = ButtonStyle.PRIMARY,
                            isLoading = true
                        ) {},
                    onBack = {}
                )
        )
    }
