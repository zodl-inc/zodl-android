package co.electriccoin.zcash.ui.design.component.colorpicker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** The WCAG 2 contrast ratio of two opaque colours, from 1 to 21. */
internal fun contrastRatio(
    first: Color,
    second: Color
): Float {
    val lighter = max(first.luminance(), second.luminance())
    val darker = min(first.luminance(), second.luminance())
    return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
}

/**
 * White when it reaches WCAG's 3:1 minimum for graphics against the opaque [background], otherwise
 * black.
 */
internal fun contrastingContentColor(background: Color): Color =
    if (contrastRatio(Color.White, background) >= MIN_GRAPHICS_CONTRAST) Color.White else Color.Black

private const val LUMINANCE_OFFSET = 0.05f
private const val MIN_GRAPHICS_CONTRAST = 3f
