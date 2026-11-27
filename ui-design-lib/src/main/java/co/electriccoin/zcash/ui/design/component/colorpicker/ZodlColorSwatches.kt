package co.electriccoin.zcash.ui.design.component.colorpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.TAP_TARGET_SIZE
import co.electriccoin.zcash.ui.design.component.ZodlCircleTapTarget
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

/**
 * The bottom row of the picker: a 75 dp preview of [current], then the [swatches] as 30 dp dots
 * five to a row at a 45 dp row pitch, the picked one ringed in white.
 */
@Composable
fun ZodlColorSwatches(
    current: Color,
    swatches: List<ColorSwatchState>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PREVIEW_GAP),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = (TAP_TARGET_SIZE - DOT_SIZE) / 2)
                    .size(PREVIEW_SIZE)
                    .background(current, RoundedCornerShape(PREVIEW_RADIUS))
        )
        Column(
            modifier = Modifier.weight(1f).widenBy(SLOT_OVERHANG),
            verticalArrangement = Arrangement.spacedBy(-ROW_OVERLAP)
        ) {
            swatches.chunked(PER_ROW).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { swatch -> SwatchDot(swatch) }
                }
            }
        }
    }
}

@Composable
private fun SwatchDot(state: ColorSwatchState) {
    val haptic = LocalHapticFeedback.current
    ZodlCircleTapTarget(
        onClick = {
            if (!state.isSelected) {
                runCatching { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
            }
            state.onClick(state.color)
        },
        modifier =
            Modifier.semantics {
                contentDescription = "#" + state.color.toHex()
                selected = state.isSelected
            }
    ) {
        Box(
            modifier =
                Modifier
                    .size(DOT_SIZE)
                    .background(state.color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (state.isSelected) {
                Box(
                    modifier =
                        Modifier
                            .size(RING_SIZE)
                            .border(RING_STROKE, Color.White, CircleShape)
                )
            }
        }
    }
}

/**
 * Lets the 48 dp slots overhang the column by [overhang] on each side, so the 30 dp dots line up
 * with its edges as they do in Figma.
 */
private fun Modifier.widenBy(overhang: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = overhang.roundToPx() * 2
        val placeable =
            measurable.measure(
                constraints.copy(
                    minWidth = constraints.minWidth + extra,
                    maxWidth = constraints.maxWidth + extra
                )
            )
        layout(constraints.maxWidth, placeable.height) {
            placeable.place(-extra / 2, 0)
        }
    }

/** One swatch dot: its colour, whether it is the picked colour, and what picking it does with that colour. */
data class ColorSwatchState(
    val color: Color,
    val isSelected: Boolean,
    val onClick: (Color) -> Unit,
)

private const val PER_ROW = 5
private val PREVIEW_SIZE = 75.dp
private val PREVIEW_RADIUS = 10.dp
private val PREVIEW_GAP = 30.dp
private val DOT_SIZE = 30.dp
private val SLOT_OVERHANG = (TAP_TARGET_SIZE - DOT_SIZE) / 2

/** Overlaps the 48 dp slot rows so the dots sit at a 45 dp pitch, ending level with the preview. */
private val ROW_OVERLAP = 3.dp
private val RING_SIZE = 22.dp
private val RING_STROKE = 2.dp

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ZodlColorSwatches(
                current = Color(0xFFAF52DE),
                swatches =
                    ColorPickerPalette.presets.mapIndexed { index, color ->
                        ColorSwatchState(color = color, isSelected = index == 6, onClick = {})
                    }
            )
        }
    }
