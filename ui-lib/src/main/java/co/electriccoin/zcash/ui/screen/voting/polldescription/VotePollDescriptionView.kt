package co.electriccoin.zcash.ui.screen.voting.polldescription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VotePollDescriptionView(
    state: VotePollDescriptionState?,
    sheetState: SheetState = rememberScreenModalBottomSheetState(),
) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetState = sheetState,
    ) { sheetState, contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            val uriHandler = LocalUriHandler.current

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringRes(R.string.vote_poll_description_title).getValue(),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
                Surface(
                    shape = CircleShape,
                    color = ZashiColors.Surfaces.bgSecondary,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .size(44.dp)
                            .clickable { sheetState.onBack() }
                ) {
                    Icon(
                        painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_navigation_close),
                        contentDescription = null,
                        tint = ZashiColors.Text.textSecondary
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f, false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp)
            ) {
                Text(
                    text = sheetState.title.getValue(),
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(8.dp)
                Text(
                    text = sheetState.description.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
                if (sheetState.discussionUrl != null) {
                    Spacer(16.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri(sheetState.discussionUrl) }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ZashiColors.Surfaces.bgTertiary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_vote_message_chat),
                                contentDescription = null,
                                tint = ZashiColors.Text.textPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(16.dp)
                        Text(
                            text = stringRes(R.string.vote_proposal_detail_forum_discussions).getValue(),
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun PollDescriptionPreview() =
    ZcashTheme {
        VotePollDescriptionView(
            state =
                VotePollDescriptionState(
                    title = stringRes("NU7 Sentiment Poll"),
                    description =
                        stringRes(
                            "This poll gauges coinholder and community sentiment on proposed Zcash protocol " +
                                "features and initiatives. It includes questions focused on protocol proposals."
                        ),
                    discussionUrl = "https://forum.zcashcommunity.com",
                    onBack = {}
                )
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun PollDescriptionNoUrlPreview() =
    ZcashTheme {
        VotePollDescriptionView(
            state =
                VotePollDescriptionState(
                    title = stringRes("NU7 Sentiment Poll"),
                    description = stringRes("This poll gauges coinholder sentiment on proposed Zcash protocol features."),
                    discussionUrl = null,
                    onBack = {}
                )
        )
    }
