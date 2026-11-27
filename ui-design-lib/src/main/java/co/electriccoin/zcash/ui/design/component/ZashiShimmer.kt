package co.electriccoin.zcash.ui.design.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import com.valentinilk.shimmer.LocalShimmerTheme
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer

@Composable
fun rememberZashiShimmer() =
    rememberShimmer(
        ShimmerBounds.View,
        LocalShimmerTheme.current.copy(
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 750,
                            easing = LinearEasing,
                            delayMillis = 450,
                        ),
                    repeatMode = RepeatMode.Restart,
                )
        )
    )

@Composable
fun ShimmerCircle(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = ZashiColors.Surfaces.bgSecondary
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(color, CircleShape)
    )
}

@Composable
fun ShimmerRectangle(
    width: Dp = 40.dp,
    height: Dp = 20.dp,
    color: Color = ZashiColors.Surfaces.bgSecondary,
    shape: Shape = RoundedCornerShape(ZashiDimensions.Radius.radiusSm)
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .height(height)
                .background(color, shape)
    )
}

@Composable
fun ShimmerRectangle(
    modifier: Modifier = Modifier,
    color: Color = ZashiColors.Surfaces.bgSecondary,
    shape: Shape = RoundedCornerShape(ZashiDimensions.Radius.radiusSm)
) {
    Box(
        modifier =
            modifier
                .background(color, shape)
    )
}

/**
 * Self-wrapping is caller-controlled: used for grouped multi-element shimmer sweeps, where one Modifier.shimmer(...)
 * is applied over several ShimmerableText/ShimmerableImage children (e.g. AccountSwitch in
 * ZashiTopAppBarWithAccountSelection.kt, ZashiSwapQuoteAmount's Layout).
 */
@Composable
fun ShimmerableText(
    text: String?,
    shimmerText: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
) {
    if (text == null) {
        ShimmerTextPlaceholder(
            sampleText = shimmerText,
            style = style.copy(fontWeight = fontWeight ?: style.fontWeight),
            modifier = modifier,
        )
    } else {
        ZashiAutoSizeText(
            modifier = modifier,
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = maxLines,
            textAlign = textAlign
        )
    }
}

/**
 * Placeholder bar sized from [sampleText] measured in [style]. The layout box keeps the full
 * line-box size (honoring [TextStyle.lineHeight]) so content does not shift when the real text
 * loads; the painted bar uses the font's natural height (measured with lineHeight removed,
 * clamped to the line box) centered vertically, approximating the visible glyph extent.
 */
@Composable
fun ShimmerTextPlaceholder(
    sampleText: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = ZashiColors.Surfaces.bgTertiary,
) {
    val lineBox = measureTextStyle(text = sampleText, style = style).size
    val naturalBox =
        measureTextStyle(
            text = sampleText,
            style = style.copy(lineHeight = TextUnit.Unspecified)
        ).size
    val barHeight = minOf(naturalBox.height, lineBox.height)
    Box(
        modifier = modifier.width(lineBox.widthDp).height(lineBox.heightDp),
        contentAlignment = Alignment.Center,
    ) {
        ShimmerRectangle(
            modifier =
                Modifier
                    .width(lineBox.widthDp)
                    .height(with(LocalDensity.current) { barHeight.toDp() }),
            color = color,
        )
    }
}

@Composable
fun ShimmerableImage(
    painter: Painter?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    colorFilter: ColorFilter? = null,
    shimmerShape: Shape = CircleShape,
) {
    if (painter == null) {
        ShimmerRectangle(
            modifier = modifier,
            color = ZashiColors.Surfaces.bgSecondary,
            shape = shimmerShape,
        )
    } else {
        Image(
            modifier = modifier,
            painter = painter,
            contentDescription = contentDescription,
            colorFilter = colorFilter,
        )
    }
}
