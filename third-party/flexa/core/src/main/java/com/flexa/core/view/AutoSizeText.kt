package com.flexa.core.view

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private const val TEXT_SCALE_REDUCTION_INTERVAL = 0.9f

@Composable
fun AutoSizeText(
    modifier: Modifier = Modifier,
    text: String? = null,
    annotatedText: AnnotatedString? = null,
    fontSize: TextUnit = 72.sp,
    lineHeight: TextUnit = 80.sp,
    maxLines: Int = 1,
    textStyle: TextStyle = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        lineHeight = lineHeight,
        textAlign = TextAlign.Center
    )
) {
    BoxWithConstraints(modifier = modifier) {
        this.maxWidth
        var shrunkFontSize = textStyle.fontSize
        val calculateIntrinsics = @Composable {
            ParagraphIntrinsics(
                text = when {
                    text != null -> text
                    annotatedText != null -> annotatedText.text
                    else -> ""
                },
                style = textStyle.copy(fontSize = shrunkFontSize),
                density = LocalDensity.current,
                fontFamilyResolver = createFontFamilyResolver(LocalContext.current)
            )
        }

        var intrinsics = calculateIntrinsics()
        with(LocalDensity.current) {
            // TextField and OutlinedText field have default horizontal padding of 16.dp
            val textFieldDefaultHorizontalPadding = 1
            val maxInputWidth = maxWidth.toPx() - 2 * textFieldDefaultHorizontalPadding

            while (intrinsics.maxIntrinsicWidth > maxInputWidth) {
                shrunkFontSize *= TEXT_SCALE_REDUCTION_INTERVAL
                intrinsics = calculateIntrinsics()
            }
        }

        when {
            text != null ->
                Text(
                    modifier = Modifier,
                    text = text,
                    maxLines = maxLines,
                    style = textStyle.copy(fontSize = shrunkFontSize),
                )
            annotatedText != null ->
                Text(
                    modifier = Modifier,
                    text = annotatedText,
                    maxLines = maxLines,
                    style = textStyle.copy(fontSize = shrunkFontSize),
                )
        }
    }
}
