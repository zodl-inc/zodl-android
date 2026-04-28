package co.electriccoin.zcash.ui.screen.voting.polldescription

import androidx.compose.foundation.clickable
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
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
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
    ) { state, contentPadding ->
        val uriHandler = LocalUriHandler.current

        Column(
            modifier =
                Modifier
                    .weight(1f, false)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ZashiDimensions.Spacing.spacingXl,
                        end = ZashiDimensions.Spacing.spacingXl,
                        bottom = contentPadding.calculateBottomPadding() + ZashiDimensions.Spacing.spacing4xl,
                    )
        ) {
            Spacer(12.dp)
            Text(
                text = state.title.getValue(),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(8.dp)
            Text(
                text = state.description.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.discussionUrl != null) {
                Spacer(16.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(state.discussionUrl) }
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
                        color = ZashiColors.Text.textPrimary,
                        fontWeight = FontWeight.SemiBold,
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
