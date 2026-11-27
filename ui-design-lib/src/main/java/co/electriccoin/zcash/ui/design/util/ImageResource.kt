package co.electriccoin.zcash.ui.design.util

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.ShimmerCircle
import co.electriccoin.zcash.ui.design.component.colorpicker.contrastingContentColor
import co.electriccoin.zcash.ui.design.component.rememberZashiShimmer
import co.electriccoin.zcash.ui.design.theme.colors.Gray
import com.valentinilk.shimmer.shimmer

sealed interface ImageResource {
    @JvmInline
    value class ByDrawable(
        @param:DrawableRes val resource: Int
    ) : ImageResource

    @JvmInline
    value class DisplayString(
        val value: String
    ) : ImageResource

    data object Loading : ImageResource

    /**
     * A circle filled with [background] under [foreground], a glyph authored with its padding built
     * in, tinted white or black by [contrastingContentColor] against the circle. [Color.Unspecified]
     * is the theme's default avatar circle.
     */
    data class Layered(
        val background: Color,
        val foreground: ByDrawable,
    ) : ImageResource
}

@Stable
fun imageRes(
    @DrawableRes resource: Int
): ImageResource = ImageResource.ByDrawable(resource)

@Stable
fun imageRes(value: String): ImageResource = ImageResource.DisplayString(value)

@Stable
fun loadingImageRes(): ImageResource = ImageResource.Loading

@Composable
fun ImageResource.Loading.ComposeAsShimmerCircle(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Box(
        modifier = modifier.shimmer(rememberZashiShimmer())
    ) {
        ShimmerCircle(size = size)
    }
}

@Composable
fun ImageResource.ByDrawable.Compose(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null
) {
    Image(
        modifier = modifier,
        painter = painterResource(resource),
        contentDescription = contentDescription,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter
    )
}

/** Draws the circle and its glyph at the size of [modifier]. */
@Composable
private fun ImageResource.Layered.Compose(modifier: Modifier = Modifier) {
    val circle = background.takeOrElse { Gray.`50` orDark Gray.`950` }
    Box(modifier = modifier.background(circle, CircleShape), propagateMinConstraints = true) {
        foreground.Compose(colorFilter = ColorFilter.tint(contrastingContentColor(circle)))
    }
}

/**
 * Draws a drawable or layered image at the size of [modifier]. [ImageResource.DisplayString] and
 * [ImageResource.Loading] draw nothing, since their callers render them their own way.
 */
@Composable
fun ImageResource.Compose(modifier: Modifier = Modifier) {
    when (this) {
        is ImageResource.ByDrawable -> Compose(modifier = modifier)
        is ImageResource.Layered -> Compose(modifier = modifier)
        is ImageResource.DisplayString, ImageResource.Loading -> Unit
    }
}
