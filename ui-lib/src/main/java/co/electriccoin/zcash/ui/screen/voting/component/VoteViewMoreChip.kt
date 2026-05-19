package co.electriccoin.zcash.ui.screen.voting.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun VoteViewMoreChip(
    onClick: () -> Unit,
    isExpanded: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text =
                if (isExpanded) {
                    stringRes(R.string.vote_proposal_list_view_less).getValue()
                } else {
                    stringRes(R.string.vote_proposal_list_view_more).getValue()
                },
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(4.dp)
        Icon(
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_down_small),
            contentDescription = null,
            tint = ZashiColors.Text.textPrimary,
            modifier =
                Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (isExpanded) ROTATE else 0f }
        )
    }
}

const val ROTATE = 180f
