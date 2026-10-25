package co.electriccoin.zcash.ui.design.component.colorpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

/**
 * The Spectrum tab: every hue from left to right, white at the top through the full hue in the
 * middle to black at the bottom, the same mapping as [spectrumColor]. Tapping or dragging reports
 * the touch point as fractions of the area; a ring filled with [color] marks [position].
 * Accessibility services read it as one image whose state names [color] in hex.
 */
@Composable
fun ZodlColorSpectrum(
    position: Offset,
    color: Color,
    onPick: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPick by rememberUpdatedState(onPick)
    val description = stringResource(R.string.colorPicker_spectrumDescription)
    val pickedHex = stringResource(R.string.colorPicker_selectedHex, color.toHex())
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(COLOR_PICKER_AREA_HEIGHT)
                .clip(RoundedCornerShape(COLOR_PICKER_AREA_RADIUS))
                .semantics {
                    contentDescription = description
                    role = Role.Image
                    stateDescription = pickedHex
                }.pickGestures { touch, size ->
                    currentOnPick(
                        Offset(
                            x = (touch.x / size.width).coerceIn(0f, 1f),
                            y = (touch.y / size.height).coerceIn(0f, 1f)
                        )
                    )
                }
    ) {
        drawRect(Brush.horizontalGradient(HUES))
        drawRect(Brush.verticalGradient(0f to Color.White, HALF to Color.White.copy(alpha = 0f)))
        drawRect(Brush.verticalGradient(HALF to Color.Black.copy(alpha = 0f), 1f to Color.Black))
        val center = Offset(position.x * size.width, position.y * size.height)
        val radius = RING_DIAMETER.toPx() / 2
        val stroke = RING_STROKE.toPx()
        drawRingShadow(center = center, outerRadius = radius + stroke / 2, ringWidth = stroke)
        drawCircle(color = color.copy(alpha = 1f), radius = radius, center = center)
        drawCircle(color = Color.White, radius = radius, center = center, style = Stroke(width = stroke))
    }
}

private val HUES =
    listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color.Red
    )

private const val HALF = 0.5f
private val RING_DIAMETER = 28.dp
private val RING_STROKE = 3.dp

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ZodlColorSpectrum(
                position = Offset(0.8f, 0.4f),
                color = spectrumColor(Offset(0.8f, 0.4f)),
                onPick = {}
            )
        }
    }
