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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
 * The Grid tab: [ColorPickerPalette.grid] as 12 x 10 cells. Tapping or dragging over a cell picks
 * its colour; the cell matching [selected] carries a white outline, black on the lightest cells.
 * Accessibility services read it as one image whose state names [selected] in hex.
 */
@Composable
fun ZodlColorGrid(
    selected: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val selectedIndex = gridIndexOf(selected)
    val description = stringResource(R.string.colorPicker_gridDescription)
    val selectedHex = stringResource(R.string.colorPicker_selectedHex, selected.toHex())
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(COLOR_PICKER_AREA_HEIGHT)
                .clip(RoundedCornerShape(COLOR_PICKER_AREA_RADIUS))
                .semantics {
                    contentDescription = description
                    role = Role.Image
                    stateDescription = selectedHex
                }.pickGestures { position, size ->
                    val column =
                        (position.x / size.width * ColorPickerPalette.GRID_COLUMNS)
                            .toInt()
                            .coerceIn(0, ColorPickerPalette.GRID_COLUMNS - 1)
                    val row =
                        (position.y / size.height * ColorPickerPalette.GRID_ROWS)
                            .toInt()
                            .coerceIn(0, ColorPickerPalette.GRID_ROWS - 1)
                    currentOnSelect(ColorPickerPalette.grid[row * ColorPickerPalette.GRID_COLUMNS + column])
                }
    ) {
        val cell = Size(size.width / ColorPickerPalette.GRID_COLUMNS, size.height / ColorPickerPalette.GRID_ROWS)
        ColorPickerPalette.grid.forEachIndexed { index, color ->
            drawRect(color = color, topLeft = cellOrigin(index, cell), size = cell)
        }
        if (selectedIndex >= 0) {
            val outline = SELECTION_OUTLINE.toPx()
            val origin = cellOrigin(selectedIndex, cell)
            drawRect(
                color = outlineColor(ColorPickerPalette.grid[selectedIndex]),
                topLeft = Offset(origin.x + outline / 2, origin.y + outline / 2),
                size = Size(cell.width - outline, cell.height - outline),
                style = Stroke(width = outline)
            )
        }
    }
}

private fun outlineColor(cell: Color) = if (cell.luminance() > LIGHT_CELL) Color.Black else Color.White

private fun gridIndexOf(color: Color): Int {
    val rgb = color.toArgb() and RGB_MASK
    return ColorPickerPalette.grid.indexOfFirst { (it.toArgb() and RGB_MASK) == rgb }
}

private fun cellOrigin(
    index: Int,
    cell: Size
) = Offset(
    x = index % ColorPickerPalette.GRID_COLUMNS * cell.width,
    y = index / ColorPickerPalette.GRID_COLUMNS * cell.height
)

/** The height of the Grid and Spectrum areas, and the corner radius both share. */
internal val COLOR_PICKER_AREA_HEIGHT = 296.dp
internal val COLOR_PICKER_AREA_RADIUS = 10.dp

private val SELECTION_OUTLINE = 3.dp
private const val LIGHT_CELL = 0.5f
private const val RGB_MASK = 0xFFFFFF

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ZodlColorGrid(selected = Color(0xFFBE38F3), onSelect = {})
        }
    }
