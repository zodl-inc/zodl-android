package co.electriccoin.zcash.ui.screen.voting.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.common.CommonShimmerLoadingScreen
import co.electriccoin.zcash.ui.screen.voting.component.VoteAppBar
import co.electriccoin.zcash.ui.screen.voting.component.VoteViewMoreChip
import co.electriccoin.zcash.ui.screen.voting.answerColors
import co.electriccoin.zcash.ui.screen.voting.component.ZipBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteResultsView(state: VoteResultsState) {
    BlankBgScaffold(
        topBar = {
            VoteAppBar(
                title = stringResource(R.string.vote_top_bar_title),
                onBack = state.onBack,
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(top = padding.calculateTopPadding())
                            .verticalScroll(rememberScrollState())
                            .scaffoldScrollPadding(
                                padding,
                                top = ZashiDimensions.Spacing.spacingLg,
                                start = ZashiDimensions.Spacing.spacing3xl,
                                end = ZashiDimensions.Spacing.spacing3xl
                            )
                ) {
                    Spacer(ZashiDimensions.Spacing.spacingLg)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = state.roundTitle.getValue(),
                            style = ZashiTypography.header6,
                            color = ZashiColors.Text.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }

                    if (state.roundDescription.getValue().isNotEmpty()) {
                        Spacer(8.dp)
                        Text(
                            text = state.roundDescription.getValue(),
                            style = ZashiTypography.textSm,
                            color = ZashiColors.Text.textTertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(4.dp)
                        VoteViewMoreChip(onClick = { state.onViewMore?.invoke() })
                    }

                    // state.votedMetaLine?.let { votedMetaLine ->
                    //     Spacer(4.dp)
                    //     Text(
                    //         text = votedMetaLine.getValue(),
                    //         style = ZashiTypography.textXs,
                    //         color = ZashiColors.Text.textTertiary,
                    //     )
                    // }

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
                }

                ZashiButton(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = ZashiDimensions.Spacing.spacing3xl,
                                end = ZashiDimensions.Spacing.spacing3xl,
                                bottom = padding.calculateBottomPadding() + ZashiDimensions.Spacing.spacing3xl
                            ),
                    state = state.doneButton,
                )
            }
        }
    )
}

@Composable
fun VoteResultsLoadingView(onBack: () -> Unit) {
    BlankBgScaffold(
        topBar = {
            VoteAppBar(
                title = stringResource(R.string.vote_top_bar_title),
                onBack = onBack,
            )
        },
        content = { padding ->
            CommonShimmerLoadingScreen(
                shimmerItemsCount = 6,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .scaffoldScrollPadding(padding),
                showDivider = false,
            )
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
            state.zipNumber?.let { zipNumber ->
                ZipBadge(label = zipNumber.getValue())
                Spacer(12.dp)
            }

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.votedLabel?.let { votedLabel ->
                    Text(
                        text = votedLabel.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Utility.Gray.utilityGray500,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                } ?: run {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = state.totalZec.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Utility.Gray.utilityGray500,
                )
            }
        }
    }
}

@Composable
private fun OptionResultBar(option: VoteOptionResultState) {
    val barColor: Color
    val textColor: Color
    if (option.isWinner) {
        val colors = option.color.answerColors()
        barColor = colors.labelColor
        textColor = colors.textColor
    } else {
        barColor = ZashiColors.Utility.Gray.utilityGray500
        textColor = ZashiColors.Text.textTertiary
    }
    val fontWeight = if (option.isWinner) FontWeight.SemiBold else FontWeight.Normal

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = option.label.getValue(),
            style = ZashiTypography.textSm,
            color = textColor,
            fontWeight = fontWeight,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = option.amountZec.getValue(),
            style = ZashiTypography.textSm,
            color = textColor,
            fontWeight = fontWeight,
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
        drawStopIndicator = {},
    )
}

private fun previewProposalResults() =
    listOf(
        VoteProposalResultState(
            zipNumber = stringRes("ZIP-317"),
            title = stringRes("Proportional Transfer Fee Mechanism"),
            description =
                stringRes(
                    "Replace the current fixed fee with a " +
                        "proportional fee based on the number of logical actions."
                ),
            options =
                listOf(
                    VoteOptionResultState(
                        label = stringRes("Yes"),
                        amountZec = stringRes("1,240,000 ZEC"),
                        fraction = 0.76f,
                        isWinner = true,
                        color = VoteOptionDisplayColor.SUPPORT,
                    ),
                    VoteOptionResultState(
                        label = stringRes("No"),
                        amountZec = stringRes("390,000 ZEC"),
                        fraction = 0.24f,
                        isWinner = false,
                        color = VoteOptionDisplayColor.OPPOSE,
                    ),
                ),
            totalZec = stringRes("Total: 1,630,000 ZEC"),
            votedLabel = stringRes("Voted: Yes"),
        ),
        VoteProposalResultState(
            zipNumber = stringRes("ZIP-320"),
            title = stringRes("Memo Encryption Upgrade"),
            description = stringRes(""),
            options =
                listOf(
                    VoteOptionResultState(
                        label = stringRes("Yes"),
                        amountZec = stringRes("500,000 ZEC"),
                        fraction = 0.5f,
                        isWinner = false,
                        color = VoteOptionDisplayColor.SUPPORT,
                    ),
                    VoteOptionResultState(
                        label = stringRes("No"),
                        amountZec = stringRes("500,000 ZEC"),
                        fraction = 0.5f,
                        isWinner = false,
                        color = VoteOptionDisplayColor.OPPOSE,
                    ),
                ),
            totalZec = stringRes("Total: 1,000,000 ZEC"),
            votedLabel = null,
        ),
        VoteProposalResultState(
            zipNumber = stringRes("ZIP-320"),
            title = stringRes("Memo Encryption Upgrade"),
            description = stringRes(""),
            options =
                listOf(
                    VoteOptionResultState(
                        label = stringRes("Yes"),
                        amountZec = stringRes("500,000 ZEC"),
                        fraction = 0.5f,
                        isWinner = false,
                        color = VoteOptionDisplayColor.SUPPORT,
                    ),
                    VoteOptionResultState(
                        label = stringRes("No"),
                        amountZec = stringRes("500,000 ZEC"),
                        fraction = 0.5f,
                        isWinner = false,
                        color = VoteOptionDisplayColor.OPPOSE,
                    ),
                ),
            totalZec = stringRes("Total: 1,000,000 ZEC"),
            votedLabel = null,
        ),
    )

private fun previewState(isLoading: Boolean = false) =
    VoteResultsState.preview.copy(
        isLoadingResults = isLoading,
        proposals = previewProposalResults(),
    )

@PreviewScreens
@Composable
private fun VoteResultsPreview() =
    ZcashTheme { VoteResultsView(previewState()) }

@PreviewScreens
@Composable
private fun VoteResultsLoadingPreview() =
    ZcashTheme { VoteResultsLoadingView(onBack = {}) }
