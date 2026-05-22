package co.electriccoin.zcash.ui.screen.voting.proposaldetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.model.voting.VoteOptionDisplayColor
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.component.VoteAppBar
import co.electriccoin.zcash.ui.screen.voting.component.VoteRadioIndicator
import co.electriccoin.zcash.ui.screen.voting.component.VoteViewMoreChip
import co.electriccoin.zcash.ui.screen.voting.escapeHorizontalPadding
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.bottomsheet.PollEndedBottomSheet
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.bottomsheet.UnansweredBottomSheet

@Composable
fun VoteProposalDetailView(state: VoteProposalDetailState) {
    ZashiConfirmationBottomSheet(state = state.unverifiedPollWarningSheet)

    val isDescriptionExpanded = remember { mutableStateOf(false) }
    val isDescriptionOverflowing = remember { mutableStateOf(false) }

    BlankBgScaffold(
        topBar = {
            VoteAppBar(
                title = state.positionLabel.getValue(),
                onBack = state.onBack,
                useCloseNavigation = true,
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
                ) {

                    Text(
                        text = state.title.getValue(),
                        style = ZashiTypography.header6,
                        color = ZashiColors.Text.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (state.description.getValue().isNotEmpty()) {
                        Spacer(16.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = state.description.getValue(),
                                style = ZashiTypography.textSm,
                                color = ZashiColors.Text.textPrimary,
                                maxLines = if (isDescriptionExpanded.value) Int.MAX_VALUE else MAX_LINES,
                                overflow =
                                    if (isDescriptionExpanded.value) TextOverflow.Visible else TextOverflow.Ellipsis,
                                onTextLayout = { result ->
                                    if (!isDescriptionExpanded.value) {
                                        isDescriptionOverflowing.value = result.hasVisualOverflow
                                    }
                                },
                            )
                            if (isDescriptionOverflowing.value || isDescriptionExpanded.value) {
                                VoteViewMoreChip(
                                    isExpanded = isDescriptionExpanded.value,
                                    onClick = { isDescriptionExpanded.value = !isDescriptionExpanded.value },
                                )
                            }
                        }
                    }

                    VerticalSpacer(24.dp)
                    VoteOptions(options = state.options)

                }

                if (!state.isLocked) {
                    state.forumUrl?.let { forumUrl ->
                        ForumLinkRow(url = forumUrl)
                        VerticalSpacer(16.dp)
                    }
                    NavigationButtons(state = state)
                }
            }
        }
    )

    if (state.showUnansweredSheet) {
        UnansweredBottomSheet(
            unansweredCount = state.unansweredCount,
            onConfirm = state.onConfirmUnanswered,
            onDismiss = state.onDismissUnanswered,
        )
    }

    if (state.showPollEndedSheet) {
        PollEndedBottomSheet(
            onViewResults = state.onPollEndedViewResults,
            onClose = state.onPollEndedClose,
        )
    }
}

@Composable
private fun ForumLinkRow(url: String) {
    val uriHandler = LocalUriHandler.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(url) }
    ) {
        Surface(
            shape = CircleShape,
            color = ZashiColors.Surfaces.bgTertiary,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(co.electriccoin.zcash.ui.R.drawable.ic_vote_message_chat),
                contentDescription = null,
                tint = ZashiColors.Text.textPrimary,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(16.dp)
        Text(
            text = stringRes(co.electriccoin.zcash.ui.R.string.vote_proposal_detail_forum_link).getValue(),
            style = ZashiTypography.textMd,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ZashiColors.Text.textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun VoteOptions(options: List<VoteVoteOptionRowState>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            VoteOptionRow(option = option)
            if (index < options.lastIndex) {
                ZashiHorizontalDivider(
                    modifier = Modifier.escapeHorizontalPadding(ZashiDimensions.Spacing.spacing3xl)
                )
            }
        }
    }
}

@Composable
private fun VoteOptionRow(option: VoteVoteOptionRowState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !option.isLocked) { option.onSelect() }
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VoteRadioIndicator(isChecked = option.isSelected)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = option.label.getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            option.description?.let { desc ->
                Text(
                    text = desc.getValue(),
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun NavigationButtons(state: VoteProposalDetailState) {
    val hasSelectedOption = state.options.any { option -> option.isSelected }
    if (!state.isEditingFromReview && !hasSelectedOption) {
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.R.string.vote_proposal_detail_next),
                    style = ButtonStyle.PRIMARY,
                    onClick = state.onNext
                )
        )
        return
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        ZashiButton(
            modifier = Modifier.weight(1f),
            state =
                ButtonState(
                    text =
                        if (state.isEditingFromReview) {
                            stringRes(co.electriccoin.zcash.ui.R.string.vote_proposal_detail_save)
                        } else {
                            stringRes(co.electriccoin.zcash.ui.R.string.vote_proposal_detail_next)
                        },
                    style = ButtonStyle.PRIMARY,
                    onClick = state.onNext
                )
        )
    }
}

private const val MAX_LINES = 4

private fun previewOptions(selectedIndex: Int? = null) =
    listOf(
        VoteVoteOptionRowState(
            index = 0,
            label = stringRes("Preserve halvings"),
            description =
                stringRes(
                    "Keep the existing halving schedule for new ZEC. Only fees and donated funds " +
                        "are smoothed and reissued."
                ),
            color = VoteOptionDisplayColor.SUPPORT,
            isSelected = selectedIndex == 0,
            isLocked = false,
            onSelect = {}
        ),
        VoteVoteOptionRowState(
            index = 1,
            label = stringRes("Smooth issuance curve"),
            description =
                stringRes(
                    "Replace halvings with a gradual issuance curve. NSM-recycled funds " +
                        "reissue along the same curve."
                ),
            color = VoteOptionDisplayColor.OPPOSE,
            isSelected = selectedIndex == 1,
            isLocked = false,
            onSelect = {}
        ),
        VoteVoteOptionRowState(
            index = 2,
            label = stringRes("Do not include issuance smoothing"),
            description = stringRes("Do not include issuance smoothing in NU7. (Fee burning still proceeds.)"),
            color = VoteOptionDisplayColor.PURPLE,
            isSelected = selectedIndex == 2,
            isLocked = false,
            onSelect = {}
        ),
        VoteVoteOptionRowState(
            index = 3,
            label = stringRes("Abstain"),
            description = null,
            color = VoteOptionDisplayColor.ABSTAIN,
            isSelected = selectedIndex == 3,
            isLocked = false,
            onSelect = {}
        ),
    )

private fun previewState(selectedIndex: Int? = null) =
    VoteProposalDetailState(
        positionLabel = stringRes("1 of 6"),
        title = stringRes("NSM issuance smoothing"),
        description =
            stringRes(
                "The fee-burning component of the Network Sustainability Mechanism is already approved. " +
                    "The issuance smoothing component is unresolved (84% ZCAP support, 83.5% coinholder opposition)."
            ),
        forumUrl = "https://forum.zcashcommunity.com",
        options = previewOptions(selectedIndex),
        isLocked = false,
        isEditingFromReview = false,
        isFromList = false,
        showUnansweredSheet = false,
        unansweredCount = 0,
        showPollEndedSheet = false,
        unverifiedPollWarningSheet = null,
        onBack = {},
        onNext = {},
        onViewMore = {},
        onConfirmUnanswered = {},
        onDismissUnanswered = {},
        onPollEndedClose = {},
        onPollEndedViewResults = {},
    )

@PreviewScreens
@Composable
private fun VoteProposalDetailPreview() =
    ZcashTheme { VoteProposalDetailView(previewState()) }

@PreviewScreens
@Composable
private fun VoteProposalDetailSelectedPreview() =
    ZcashTheme { VoteProposalDetailView(previewState(selectedIndex = 0)) }
