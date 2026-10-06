package co.electriccoin.zcash.ui.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.spackle.AndroidApiVersion
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

/**
 * The "reveal" hint drawn over hidden secret content on devices without render-effect blur (below
 * Android S), where [blurCompat] can only dim the content. Shown while [isHidden] on those devices only,
 * always at the same fixed width so it looks identical whatever it covers.
 */
@Composable
fun ZashiHiddenContentOverlay(
    isHidden: Boolean,
    text: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        modifier = modifier.width(OVERLAY_WIDTH),
        visible = !AndroidApiVersion.isAtLeastS && isHidden,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_reveal),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Text.textPrimary)
            )

            if (text != null) {
                Spacer(Modifier.height(ZashiDimensions.Spacing.spacingMd))

                Text(
                    text = text,
                    style = ZashiTypography.textLg,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val OVERLAY_WIDTH = 200.dp
