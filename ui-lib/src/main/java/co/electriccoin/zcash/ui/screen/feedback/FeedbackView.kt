package co.electriccoin.zcash.ui.screen.feedback

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.OverlappingIconsState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiOverlappingIcons
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun FeedbackView(state: FeedbackState) {
    Scaffold(
        topBar = {
            SupportTopAppBar(
                state = state,
            )
        },
    ) { paddingValues ->
        SupportMainContent(
            state = state,
            modifier = Modifier.scaffoldPadding(paddingValues)
        )
    }
}

@Composable
private fun SupportTopAppBar(
    state: FeedbackState
) {
    ZashiSmallTopAppBar(
        title = stringResource(id = R.string.support_header),
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = state.onBack)
        },
    )
}

@Composable
private fun SupportMainContent(
    state: FeedbackState,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .then(modifier),
    ) {
        ZashiOverlappingIcons(
            state =
                OverlappingIconsState(
                    icons =
                        listOf(
                            imageRes(R.drawable.ic_feedback),
                            imageRes(R.drawable.ic_feedback_zec)
                        )
                )
        )

        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacing3xl))

        Text(
            text = stringResource(id = R.string.support_title),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingMd))

        Text(
            text = stringResource(id = R.string.sendFeedback_desc),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textSm
        )

        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacing4xl))

        Text(
            text = stringResource(id = R.string.sendFeedback_ratingQuestion),
            color = ZashiColors.Inputs.Default.label,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingLg))

        EmojiRow(state.emojiState)

        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacing3xl))

        Text(
            text = stringResource(id = R.string.sendFeedback_howCanWeHelp),
            color = ZashiColors.Inputs.Default.label,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingLg))

        ZashiTextField(
            state = state.feedback,
            minLines = 3,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = stringResource(id = R.string.sendFeedback_hcwhPlaceholder),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Inputs.Default.text
                )
            },
        )

        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingLg))

        Spacer(
            modifier = Modifier.weight(1f)
        )

        // TODO [#1467]: Support screen - keep button above keyboard
        // TODO [#1467]: https://github.com/Electric-Coin-Company/zashi-android/issues/1467
        ZashiButton(
            state = state.sendButton,
            modifier = Modifier.fillMaxWidth()
        )
    }

    LaunchedEffect(Unit) {
        // Causes the TextField to focus on the first screen visit
        focusRequester.requestFocus()
    }
}

@Composable
private fun EmojiRow(state: FeedbackEmojiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = spacedBy(2.dp),
    ) {
        listOf(
            FeedbackEmoji.FIRST,
            FeedbackEmoji.SECOND,
            FeedbackEmoji.THIRD,
            FeedbackEmoji.FOURTH,
            FeedbackEmoji.FIFTH,
        ).forEach {
            Emoji(
                modifier = Modifier.weight(1f),
                emoji = it,
                isSelected = state.selection == it,
                onClick = { state.onSelected(it) }
            )
        }
    }
}

@Composable
private fun Emoji(
    emoji: FeedbackEmoji,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
            modifier
                .aspectRatio(EMOJI_CARD_RATIO)
                .border(
                    width = 2.5.dp,
                    color = if (isSelected) ZashiColors.Text.textPrimary else Color.Transparent,
                    shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl)
                ).padding(4.5.dp)
                .background(
                    color = ZashiColors.Surfaces.bgSecondary,
                    shape = RoundedCornerShape(8.dp)
                ).clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            modifier = Modifier.size(24.dp),
            painter = painterResource(emoji.res),
            contentDescription = null
        )
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            FeedbackView(
                state =
                    FeedbackState(
                        onBack = {},
                        sendButton = ButtonState(stringRes("Button")),
                        feedback = TextFieldState(stringRes("")) {},
                        emojiState =
                            FeedbackEmojiState(
                                selection = FeedbackEmoji.FIRST,
                                onSelected = {}
                            )
                    ),
            )
        }
    }

private const val EMOJI_CARD_RATIO = 1.25f
