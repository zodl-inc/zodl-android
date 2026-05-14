package co.electriccoin.zcash.ui.screen.voting.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors

sealed interface VoteHeaderIconStyle {
    data object ThumbsUp : VoteHeaderIconStyle

    data object Confirmed : VoteHeaderIconStyle
}

data class VoteWalletHeaderIconsState(
    val isKeystone: Boolean,
    val style: VoteHeaderIconStyle = VoteHeaderIconStyle.ThumbsUp,
)

@Composable
fun VoteWalletHeaderIcons(
    state: VoteWalletHeaderIconsState,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.width(92.dp)
    ) {
        Surface(
            shape = CircleShape,
            color =
                if (state.isKeystone) {
                    ZashiColors.Surfaces.bgAlt
                } else {
                    Color.Black
                },
            modifier = Modifier.size(48.dp)
        ) {
            if (state.isKeystone) {
                Icon(
                    painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_vote_zodl),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        when (state.style) {
            VoteHeaderIconStyle.ThumbsUp -> {
                Surface(
                    shape = CircleShape,
                    color = ZashiColors.Surfaces.bgSecondary,
                    border = BorderStroke(2.dp, ZashiColors.Surfaces.bgPrimary),
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = 44.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_vote_thumbs_up),
                        contentDescription = null,
                        tint = ZashiColors.Text.textPrimary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            VoteHeaderIconStyle.Confirmed -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = 44.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    ZashiColors.Utility.SuccessGreen.utilitySuccess50,
                                    ZashiColors.Utility.SuccessGreen.utilitySuccess100
                                )
                            )
                        )
                        .border(2.dp, ZashiColors.Surfaces.bgPrimary, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_vote_check_verified_solid),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
