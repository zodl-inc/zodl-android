package co.electriccoin.zcash.ui.screen.voting

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

fun Modifier.escapeHorizontalPadding(horizontal: Dp): Modifier =
    layout { measurable, constraints ->
        val extraPx = (horizontal * 2).roundToPx()
        val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extraPx))
        layout(constraints.maxWidth, placeable.height) {
            placeable.place(-horizontal.roundToPx(), 0)
        }
    }
