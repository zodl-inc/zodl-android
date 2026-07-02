package co.electriccoin.zcash.ui.screen.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.steppedRotation

sealed interface WalletHeaderBadgeChrome {
    data object Neutral : WalletHeaderBadgeChrome

    data object Success : WalletHeaderBadgeChrome
}

data class WalletHeaderIconsState(
    val isKeystone: Boolean,
    val badgeIcon: Int,
    val badgeChrome: WalletHeaderBadgeChrome = WalletHeaderBadgeChrome.Neutral,
    // Spins the badge icon in discrete 45°/100ms steps — for loading-style badge icons.
    val isBadgeAnimated: Boolean = false,
)

@Composable
fun WalletHeaderIcons(
    state: WalletHeaderIconsState,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = ZashiColors.Surfaces.bgAlt,
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
                Image(
                    painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_item_zashi),
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        val (background, tint, iconPadding) = badgeChromeAppearance(state.badgeChrome)

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(48.dp)
                    .offset(x = 36.dp)
                    .clip(CircleShape)
                    .background(background)
                    .border(2.dp, ZashiColors.Surfaces.bgPrimary, CircleShape)
        ) {
            Icon(
                painter = painterResource(state.badgeIcon),
                contentDescription = null,
                tint = tint,
                modifier =
                    Modifier
                        .padding(iconPadding)
                        .let { if (state.isBadgeAnimated) it.steppedRotation() else it }
            )
        }
    }
}

private data class BadgeAppearance(
    val background: Brush,
    val tint: Color,
    val iconPadding: Dp,
)

@Composable
private fun badgeChromeAppearance(chrome: WalletHeaderBadgeChrome): BadgeAppearance =
    when (chrome) {
        WalletHeaderBadgeChrome.Neutral ->
            BadgeAppearance(
                background = SolidColor(ZashiColors.Surfaces.bgSecondary),
                tint = ZashiColors.Text.textPrimary,
                iconPadding = 14.dp,
            )

        WalletHeaderBadgeChrome.Success ->
            BadgeAppearance(
                background =
                    Brush.verticalGradient(
                        listOf(
                            ZashiColors.Utility.SuccessGreen.utilitySuccess50,
                            ZashiColors.Utility.SuccessGreen.utilitySuccess100
                        )
                    ),
                tint = Color.Unspecified,
                iconPadding = 12.dp,
            )
    }
