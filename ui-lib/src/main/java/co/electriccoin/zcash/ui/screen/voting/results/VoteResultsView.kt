package co.electriccoin.zcash.ui.screen.voting.results

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

// ─── View ─────────────────────────────────────────────────────────────────────

@Composable
fun VoteResultsView(state: VoteResultsState) {
    BlankBgScaffold(
        topBar = {
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
                            containerColor = Color.Transparent
                        )
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding)
                        .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
            ) {
                Spacer(24.dp)

                // ── Round header ──────────────────────────────────────────
                Text(
                    text = state.roundTitle.getValue(),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.roundDescription.getValue().isNotEmpty()) {
                    Spacer(8.dp)
                    Text(
                        text = state.roundDescription.getValue(),
                        style = ZashiTypography.textSm,
                        color = ZashiColors.Text.textTertiary,
                    )
                }
                if (state.metaLine != null) {
                    Spacer(4.dp)
                    Text(
                        text = state.metaLine.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                    )
                }

                Spacer(24.dp)

                // ── "Results" subtitle ────────────────────────────────────
                Text(
                    text = "Results",
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(16.dp)

                // ── Proposal cards ────────────────────────────────────────
                if (state.proposals.isEmpty()) {
                    Text(
                        text = "Results not yet available.",
                        style = ZashiTypography.textMd,
                        color = ZashiColors.Text.textTertiary,
                    )
                } else {
                    state.proposals.forEach { proposal ->
                        ProposalResultCard(proposal)
                        Spacer(16.dp)
                    }
                }

                Spacer(1f)

                // ── Done button ───────────────────────────────────────────
                ZashiButton(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = ZashiDimensions.Spacing.spacingMd),
                    state = state.doneButton,
                )
            }
        }
    )
}

// ─── Proposal Result Card ─────────────────────────────────────────────────────

@Composable
private fun ProposalResultCard(state: VoteProposalResultState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ZashiColors.Surfaces.bgSecondary,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(ZashiDimensions.Spacing.spacingXl)
        ) {
            // ZIP badge + winner badge row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.zipNumber != null) {
                    ZipBadge(label = state.zipNumber.getValue())
                }
                Spacer(1f)
                if (state.winnerLabel != null) {
                    WinnerBadge(label = "Winner: ${state.winnerLabel.getValue()}", color = state.winnerColor)
                } else {
                    // Show placeholder so the row height stays consistent (mirrors iOS "—" badge)
                    WinnerBadge(label = "—", color = VoteOptionColor.OTHER)
                }
            }

            Spacer(12.dp)

            // Title
            Text(
                text = state.title.getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )

            // Description
            if (state.description.getValue().isNotEmpty()) {
                Spacer(4.dp)
                Text(
                    text = state.description.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    maxLines = 2,
                )
            }

            Spacer(16.dp)

            // Result bars
            state.options.forEachIndexed { index, option ->
                if (index > 0) Spacer(12.dp)
                OptionResultBar(option = option, totalOptions = state.options.size)
            }

            Spacer(8.dp)

            // Total ZEC
            Text(
                text = state.totalZEC.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

// ─── Option Result Bar ────────────────────────────────────────────────────────

@Composable
private fun OptionResultBar(option: VoteOptionResultState, totalOptions: Int) {
    val barColor = optionBarColor(option.color, option.isWinner)
    val trackColor = ZashiColors.Surfaces.strokeSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = option.label.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = option.amountZEC.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
    }
    VerticalSpacer(4.dp)
    LinearProgressIndicator(
        progress = { option.fraction },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(8.dp),
        color = barColor,
        trackColor = trackColor,
    )
}

// ─── Badges ───────────────────────────────────────────────────────────────────

@Composable
private fun ZipBadge(label: String) {
    Surface(
        color = ZashiColors.Surfaces.bgTertiary,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            style = ZashiTypography.textXs,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WinnerBadge(label: String, color: VoteOptionColor) {
    val (bgColor, textColor) =
        when (color) {
            VoteOptionColor.SUPPORT -> Color(0xFF22C55E) to Color.White
            VoteOptionColor.OPPOSE -> Color(0xFFEF4444) to Color.White
            VoteOptionColor.ABSTAIN -> Color(0xFF3B82F6) to Color.White
            VoteOptionColor.OTHER -> Color(0xFF6B7280) to Color.White
        }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            style = ZashiTypography.textXs,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ─── Color helpers ────────────────────────────────────────────────────────────

private fun optionBarColor(color: VoteOptionColor, isWinner: Boolean): Color {
    if (!isWinner) return Color(0xFF6B7280)
    return when (color) {
        VoteOptionColor.SUPPORT -> Color(0xFF22C55E)
        VoteOptionColor.OPPOSE -> Color(0xFFEF4444)
        VoteOptionColor.ABSTAIN -> Color(0xFF3B82F6)
        VoteOptionColor.OTHER -> Color(0xFF6B7280)
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

@PreviewScreens
@Composable
private fun ResultsPreview() =
    ZcashTheme {
        VoteResultsView(
            state =
                VoteResultsState(
                    roundTitle = stringRes("NU7 Sentiment Poll"),
                    roundDescription = stringRes("Community sentiment on three ZIPs proposed for NU7."),
                    metaLine = null,
                    isLoadingResults = false,
                    proposals =
                        listOf(
                            VoteProposalResultState(
                                zipNumber = stringRes("ZIP-227"),
                                title = stringRes("Zcash Shielded Assets (ZSAs)"),
                                description = stringRes("Extend the Orchard protocol to support user-defined assets."),
                                options =
                                    listOf(
                                        VoteOptionResultState(
                                            label = stringRes("Support"),
                                            amountZEC = stringRes("750.00 ZEC"),
                                            fraction = 0.75f,
                                            color = VoteOptionColor.SUPPORT,
                                            isWinner = true,
                                        ),
                                        VoteOptionResultState(
                                            label = stringRes("Oppose"),
                                            amountZEC = stringRes("250.00 ZEC"),
                                            fraction = 0.25f,
                                            color = VoteOptionColor.OPPOSE,
                                            isWinner = false,
                                        ),
                                    ),
                                totalZEC = stringRes("Total: 1000.00 ZEC"),
                                winnerLabel = stringRes("Support"),
                                winnerColor = VoteOptionColor.SUPPORT,
                            ),
                            VoteProposalResultState(
                                zipNumber = stringRes("ZIP-235"),
                                title = stringRes("Network Sustainability Mechanism"),
                                description = stringRes("Redirect a portion of the block subsidy to a sustainability fund."),
                                options =
                                    listOf(
                                        VoteOptionResultState(
                                            label = stringRes("Support"),
                                            amountZEC = stringRes("400.00 ZEC"),
                                            fraction = 0.40f,
                                            color = VoteOptionColor.SUPPORT,
                                            isWinner = false,
                                        ),
                                        VoteOptionResultState(
                                            label = stringRes("Oppose"),
                                            amountZEC = stringRes("500.00 ZEC"),
                                            fraction = 0.50f,
                                            color = VoteOptionColor.OPPOSE,
                                            isWinner = true,
                                        ),
                                        VoteOptionResultState(
                                            label = stringRes("Abstain"),
                                            amountZEC = stringRes("100.00 ZEC"),
                                            fraction = 0.10f,
                                            color = VoteOptionColor.ABSTAIN,
                                            isWinner = false,
                                        ),
                                    ),
                                totalZEC = stringRes("Total: 1000.00 ZEC"),
                                winnerLabel = stringRes("Oppose"),
                                winnerColor = VoteOptionColor.OPPOSE,
                            ),
                        ),
                    doneButton =
                        ButtonState(
                            text = stringRes("Done"),
                            style = ButtonStyle.PRIMARY,
                            onClick = {},
                        ),
                    onBack = {},
                )
        )
    }
