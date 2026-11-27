package com.flexa.spend

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

fun Color.isDark(luminance: Float): Boolean {
    return this.luminance() < luminance
}

fun Color.lightenColor(factor: Float): Color {
    val r = (red + (1 - red) * factor).coerceIn(0f, 1f)
    val g = (green + (1 - green) * factor).coerceIn(0f, 1f)
    val b = (blue + (1 - blue) * factor).coerceIn(0f, 1f)
    return Color(r, g, b, alpha)
}

internal fun Color.shiftHue(degrees: Float): Color {
    val hsl = FloatArray(3)
    toHSL(this, hsl)
    hsl[0] = (hsl[0] + degrees) % 360f
    if (hsl[0] < 0)  hsl[0] += 360f
    return Color.HSL(hsl[0], hsl[1], hsl[2])
}

private fun toHSL(color: Color, hsl: FloatArray) {
    val r = (color.red * 255).toInt() / 255f
    val g = (color.green * 255).toInt() / 255f
    val b = (color.blue * 255).toInt() / 255f

    val max = max(max(r, g), b)
    val min = min(min(r, g), b)
    val delta = max - min

    val l = (max + min) / 2f

    var h: Float
    val s: Float
    if (max == min) {
        h = 0f
        s = 0f
    } else {
        s = if (l < 0.5f) delta / (max + min) else delta / (2f - max - min)

        h = when (max) {
            r -> (g - b) / delta + (if (g < b) 6 else 0)
            g -> (b - r) / delta + 2
            else -> (r - g) / delta + 4
        }
        h /= 6
    }
    hsl[0] = h * 360f
    hsl[1] = s
    hsl[2] = l
}

private fun Color.Companion.HSL(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - kotlin.math.abs(2 * lightness - 1)) * saturation
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2 - 1))
    val m = lightness - c / 2f
    val (r, g, b) = when {
        hue < 60 -> Triple(c, x, 0f)
        hue < 120 -> Triple(x, c, 0f)
        hue < 180 -> Triple(0f, c, x)
        hue < 240 -> Triple(0f, x, c)
        hue < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}
