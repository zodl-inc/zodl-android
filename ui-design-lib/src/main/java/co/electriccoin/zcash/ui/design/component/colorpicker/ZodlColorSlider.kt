package co.electriccoin.zcash.ui.design.component.colorpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

/**
 * A 34 dp capsule track filled with [trackColors] from left to right, with a 34 dp white ring and a
 * soft shadow as the knob at [value] (0 to 1), filled with [knobColor]. Tapping or dragging anywhere
 * in its 48 dp tall touch area moves the knob.
 */
@Composable
fun ZodlColorSlider(
    value: Float,
    trackColors: List<Color>,
    knobColor: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val knob = with(LocalDensity.current) { KNOB_SIZE.toPx() }
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TOUCH_HEIGHT)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                    setProgress { target ->
                        currentOnValueChange(target.coerceIn(0f, 1f))
                        true
                    }
                }.pickGestures { touch, size ->
                    val travel = (size.width - knob).coerceAtLeast(1f)
                    currentOnValueChange(((touch.x - knob / 2) / travel).coerceIn(0f, 1f))
                }
    ) {
        val track = KNOB_SIZE.toPx()
        val top = (size.height - track) / 2
        val trackShape =
            Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = top,
                        right = size.width,
                        bottom = top + track,
                        cornerRadius = CornerRadius(track / 2)
                    )
                )
            }
        clipPath(trackShape) {
            drawRect(
                brush = Brush.horizontalGradient(trackColors),
                topLeft = Offset(0f, top),
                size = Size(size.width, track)
            )
        }
        val radius = track / 2
        val center = Offset(radius + value.coerceIn(0f, 1f) * (size.width - track), size.height / 2)
        val ring = KNOB_RING.toPx()
        drawRingShadow(center = center, outerRadius = radius, ringWidth = ring)
        drawCircle(color = knobColor, radius = radius, center = center)
        drawCircle(color = Color.White, radius = radius - ring / 2, center = center, style = Stroke(width = ring))
    }
}

private val TOUCH_HEIGHT = 48.dp
private val KNOB_SIZE = 34.dp
private val KNOB_RING = 3.dp

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ZodlColorSlider(
                value = 1f,
                trackColors = listOf(Color.Black, Color(0xFFAF52DE)),
                knobColor = Color(0xFFAF52DE),
                onValueChange = {}
            )
        }
    }
