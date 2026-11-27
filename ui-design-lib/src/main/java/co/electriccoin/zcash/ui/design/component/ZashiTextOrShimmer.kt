package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer

/**
 * Self-contained, single-element: owns its own .shimmer(), unlike ShimmerableText which relies on the caller
 * wrapping a group of elements in one shared Modifier.shimmer(...) sweep.
 */
@Composable
fun ZashiTextOrShimmer(
    text: String?,
    modifier: Modifier = Modifier,
    shimmerWidth: Dp = 40.dp,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    if (text != null) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style,
        )
    } else {
        val style =
            style.merge(
                fontWeight = fontWeight,
                fontSize = fontSize,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textAlign = textAlign ?: TextAlign.Companion.Unspecified,
                lineHeight = lineHeight,
            )
        Box(
            modifier = modifier.shimmer(rememberZashiShimmer())
        ) {
            ShimmerRectangle(
                width = shimmerWidth,
                height = measureTextStyle(style).size.heightDp
            )
        }
    }
}

@Composable
fun ZashiTextOrShimmer(
    text: AnnotatedString?,
    modifier: Modifier = Modifier,
    shimmerWidth: Dp = 40.dp,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit) = {},
    style: TextStyle = LocalTextStyle.current
) {
    if (text != null) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style,
        )
    } else {
        val style =
            style.merge(
                fontWeight = fontWeight,
                fontSize = fontSize,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textAlign = textAlign ?: TextAlign.Companion.Unspecified,
                lineHeight = lineHeight,
            )
        Box(
            modifier = modifier.shimmer(rememberZashiShimmer())
        ) {
            ShimmerRectangle(
                width = shimmerWidth,
                height = measureTextStyle(style).size.heightDp
            )
        }
    }
}
