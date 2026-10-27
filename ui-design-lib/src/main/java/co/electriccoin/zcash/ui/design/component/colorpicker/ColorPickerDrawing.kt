package co.electriccoin.zcash.ui.design.component.colorpicker

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Reports every position of a press and of the drag that follows it, together with the size of
 * the element. The press is consumed at once, so neither the sheet nor a scrolling parent takes
 * the gesture over. [onPick] is read once, so it must read the latest callback through a state
 * (`rememberUpdatedState`).
 */
internal fun Modifier.pickGestures(onPick: (Offset, IntSize) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            onPick(down.position, size)
            drag(down.id) { change ->
                change.consume()
                onPick(change.position, size)
            }
        }
    }

/**
 * A soft shadow, about 3 dp wide and 10 % black, on both sides of a ring of [ringWidth] whose outer
 * edge is [outerRadius] from [center]: the `0 0 3 rgba(0,0,0,0.1)` drop shadow of the Figma knob.
 */
internal fun DrawScope.drawRingShadow(
    center: Offset,
    outerRadius: Float,
    ringWidth: Float,
) {
    val blur = RING_SHADOW_BLUR.toPx()
    val extent = outerRadius + blur
    val innerEdge = (outerRadius - ringWidth).coerceAtLeast(0f)
    val shadow = Color.Black.copy(alpha = RING_SHADOW_ALPHA)
    drawCircle(
        brush =
            Brush.radialGradient(
                (innerEdge - blur).coerceAtLeast(0f) / extent to Color.Transparent,
                innerEdge / extent to shadow,
                outerRadius / extent to shadow,
                1f to Color.Transparent,
                center = center,
                radius = extent
            ),
        radius = extent,
        center = center
    )
}

private val RING_SHADOW_BLUR = 3.dp
private const val RING_SHADOW_ALPHA = 0.1f
