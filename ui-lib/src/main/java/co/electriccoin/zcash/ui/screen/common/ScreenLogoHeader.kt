package co.electriccoin.zcash.ui.screen.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors

private val HEADER_ICON_SIZE = 40.dp

private val HEADER_ICON_OVERLAP = 3.dp

/**
 * A screen's logo + badge header: the app's own logo circle overlapped by a circle carrying [icon], re-tinted
 * with [ZashiColors.Text.textPrimary] so it stays legible against [ZashiColors.Surfaces.bgTertiary] in both
 * appearances. The badge glyphs occupy the inner half of their own 40dp vector, so the icon is drawn at the
 * full circle size - the same way the Settings rows draw them - which renders the artwork at the intended ~20dp.
 */
@Composable
fun ScreenLogoHeader(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
    ) {
        Image(
            painter = painterResource(R.drawable.ic_item_zashi),
            contentDescription = null,
            modifier = Modifier.size(HEADER_ICON_SIZE)
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(HEADER_ICON_SIZE)
                    .offset(x = HEADER_ICON_SIZE - HEADER_ICON_OVERLAP)
                    .clip(CircleShape)
                    .background(ZashiColors.Surfaces.bgTertiary)
                    .border(2.dp, ZashiColors.Surfaces.bgPrimary, CircleShape)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = ZashiColors.Text.textPrimary,
                modifier = Modifier.size(HEADER_ICON_SIZE)
            )
        }
    }
}
