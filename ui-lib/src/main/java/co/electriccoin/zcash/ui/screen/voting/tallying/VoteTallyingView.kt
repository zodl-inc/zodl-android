package co.electriccoin.zcash.ui.screen.voting.tallying

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
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
fun VoteTallyingView(state: VoteTallyingState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = "Governance",
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
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                VerticalSpacer(ZashiDimensions.Spacing.spacingXl)

                // Clock-like circle icon
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color =
                            ZashiColors.Btns.Primary.btnPrimaryBg
                                .copy(alpha = 0.12f)
                    ) {}
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = null,
                        tint = ZashiColors.Btns.Primary.btnPrimaryBg,
                        modifier = Modifier.size(32.dp)
                    )
                }

                VerticalSpacer(24.dp)

                // "Votes Closed" title
                Text(
                    text = "Votes Closed",
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                    textAlign = TextAlign.Center
                )

                VerticalSpacer(12.dp)

                // Description
                Text(
                    text = "Results are being tallied. The election authority is decrypting the aggregate vote totals.",
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                VerticalSpacer(16.dp)

                // Tallying progress indicator row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = ZashiColors.Btns.Primary.btnPrimaryBg,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Tallying",
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.Medium,
                        color = ZashiColors.Text.textTertiary
                    )
                }

                VerticalSpacer(24.dp)

                // Round info card
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = ZashiColors.Surfaces.strokeSecondary,
                                shape = RoundedCornerShape(14.dp)
                            ),
                    shape = RoundedCornerShape(14.dp),
                    color = ZashiColors.Surfaces.bgPrimary
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailRow(label = "Round", value = state.roundTitle.getValue())
                        DetailRow(label = "Ended", value = state.endedLabel.getValue())
                        DetailRow(label = "Proposals", value = state.proposalCount.getValue())
                    }
                }

                VerticalSpacer(ZashiDimensions.Spacing.spacingXl)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            fontWeight = FontWeight.Medium
        )
        androidx.compose.foundation.layout
            .Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

@PreviewScreens
@Composable
private fun TallyingPreview() =
    ZcashTheme {
        VoteTallyingView(
            state =
                VoteTallyingState(
                    roundTitle = stringRes("ZF Grant Funding — Q3 2026"),
                    endedLabel = stringRes("Apr 20, 2026"),
                    proposalCount = stringRes("3"),
                    onBack = {}
                )
        )
    }
