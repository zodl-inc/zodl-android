package com.flexa.core.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FlexaLogo(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    colors: List<Color> = listOf(Color.White, Color.Magenta, Color.Blue),
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .rotate(180f)
            .background(
                brush = Brush.sweepGradient(colors = colors),
                shape = shape
            )
            .border(
                width = borderWidth,
                shape = shape,
                brush = if (borderWidth.value > 0)
                    Brush.linearGradient(colors = listOf(borderColor, borderColor))
                else Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))

            )
    )
}

@Preview
@Composable
private fun LogoPreview() {
    FlexaLogo(
        modifier = Modifier.size(32.dp),
        shape = RoundedCornerShape(8.dp),
        borderWidth = 1.dp,
    )
}
