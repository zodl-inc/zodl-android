@file:Suppress("TooManyFunctions")

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
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
import co.electriccoin.zcash.ui.design.component.VerticalSpacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiCheckboxIndicator
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
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
fun VoteProposalDetailView(state: VoteProposalDetailState) {
    BlankBgScaffold(
        topBar = { AppBar(state) },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .scaffoldPadding(padding)
            ) {
                // Scrollable content
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                ) {
                    VerticalSpacer(12.dp)
                    Text(
                        text = state.title.getValue(),
                        style = ZashiTypography.header6,
                        color = ZashiColors.Text.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.description.getValue().isNotEmpty()) {
                        Spacer(8.dp)
                        Text(
                            text = state.description.getValue(),
                            style = ZashiTypography.textSm,
                            color = ZashiColors.Text.textPrimary,
                        )
                    }
                    if (state.forumUrl != null) {
                        Spacer(16.dp)
                        ForumLinkRow(url = state.forumUrl)
                    }
                    VerticalSpacer(24.dp)
                }

                // Fixed bottom: vote options + nav buttons
                BottomSection(state = state)
            }
        }
    )

    if (state.showUnansweredSheet) {
        UnansweredBottomSheet(state = state)
    }

    if (state.showPollEndedSheet) {
        PollEndedBottomSheet(state = state)
    }
}

// ─── App Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun AppBar(state: VoteProposalDetailState) {
    ZashiSmallTopAppBar(
        title = state.positionLabel.getValue(),
        navigationAction = {
            ZashiTopAppBarCloseNavigation(
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

// ─── Forum Link ───────────────────────────────────────────────────────────────

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
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ZashiColors.Text.textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─── Bottom Section ───────────────────────────────────────────────────────────

@Composable
private fun BottomSection(state: VoteProposalDetailState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = ZashiDimensions.Spacing.spacingMd)
    ) {
        VoteOptions(options = state.options)
        if (!state.isLocked) {
            Spacer(20.dp)
            NavigationButtons(state = state)
        }
    }
}

// ─── Vote Options ─────────────────────────────────────────────────────────────

@Composable
private fun VoteOptions(options: List<VoteVoteOptionRowState>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            VoteOptionRow(option = option)
        }
    }
}

@Composable
private fun VoteOptionRow(option: VoteVoteOptionRowState) {
    val bgColor = if (option.isSelected) option.color.toComposeBgColor() else ZashiColors.Surfaces.bgSecondary
    val textColor = if (option.isSelected) Color.White else ZashiColors.Text.textPrimary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
        onClick = { if (!option.isLocked) option.onSelect() },
        enabled = !option.isLocked,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = option.label.getValue(),
                style = ZashiTypography.textMd,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            ZashiCheckboxIndicator(isChecked = option.isSelected)
        }
    }
}

@Composable
private fun VoteVoteOptionColor.toComposeBgColor(): Color =
    when (this) {
        VoteVoteOptionColor.SUPPORT -> ZashiColors.Utility.SuccessGreen.utilitySuccess500
        VoteVoteOptionColor.OPPOSE -> ZashiColors.Utility.ErrorRed.utilityError500
        VoteVoteOptionColor.ABSTAIN -> ZashiColors.Utility.HyperBlue.utilityBlueDark500
        VoteVoteOptionColor.OTHER -> ZashiColors.Utility.Gray.utilityGray500
    }

// ─── Navigation Buttons ───────────────────────────────────────────────────────

@Composable
private fun NavigationButtons(state: VoteProposalDetailState) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ZashiButton(
            modifier = Modifier.weight(1f),
            state =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.R.string.vote_proposal_detail_back),
                    style = ButtonStyle.TERTIARY,
                    onClick = state.onBack
                )
        )
        Spacer(12.dp)
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

// ─── Unanswered Bottom Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnansweredBottomSheet(state: VoteProposalDetailState) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = state.onDismissUnanswered,
        sheetState = sheetState,
        containerColor = ZashiColors.Surfaces.bgPrimary,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                    .padding(bottom = 32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color =
                    ZashiColors.Utility.ErrorRed.utilityError500
                        .copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = ZashiColors.Utility.ErrorRed.utilityError500,
                    modifier = Modifier.padding(12.dp)
                )
            }
            VerticalSpacer(16.dp)
            Text(
                text = stringRes("Unanswered Questions").getValue(),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            VerticalSpacer(8.dp)
            val countText = if (state.unansweredCount == 1) "1 question" else "${state.unansweredCount} questions"
            val pronounText = if (state.unansweredCount == 1) "this question" else "these questions"
            Text(
                text =
                    "You have not responded to $countText. " +
                        "Confirm to abstain from $pronounText or go back to respond.",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            VerticalSpacer(24.dp)
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ButtonState(
                        text = stringRes("Confirm"),
                        style = ButtonStyle.SECONDARY,
                        onClick = state.onConfirmUnanswered
                    )
            )
            VerticalSpacer(12.dp)
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ButtonState(
                        text = stringRes("Go back"),
                        style = ButtonStyle.PRIMARY,
                        onClick = state.onDismissUnanswered
                    )
            )
        }
    }
}

// ─── Poll Ended Bottom Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PollEndedBottomSheet(state: VoteProposalDetailState) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = state.onPollEndedClose,
        sheetState = sheetState,
        containerColor = ZashiColors.Surfaces.bgPrimary,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZashiDimensions.Spacing.spacingMd)
                    .padding(bottom = 32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color =
                    ZashiColors.Utility.ErrorRed.utilityError500
                        .copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = ZashiColors.Utility.ErrorRed.utilityError500,
                    modifier = Modifier.padding(12.dp)
                )
            }
            VerticalSpacer(16.dp)
            Text(
                text = stringRes(co.electriccoin.zcash.ui.R.string.vote_poll_ended_title).getValue(),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            VerticalSpacer(8.dp)
            Text(
                text = stringRes(co.electriccoin.zcash.ui.R.string.vote_poll_ended_message).getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            VerticalSpacer(24.dp)
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ButtonState(
                        text = stringRes(co.electriccoin.zcash.ui.R.string.vote_poll_ended_view_results),
                        style = ButtonStyle.PRIMARY,
                        onClick = state.onPollEndedViewResults
                    )
            )
            VerticalSpacer(12.dp)
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ButtonState(
                        text = stringRes(co.electriccoin.zcash.ui.R.string.vote_close),
                        style = ButtonStyle.TERTIARY,
                        onClick = state.onPollEndedClose
                    )
            )
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

@PreviewScreens
@Composable
private fun ProposalDetailPreviewUnvoted() =
    ZcashTheme {
        VoteProposalDetailView(
            state =
                VoteProposalDetailState(
                    positionLabel = stringRes("1 OF 3"),
                    title = stringRes("Zcash Shielded Assets (ZSAs)"),
                    description =
                        stringRes(
                            "This ZIP extends the Orchard protocol to support user-defined " +
                                "shielded assets on the Zcash network, enabling new financial primitives " +
                                "while maintaining the privacy guarantees of Zcash."
                        ),
                    forumUrl = "https://forum.zcashcommunity.com",
                    options =
                        listOf(
                            VoteVoteOptionRowState(
                                0,
                                stringRes(co.electriccoin.zcash.ui.R.string.vote_option_support),
                                VoteVoteOptionColor.SUPPORT,
                                false,
                                false
                            ) {},
                            VoteVoteOptionRowState(
                                1,
                                stringRes(co.electriccoin.zcash.ui.R.string.vote_option_oppose),
                                VoteVoteOptionColor.OPPOSE,
                                false,
                                false
                            ) {},
                            VoteVoteOptionRowState(
                                2,
                                stringRes(co.electriccoin.zcash.ui.R.string.vote_option_abstain),
                                VoteVoteOptionColor.ABSTAIN,
                                false,
                                false
                            ) {},
                        ),
                    isLocked = false,
                    isEditingFromReview = false,
                    showUnansweredSheet = false,
                    unansweredCount = 0,
                    showPollEndedSheet = false,
                    onBack = {},
                    onNext = {},
                    onConfirmUnanswered = {},
                    onDismissUnanswered = {},
                    onPollEndedClose = {},
                    onPollEndedViewResults = {},
                )
        )
    }

@PreviewScreens
@Composable
private fun ProposalDetailPreviewSupport() =
    ZcashTheme {
        VoteProposalDetailView(
            state =
                VoteProposalDetailState(
                    positionLabel = stringRes("2 OF 3"),
                    title = stringRes("Network Sustainability Mechanism"),
                    description =
                        stringRes(
                            "Redirects a portion of the block subsidy to a sustainability fund."
                        ),
                    forumUrl = null,
                    options =
                        listOf(
                            VoteVoteOptionRowState(
                                0,
                                stringRes(co.electriccoin.zcash.ui.R.string.vote_option_support),
                                VoteVoteOptionColor.SUPPORT,
                                true,
                                false
                            ) {},
                            VoteVoteOptionRowState(
                                1,
                                stringRes(co.electriccoin.zcash.ui.R.string.vote_option_oppose),
                                VoteVoteOptionColor.OPPOSE,
                                false,
                                false
                            ) {},
                            VoteVoteOptionRowState(
                                2,
                                stringRes(co.electriccoin.zcash.ui.R.string.vote_option_abstain),
                                VoteVoteOptionColor.ABSTAIN,
                                false,
                                false
                            ) {},
                        ),
                    isLocked = false,
                    isEditingFromReview = false,
                    showUnansweredSheet = false,
                    unansweredCount = 0,
                    showPollEndedSheet = false,
                    onBack = {},
                    onNext = {},
                    onConfirmUnanswered = {},
                    onDismissUnanswered = {},
                    onPollEndedClose = {},
                    onPollEndedViewResults = {},
                )
        )
    }
