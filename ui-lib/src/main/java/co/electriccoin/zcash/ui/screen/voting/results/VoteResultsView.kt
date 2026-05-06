package co.electriccoin.zcash.ui.screen.voting.results

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.accentColor

private const val BADGE_CORNER_RADIUS = 50
private const val CHECK_ICON_SIZE_DP = 12

@Composable
fun VoteResultsView(state: VoteResultsState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = stringRes(R.string.vote_top_bar_title).getValue(),
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = state.onBack,
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                },
                colors = ZcashTheme.colors.topAppBarColors orDark
                    ZcashTheme.colors.topAppBarColors.copyColors(containerColor = Color.Transparent)
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

                state.votedMetaLine?.let { votedMetaLine ->
                    Spacer(4.dp)
                    Text(
                        text = votedMetaLine.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                    )
                }

                Spacer(24.dp)
                Text(
                    text = stringRes(R.string.vote_results_title).getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(16.dp)

                state.proposals.forEach { proposal ->
                    ProposalResultCard(proposal)
                    Spacer(16.dp)
                }

                Spacer(24.dp)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.zipNumber?.let { zipNumber ->
                    ZipBadge(label = zipNumber.getValue())
                }
                Spacer(1f)
                state.winnerLabel?.let { winner ->
                    WinnerBadge(
                        label = winner.getValue(),
                        color = state.winnerColor,
                        showIcon = state.showWinnerSeal,
                        isTie = !state.showWinnerSeal,
                    )
                }
            }

            Spacer(12.dp)

            Text(
                text = state.title.getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )

            if (state.description.getValue().isNotEmpty()) {
                Spacer(4.dp)
                Text(
                    text = state.description.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(16.dp)

            state.options.forEachIndexed { index, option ->
                if (index > 0) {
                    Spacer(12.dp)
                }
                OptionResultBar(option)
            }

            Spacer(8.dp)

            Text(
                text = state.totalZec.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@Composable
private fun OptionResultBar(option: VoteOptionResultState) {
    val barColor = optionBarColor(option.color, option.isWinner)

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
            text = option.amountZec.getValue(),
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
        trackColor = ZashiColors.Surfaces.strokeSecondary,
    )
}

@Composable
private fun ZipBadge(label: String) {
    Surface(
        color = ZashiColors.Surfaces.bgTertiary,
        shape = RoundedCornerShape(BADGE_CORNER_RADIUS),
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
private fun WinnerBadge(
    label: String,
    color: VoteOptionDisplayColor,
    showIcon: Boolean,
    isTie: Boolean,
) {
    val (backgroundColor, textColor) =
        if (isTie) {
            ZashiColors.Surfaces.bgTertiary to ZashiColors.Text.textPrimary
        } else {
            color.accentColor() to Color.White
        }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(BADGE_CORNER_RADIUS),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (showIcon) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(CHECK_ICON_SIZE_DP.dp),
                )
                Spacer(4.dp)
            }
            Text(
                text =
                    if (isTie || showIcon) {
                        stringRes(R.string.vote_results_winner, label).getValue()
                    } else {
                        label
                    },
                style = ZashiTypography.textXs,
                color = textColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun optionBarColor(
    color: VoteOptionDisplayColor,
    isWinner: Boolean,
): Color {
    if (!isWinner) return ZashiColors.Utility.Gray.utilityGray500
    return color.accentColor()
}
