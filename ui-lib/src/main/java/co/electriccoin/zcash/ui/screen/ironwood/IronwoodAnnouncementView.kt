package co.electriccoin.zcash.ui.screen.ironwood

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun IronwoodAnnouncementView(state: IronwoodAnnouncementState) {
    BlankBgScaffold(
        // Empty top app bar: no title, no back button — reserves the standard bar height like Figma.
        topBar = { ZashiSmallTopAppBar(title = null) },
        bottomBar = {},
        content = { padding ->
            Content(
                state = state,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding),
            )
        }
    )
}

@Composable
private fun Content(
    state: IronwoodAnnouncementState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.ic_ironwood_zodl_logos),
            contentDescription = null,
        )

        Spacer(16.dp)

        Text(
            text = stringResource(R.string.ironwood_announcement_title),
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
        )

        Spacer(12.dp)

        Text(
            text = stringResource(R.string.ironwood_announcement_body_1),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.ironwood_announcement_body_2),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Spacer(12.dp)
        Text(
            text = stringResource(R.string.ironwood_announcement_body_3),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )

        Spacer(24.dp)

        GuideParagraph(onGuideClick = state.onGuideClick)

        Spacer(24.dp)
        Spacer(1f)

        ZashiButton(
            state = state.primaryButton,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GuideParagraph(onGuideClick: () -> Unit) {
    val full = stringResource(R.string.ironwood_announcement_body_guide)
    val linkText = stringResource(R.string.ironwood_announcement_body_guide_link)
    val start = full.indexOf(linkText)

    val annotated =
        buildAnnotatedString {
            append(full)
            if (start >= 0 && linkText.isNotEmpty()) {
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "guide",
                        styles =
                            TextLinkStyles(
                                style =
                                    SpanStyle(
                                        color = ZashiColors.Text.textLink,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline,
                                    )
                            ),
                    ) { onGuideClick() },
                    start = start,
                    end = start + linkText.length,
                )
            }
        }

    Text(
        text = annotated,
        style = ZashiTypography.textSm,
        fontWeight = FontWeight.Medium,
        color = ZashiColors.Text.textPrimary,
    )
}

@PreviewScreens
@Composable
private fun IronwoodAnnouncementPreview() =
    ZcashTheme {
        IronwoodAnnouncementView(
            state =
                IronwoodAnnouncementState(
                    onGuideClick = {},
                    primaryButton =
                        ButtonState(
                            text = stringRes(R.string.ironwood_announcement_primary_button),
                        ),
                ),
        )
    }
